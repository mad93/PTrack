# Android Auto — estudi de viabilitat

> **Document històric de la branca anterior.** La prova mínima posterior sí que implementa un client experimental real de plantilles, sense dades OBD. Estat actual, límits de sideload i procediments: [ANDROID-AUTO-MINIMAL.md](ANDROID-AUTO-MINIMAL.md). Les afirmacions següents sobre absència de client/APK descriuen l'estudi anterior, no l'APK mínima actual.

Revisió: **3 de novembre de 2026**. Branca de treball: `feature/android-auto`.

## Resultat i abast

**No s'ha acreditat una via oficial compatible per al panell OBD demanat durant la conducció. No s'ha implementat un client Android Auto.** La biblioteca permet actualitzar text en plantilles, però això no acredita que aquest ús estigui admès com a categoria d'aplicació. S'aplica el lliurable alternatiu de l'encàrrec: investigació documentada i alternatives, sense declarar categories artificials.

Aquesta conclusió és una decisió conservadora basada en la documentació pública consultada, **no una prohibició explícita publicada per Google de tota telemetria OBD**. En particular, queda pendent confirmar amb Google si una pantalla de mesures contínues d'un adaptador OBD propi pot ser acceptada com a consulta d'estat IoT. No s'ha rebut cap confirmació ni rebuig individual de Google.

## Base de partida comprovada

