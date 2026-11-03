# Aprenentatge local del consum — fase 1

Branca: `feature/consumption-learning`. Revisió de l'arquitectura feta abans dels canvis.

## Dades reals i compatibilitat

`Sample` és un record amb `time` (epoch ms), `soc` (%), `voltage` (V), `current` (A), `speed` (km/h), `latitude`, `longitude`, `altitude` (opcionales) i `raw`. `power()` deriva kW de V × A / 1000. No existeixen comptadors d'energia consumida/regenerada persistits: `TripStats` integra potència i velocitat amb trapezis i exclou buits superiors a 10 s.

`Store` desa `trips`, `samples` i `readings`. `RecordingService` admet cicles OBD complets de fins a 8 s; incorpora GPS recent (<10 s, precisió horitzontal ≤30 m) i altitud només amb precisió vertical ≤10 m. Aquests criteris es comprovaven ja durant el registre, però la precisió i l'edat concretes del fix no es desen. Tampoc hi ha indicador explícit d'endoll/càrrega. Els viatges poden incloure temps endollat perquè comencen quan hi ha lectures OBD vàlides.

El nou analitzador només llegeix mostres, independentment de Bluetooth, ELM327, `TripStats` i la generació de PDF. No s'han modificat `Sample`, `TripStats` ni `RecordingService`. Les dades originals, inclosos NULL i respostes brutes, es conserven.

## Model i filtres

És un aprenentatge empíric per agregació d'observacions, sense valors de catàleg ni entrenament al núvol. Selecciona exclusivament viatges reals (`demo=0`) tancats (`complete`); descarta oberts i interromputs. Cada viatge compta una sola vegada per cel·la del perfil, encara que hi contribueixin diversos trams.

Exigeix SOC finit 0..100 %, tensió 100..1000 V, intensitat −1000..1000 A, velocitat 5..250 km/h i coordenades finites dins dels rangs geogràfics. Són límits de validació conservadors, no consums ni característiques oficials del vehicle. SQL NULL en camps obligatoris es llegeix com NaN, mai com zero.

Trenca la continuïtat amb mostres incompletes, temps no creixents, buits >10 s, acceleració absoluta >8 m/s², salt SOC >2 punts + 0,05 punts/s, o distància GPS entre fixes >1,5 × distància OBD + 60 m. El marge admet soroll i fixes repetits entre actualitzacions GPS.

Cada tram continu necessita ≥60 s, ≥1 km i distància GPS acumulada entre 0,5 i 1,5 × distància OBD (+60 m al límit superior). Això també rebutja GPS congelat durant tot un tram. No es reconstrueixen buits, distàncies o energia sense observacions. Les aturades i velocitats <5 km/h trenquen el tram: no s'atribueix càrrega estacionària a regeneració durant conducció. S'admet energia neta negativa en baixades reals.

Integra distància OBD i energia a partir de la potència dels extrems. Divideix els intervals exactament als límits de velocitat i al creuament de potència zero, conservant distància, consum brut i regeneració. Les cel·les tenen amplada configurable (enter 1..250 km/h), 10 km/h per defecte; són semiobertes: 60 pertany a [60,70). `validIntervals` compta parelles originals vàlides que contribueixen a una cel·la, una sola vegada encara que el signe de potència canviï. No és el nombre de punts interpolats ni de viatges.

Consum observat = `100 × (consumedKwh − recoveredKwh) / km`: mitjana ponderada per distància, no mitjana simple dels consums de cada interval.

## Desnivell i confiança

Les cel·les també distingeixen `UNKNOWN`, `LEVEL`, `UPHILL` i `DOWNHILL`. El terreny representa el pendent **net mitjà de tot el tram continu**, no una correcció física ni un model de pendent instantani. Exigeix altitud en totes les mostres, dins −500..9000 m i sense salts >20 m entre mostres. Considera una incertesa conservadora de 20 m entre extrems i un llindar de pendent del 2 %. Només etiqueta pujada/baixada quan se supera el llindar més la incertesa; pla quan tota la incertesa queda dins el llindar. La resta és desconegut; no hi ha substitució automàtica entre terrenys.