Després de `git fetch origin`, `origin/main` apunta a `8563b0b` (fusió de la PR #1), amb `3a94ede` d'aprenentatge del consum com a ancestre. La nova branca parteix de `8563b0b`; el contingut de codi coincideix amb la branca inicial `feature/consumption-learning`. El `main` local estava a `467a4e4` i no s'ha modificat. No s'han fusionat ni publicat aquests canvis.

Es conserven `ConsumptionModel`, `ConsumptionLearner`, `ConsumptionRepository`, la migració SQLite v2 i les proves prèvies. No s'ha substituït el mòdul d'aprenentatge.

## Arquitectura actual inspeccionada

| Component | Responsabilitat i conseqüència per a una futura integració |
| --- | --- |
| `RecordingService` | Únic propietari del socket Bluetooth clàssic SPP; inicialitza ELM327 i consulta les lectures. Treballador únic, servei en primer pla i wake lock. |
| `Profile` / `ElmProtocol` | Descodificació del perfil e-CMP, validació de rangs i transport de consultes de lectura. |
| `Sample` | Mostra immutable; `power()` calcula `voltage × current / 1000` en kW. |
| `RecordingService.latest/state/activeTrip` | Estat compartit en memòria del procés. `latest` es publica després de desar la mostra. Els camps són volàtils, però no constitueixen un snapshot atòmic conjunt ni una API de subscripció. |
| `MainActivity` | Consulta l'estat cada 750 ms i deixa de mostrar mostres amb més de 10 s. El seu cicle de vida no governa la captura. |
| `Store` | Històric SQLite: trajectes, mostres, lectures originals i perfils de consum. |
| `TripStats` | Integra energia i distància; separa signes, omet intervals >10 s i calcula consum net només des de 100 m. S'utilitza per als resums; no és un acumulador compartit en directe del servei. |
| `ConsumptionRepository` | Analitza trajectes reals tancats en un worker; és independent de la connexió i de la UI. No s'ha d'utilitzar per calcular cada refresc de pantalla. |

El servei accepta cicles bàsics complets de fins a 8 s; les lectures són seqüencials. En un cicle invàlid o una desconnexió neteja les dades en viu. Reintenta al cap d'uns 15 s i tanca el trajecte després de 45 s sense dades. Tancar una pantalla no equival a aturar el servei.

## Categories i via oficial

La [taula oficial de categories](https://developer.android.com/training/cars) enumera àudio, comunicacions, navegació, punts d'interès, IoT i meteorologia per a experiències durant la conducció. Algunes variants de comunicació i multimèdia amb plantilles són programes limitats. Els jocs d'Android Auto només funcionen estacionats; la taula situa vídeo i navegadors a Android Automotive OS, amb les seves restriccions. No hi figura una categoria independent d'instrumentació o diagnòstic OBD. Android Automotive OS instal·lat al vehicle és una plataforma diferent d'Android Auto projectat des del mòbil; no es pot assumir que el Peugeot l'ofereixi.

La [guia d'IoT](https://developer.android.com/training/cars/apps/iot) descriu control de dispositius connectats i exigeix la categoria `androidx.car.app.category.IOT` al `CarAppService`. Els exemples domòtics no acrediten per si mateixos un panell OBD, però tampoc constitueixen una llista explícitament exhaustiva.

Les [normes de qualitat](https://developer.android.com/docs/quality-guidelines/car-app-quality) afegeixen:

- `PC-1`: funcionalitats dins dels tipus admesos.
- `IT-1`: consulta d'estat de dispositius i controls simples; configuració, edició i control detallat restringits durant la conducció.
- `NF-1`/`NF-2`: navegació real pas a pas i superfície destinada a mapes; no justifiquen dibuixar un quadre OBD sobre un mapa fictici.
- `SA-1`: restriccions d'animació; `AC-1`: fluxos limitats, amb excepcions específiques de la plataforma.

**Inferència per a PTrack:** ni disposar d'un ELM327 Bluetooth ni utilitzar una plantilla demostra l'elegibilitat IoT del producte sol·licitat. Una prova carregada en desenvolupament no acredita admissió a Google Play. Tampoc convertir-lo en una app estacionada resol automàticament la categoria.

## Plantilles, freqüència i interacció

La [Car App Library](https://developer.android.com/training/cars/apps/library) proporciona el marc oficial de pantalles. Un futur client admès utilitzaria `CarAppService → Session → Screen → Template`, amb la presentació a càrrec del host, en lloc de projectar `MainActivity` o els seus `Gauge` Canvas.

[`Screen.invalidate()`](https://developer.android.com/reference/androidx/car/app/Screen#invalidate()) sol·licita una nova plantilla: no garanteix renderització immediata ni una freqüència fixa. No té efecte si la pantalla no està almenys iniciada; les peticions repetides s'agrupen fins que es retorna la plantilla. Cal invocar-lo al fil principal. Per tant, no es pot prometre una pantalla OBD a 10/30/60 Hz.

[`PaneTemplate`](https://developer.android.com/reference/androidx/car/app/model/PaneTemplate) considera refresc una actualització que manté títol, nombre de files i títols de fila, a més del cas de càrrega. Això permetria canviar textos secundaris mantenint etiquetes fixes, si l'ús fos admès. No habilita rellotges arbitraris.

La [guia de fluxos i refrescos](https://developers.google.com/cars/design/create-apps/apps-for-drivers/plan-task-flows) explica límits de passos i refrescos espaiats pel host, dependents de funcions i fabricant. Cal adaptar-se a les capacitats disponibles; no hi ha una garantia universal d'1 Hz. Una futura UI hauria de prioritzar pocs valors, combinar-los o repartir-los segons els límits de plantilla, respectar dia/nit i mides del host i evitar configuració complexa mentre es condueix.

## Car Scanner: evidència i incertesa

S'han consultat el [web del fabricant](https://www.carscanner.info/), les [FAQ](https://www.carscanner.info/faq/) i la [fitxa de Google Play enllaçada pel fabricant](https://play.google.com/store/apps/details?id=com.ovz.carscanner). El contingut recuperat no identifica la seva API, categoria declarada, acords amb Google o condicions de revisió d'Android Auto. La ruta `/android-auto/` no ha estat accessible amb l'eina de consulta.

Es manté Car Scanner com a referència funcional aportada a l'encàrrec. **No es conclou que no tingui Android Auto ni se li atribueix cap mecanisme d'elusió. Tampoc se'n dedueix que PTrack tingui la mateixa autorització.** No s'ha inspeccionat cap APK ni s'ha fet una prova física d'aquest producte.

## Dades disponibles i contracte pendent

Aquesta és una especificació per reprendre el treball si es confirma una categoria compatible; **no és codi implementat ni comportament nou**.

| Indicador | Origen i tractament necessari |
| --- | --- |
| Potència | `Sample.power()`, kW de bateria, no potència mecànica. Conservar signe. |
| Consum instantani | `100 × power / speed`, kWh/100 km, només amb valors finits i velocitat ≥5 km/h; és un llindar proposat, no una precisió certificada. Sota el llindar, «—». |
| Consum mitjà | `TripStats.consumption()`, consum net ponderat per distància; «—» abans de 100 m. Cal reconstruir el trajecte actiu des de SQLite en un worker i incorporar cada mostra una vegada. |
| SOC, tensió, intensitat, velocitat | `Sample`, en %, V, A i km/h; no convertir absències en zero. |
| Energia consumida/entrant | `TripStats.consumed/recovered`, kWh. No etiquetar tota energia entrant com a regeneració demostrada. |

El conveni de codi és positiu = descàrrega, negatiu = energia entrant. Les proves verifiquen descodificació i fórmules sintètiques, **no el signe físic del Peugeot concret**. `Sample` no desa estat de connector ni de càrrega. Una potència negativa a velocitat zero no demostra càrrega externa; caldria un senyal fiable validat i temporalment coherent. Fins aleshores, mostrar «energia entrant» o «origen no disponible».

Una futura integració compartiria un snapshot coherent, amb edat, estat i identificador de trajecte, del mateix productor. No obriria un segon socket, no iniciaria captura en crear una pantalla i no aturaria el registre en destruir-la. L'host consultaria dades recents i alliberaria els observadors en sortir. S'haurien de provar deduplicació, reobertura de pantalla, canvi de trajecte, desconnexió, reconnexió i dades caducades abans de publicar. No s'ha afegit aquesta infraestructura mentre l'elegibilitat segueix sense acreditar.

## Alternatives viables

1. **Mantenir el panell del mòbil i l'històric actuals.** Ja consumeixen el productor existent. Revisar dades i informes amb el vehicle estacionat; no requereix cap canvi d'Android Auto.
2. **Demanar aclariment formal a Google sobre IoT.** Presentar als canals de suport de desenvolupadors de Google Play Console un cas de només lectura, valors numèrics, freqüència limitada i absència de diagnòstic interactiu; sol·licitar confirmació sobre categoria i revisió. No s'ha enviat cap missatge en nom de l'usuari. Si s'accepta aquest ús, reprendre el client amb plantilles i proves específiques.
3. **Navegació elèctrica real en una fase independent.** Si el futur planificador es converteix en navegació pas a pas o cerca real de carregadors, estudiar les categories corresponents i confirmar quina informació energètica auxiliar admeten. No garanteix un panell OBD independent; el planificador encara no està implementat.

No es proposen mirroring, overlays, categories falses ni desactivar controls del host.

## Canvis, configuració i validació

Fitxers d'aquest lliurament: aquest document, `README.md` i `docs/VALIDATION.md`. Dependències noves: **cap**. Fonts Java, manifest, Bluetooth, `RecordingService`, SQLite i aprenentatge: sense canvis. No cal cap configuració d'Android Auto: PTrack no apareixerà al seu llançador amb aquesta APK.

Els resultats d'aquesta execució i les ordres reproduïbles es documenten a [VALIDATION.md](VALIDATION.md). No s'afegeixen proves d'una integració inexistent: s'executen les proves prèvies per comprovar la base conservada. No hi ha validació al vehicle ni al DHU. El DHU no està instal·lat a l'SDK inspeccionat i no hi ha dispositius connectats ni AVD configurats.

## Procediment al Peugeot

Per comprovar la versió actual: instal·lar l'APK de depuració sobre la mateixa app, sense desinstal·lar ni esborrar dades; aparellar el Bluetooth clàssic, seleccionar-lo a PTrack i començar amb el cotxe estacionat. No mantenir Car Scanner ni una altra app connectada al mateix ELM327. Comprovar SOC, tensió i signe amb càrregues auxiliars; encarregar a un acompanyant la revisió de conducció/regeneració. Amb el cotxe aturat, comprovar signe durant càrrega externa sense confondre'l amb regeneració.

Connectar Android Auto del mòbil al Peugeot i comprovar que el registre continua al mòbil; **no esperar una pantalla PTrack al cotxe**. Apagar la pantalla del mòbil, desconnectar Android Auto i revisar després mostres, trajecte i exportació JSON. Provar un tall OBD curt i un de més de 45 s segons [VALIDATION.md](VALIDATION.md). Tot aquest procediment resta pendent d'execució física.

Si s'aprova una futura integració, començar amb el [Desktop Head Unit oficial](https://developer.android.com/training/cars/testing/dhu), amb un mòbil Android Auto compatible, abans de provar el Peugeot estacionat. Validar host, mides de pantalla, moviment simulat, límits de refresc i continuïtat del registre. Superar el DHU no certifica funcionament al vehicle ni aprovació a Google Play.