| Confiança | Requisits acumulats per cel·la |
| --- | --- |
| INSUFFICIENT | Menys de 5 km, 30 intervals o 3 viatges |
| LOW | Tots els mínims anteriors |
| MEDIUM | També ≥20 km i ≥5 viatges |
| HIGH | També ≥50 km, ≥200 intervals i ≥10 viatges |

És un indicador heurístic de suport de dades, no un interval de confiança probabilístic ni una garantia d'error. Les mostres successives estan correlacionades. `observedKwhPer100Km` exposa la mitjana descriptiva disponible (null si no hi ha observacions); `kwhPer100Km` només ofereix una estimació utilitzable si es compleixen els mínims. En cas contrari és null i `status()` retorna **dades insuficients**. Sempre s'inclouen intervals, distància i viatges. No s'extrapola a velocitats o terrenys no observats.

## SQLite i consulta futura

Esquema v2: migració additiva des de v1. `consumption_profiles` desa cel·les, distància, energies separades, intervals i viatges; `consumption_model` desa versió d'algorisme, amplada i moment del recàlcul. No elimina ni transforma les taules existents.

```java
// Executar en un worker; Store ha de continuar obert durant la consulta.
ConsumptionModel model = new ConsumptionRepository(store); // intervals de 10 km/h
ConsumptionModel.Estimate e = model.estimate(65, ConsumptionModel.Terrain.UNKNOWN);
if (e.kwhPer100Km() != null) {
    // Consum observat amb prou suport per a aquesta velocitat i terreny.
}
new ConsumptionRepository(store, 20).recalculate(); // reconstrueix amb intervals de 20 km/h
```

`recalculate()` i `estimate()` reconstrueixen tot l'històric elegible i substitueixen el perfil dins una transacció. La consulta i el recàlcul comparteixen la mateixa transacció: no es barregen configuracions. Les consultes incorporen automàticament nous viatges tancats, sense dobles recomptes ni necessitat d'alterar el servei de registre. El resultat persisteix entre obertures. Només es conserva la configuració reconstruïda més recent. No hi ha botó ni pantalla nova en aquesta fase; la interfície Java és el punt d'entrada.

## Límits i dades pendents

- Recalcular és O(mostres històriques) i carrega un viatge a memòria. Cal usar un worker; un històric gran justificarà streaming, cache i invalidació incremental. La transacció d'escriptura també pot retardar breument el registre concurrent.
- El filtre GPS és deliberadament conservador i pot rebutjar dades OBD útils quan no hi ha GPS. No permet certificar la precisió retrospectivament. Una degradació GPS que passi els marges encara pot introduir error.
- Falta indicador explícit d'endoll, estat de càrrega i unitat temporal/precisió per cada senyal. El filtre de moviment rebutja càrrega estacionària, però no certifica l'origen de cada energia entrant.
- La tensió i la intensitat es llegeixen seqüencialment; la integració assumeix variació lineal entre mostres. No hi ha comptador d'energia calibrat per comparar-la.
- S'exclouen baixes velocitats, aturades, trams curts i viatges interromputs. El perfil no representa encara consum urbà complet, auxiliars en aturades ni cost total d'una ruta.
- El pendent net pot ocultar pujades i baixades dins el mateix tram. L'altitud GPS no substitueix un altímetre calibrat. No es calcula un coeficient energètic causal per metre de desnivell.
- No es modelen temperatura, vent, massa, pneumàtics, climatització o degradació de bateria; falta validar signe, unitats i precisió al vehicle real.
- No inclou mapes, rutes, carregadors ni optimització de parades.

## Fitxers

Nous: `ConsumptionModel.java` (contracte), `ConsumptionLearner.java` (nucli pur), `ConsumptionRepository.java` (SQLite), `core-tests/ConsumptionTests.java` i aquest document. Modificats: `Store.java` (v2/migració), `scripts/test-core.ps1` (execució de proves), `SmokeInstrumentation.java` (proves SQLite), `README.md` i `docs/VALIDATION.md` (documentació).

## Verificació

Vegeu `docs/VALIDATION.md` per als resultats d'execució i les limitacions de l'entorn. Les dades de les proves són sintètiques i no entren al perfil real.
