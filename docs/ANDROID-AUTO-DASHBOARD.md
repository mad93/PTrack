# Quadre Android Auto

Implementació del **3–4 de novembre de 2026**, a `feature/android-auto-minimal`, sobre el client mínim ja validat. **Compilada, instal·lada al Pixel 10 i provada en projecció real amb el DHU oficial.** Les proves de valors al DHU utilitzen una font sintètica explícita. **No s'han validat encara les lectures reals d'aquesta pantalla al Peugeot.**

APK: `artifacts/android-auto-dashboard/PTrack-android-auto-dashboard-debug.apk`.
SHA-256: `6c6b904431038ede32695978f74c30343c1a809afc4d0b1db364a0704a275b60`.

## Pantalles i indicadors

S'ha ampliat el mateix `CarAppService → Session → Screen` amb `PaneTemplate` (Car API 1). Categoria, descriptor, dependència i validador debug es conserven. No hi ha una Activity projectada ni una categoria nova.

| Pantalla | Valors |
| --- | --- |
| Principal | Potència de bateria en kW i direcció de l'energia; consum mitjà net en kWh/100 km; SOC i connexió. Els tres es veuen alhora al DHU 800×480. |
| Detalls | Consum instantani i velocitat OBD; energia consumida i energia entrant del trajecte. |
| Bateria | Tensió, intensitat, estat i distància integrada. |

Un botó de capçalera porta de Principal a Detalls i després a Bateria. Bateria permet tornar directament a Principal; el botó enrere del host conserva el flux habitual. Cap pantalla requereix desplaçar-se per veure els seus valors. Hi ha com a màxim tres pantalles al flux.

Les plantilles proporcionen tipografia, contrast i tema dia/nit. La prova s'ha fet amb la presentació fosca del host. L'app no pot imposar lliurement la mida de les xifres, les columnes o el tema del vehicle: s'ha prioritzat visibilitat dels tres indicadors principals i ús de plantilles admeses. No s'han afegit animacions ni rellotges Canvas.

## Arquitectura i càlculs reutilitzats

```text
ELM327 → únic socket de RecordingService → Profile → Sample
                                         ↓
                                  Store.add (existent)
                                         ↓
                              VehicleTelemetry.LIVE.accept
                                   ├─ TripStats.add
                                   └─ Snapshot immutable → consumidor Android Auto

Mòbil: continua llegint RecordingService.latest/state (la mateixa Sample).
CarTestFeed.SOURCE → només el consumidor debug, si la prova s'activa explícitament.
```

`RecordingService` continua sent l'únic propietari del Bluetooth i del lector ELM. Les úniques modificacions són publicar una mostra **després de desar-la**, publicar els canvis d'estat, marcar el final del trajecte després de `Store.finish`, i publicar el valor numèric del cable quan la consulta lenta **existent** de `charging` ja s'ha executat. No s'han afegit ordres, consultes, temporitzadors de captura, sockets o permisos de Bluetooth.

`VehicleTelemetry` manté un únic `TripStats` incremental per trajecte i publica un resum immutable atòmic amb la mostra, l'edat monotònica, l'identificador i el mode de dades. No implementa una segona fórmula d'integració. Els informes continuen reconstruint els seus `TripStats` des de SQLite; les proves comparen ambdós resultats. No cal llegir l'històric al fil principal ni reconstruir-lo en obrir Android Auto.

- **Potència:** `Sample.power() = voltage × current / 1000`. Són kW de bateria, no potència mecànica.
- **Consum instantani:** no existia com a valor reutilitzable; s'afegeix `power × 100 / speed`, només amb mostra recent i velocitat **≥5 km/h**. Sota el llindar: `--`. Pot ser negatiu.
- **Consum mitjà:** `TripStats.consumption()`, net d'energia entrant i només des de **100 m**. No s'utilitza una predicció del model d'aprenentatge com si fos consum mesurat.
- **Energia/distància:** mateixos `TripStats.consumed`, `.recovered` i `.km`: integració trapezoïdal, separació dels creuaments de signe i omissió de buits **>10 s**. Duplicats o temps retrocedits no rejoveneixen la lectura ni dupliquen totals.
- **SOC, tensió, intensitat i velocitat:** mateixa `Sample` acceptada pel productor existent.

No s'han modificat `Sample`, `TripStats`, `ElmProtocol`, `Profile`, `MainActivity`, `Store`, l'esquema SQLite ni les classes d'aprenentatge. L'app del mòbil continua utilitzant la seva interfície anterior. El client de cotxe no crida `startService`, `stopService`, `connect`, `Store` ni l'aprenentatge.

Obrir/tancar una pantalla només subscriu/desubscriu el consumidor; no reinicia totals, trajectes o connexions. Quan acaba el trajecte, el consum mitjà principal passa a `--`; l'energia final es conserva a Detalls amb l'etiqueta «Darrer». El següent trajecte crea un acumulador nou. Si Android mata tot el procés, s'aplica la recuperació existent del registre; no s'ha introduït una política nova de represa de trajectes.

## Signes, regeneració i càrrega

El perfil empaquetat `stellantis-ecmp.json`, BMS `D816`, documenta la fórmula `[B4:B7]/64 - 1200` i **descàrrega positiva**. Les notes del perfil descriuen contrastos amb calefacció i comptadors energètics en altres proves e-CMP. S'han comprovat els bytes amb corrents positius i negatius i s'ha conservat el signe de tota l'app. Això **no substitueix una comprovació física en el Peugeot de l'usuari**.

La UI mostra:

- Positiu: «Consum de bateria».
- Negatiu amb velocitat ≥5 km/h: «Regeneració · energia entrant», interpretació basada en el signe del perfil i el moviment.
- Negatiu amb velocitat ≤1 km/h i cable `D854 == 1` rebut fa ≤10 s: **«Càrrega probable · cable detectat»**.
- Negatiu estacionat sense confirmació recent: «Energia entrant · origen no confirmat».

**No es pot donar una xifra separada de regeneració pura amb els acumuladors actuals.** `TripStats.recovered` inclou tota l'energia entrant, també una possible càrrega externa. La pantalla l'etiqueta «Energia entrant · regen./càrrega». El consum mitjà net també segueix aquest criteri existent. No s'ha inventat una separació ni s'ha canviat el registre per introduir-la. El senyal lent de cable no s'ha accelerat i sovint caducarà: la classificació conservadora és intencionada.

## Actualització i caducitat

No hi havia un bus reactiu previ; `VehicleTelemetry` afegeix observadors lleugers amb publicació sincronitzada i instantànies immutables. Els callbacks del consumidor només programen feina al `Handler` principal. Una excepció d'un observador no atura la gravació.

El consumidor coalescent demana com a màxim **una invalidació per segon**, només si hi ha canvis i mentre el seu cicle de vida està iniciat. El host pot espaiar les respostes i mantenir iniciada una pantalla que té en memòria; no es promet una freqüència de renderització fixa. Les etiquetes i l'estructura romanen constants durant les actualitzacions; els valors canvien en els textos secundaris. La capçalera només canvia quan es canvia entre mode real i simulació, no a cada mostra.

Un únic temporitzador de caducitat retira les lectures actuals als **10 s** de l'última mostra, encara que no arribi cap event nou. També caduca la confirmació de cable. Cal afegir-hi el possible retard del host. Desconnexions i cicles invàlids retiren immediatament la mostra publicada. SOC, potència, intensitat, tensió, velocitat i consum instantani passen a `--`; els acumuladors d'un trajecte encara actiu es conserven com a resum, no com a lectures actuals.

## Prova sintètica reproduïble

Només l'APK **debug** incorpora `CarTestFeed` i `CarTestReceiver`. El receptor exigeix el permís de signatura `android.permission.DUMP`, disponible per al shell ADB comprovat al Pixel. No s'ha sol·licitat aquest permís a PTrack ni als usuaris. No existeix a release.

La font de prova és un objecte separat de `VehicleTelemetry.LIVE`. No modifica les dades del mòbil, no connecta l'ELM, no crida `RecordingService` i no escriu SQLite. La capçalera de **totes** les pantalles mostra «SIMULACIÓ». La simulació existent del mòbil també arriba etiquetada si és el productor qui està en mode demo.

```powershell
./scripts/android-auto-dhu.ps1 -Action Run -Offline
# En una altra PowerShell, un cop obert PTrack al DHU:
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario drive
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario regen
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario charge
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario freeze
# Espera almenys 11 s i comprova -- / Dades no disponibles.
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario disconnect
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario reconnect
./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario real
```

`drive` genera valors canviants i sembra un minut fictici per poder comprovar el consum mitjà sense esperar 100 m. `freeze` atura només les mostres; `disconnect` publica la pèrdua; `reconnect` reprèn el mateix trajecte de prova. `real` cancel·la el generador i torna a LIVE. Les actualitzacions automàtiques de prova tenen un límit de 10 minuts. Una recreació del procés torna al mode real per defecte. **En acabar aquesta sessió s'ha executat `real` i s'ha verificat la pantalla sense cap marca de simulació, amb `--` i Desconnectat.**

## Resultats executats

| Prova | Resultat real |
| --- | --- |
| Compilació debug, APK instrumentada i lint | BUILD SUCCESSFUL. Lint: **0 errors, 6 avisos** (4 previs i 2 recursos de text de la prova mínima ara no utilitzats). |
| Compilació release i comprovació binària debug/release | BUILD SUCCESSFUL; servei de cotxe i receptor de prova absents del manifest release. |
| `test-core.ps1` | **32 nucli + 39 aprenentatge + 1065 telemetria = 1136 comprovacions superades**. |
| Proves noves | W→kW, consum i velocitat 0/4,99/5, signes, bytes del perfil, absències, rellotge monotònic, caducitat, tall/reconnexió, buits, deduplicació, final/nou trajecte, observadors, excepcions de consumidor i lectura concurrent. |
| `SmokeInstrumentation` al Pixel 10 | **PASS**: migració/rebuild/query d'aprenentatge, SQLite, recuperació de viatge, SOC, PDFs ca/es/en, render i obertura de la pantalla mòbil. Executada després d'instal·lar la disposició final de pàgines; el posterior ajust del limitador s'ha comprovat al DHU. Utilitza bases de prova. |
| DHU oficial + Pixel 10 / Android 17 / Android Auto 17.9 | Obertura del quadre, tres valors principals simultanis, valors canviants, Detalls, Bateria i retorn directe: **verificats visualment**. |
| Consum / regeneració / càrrega sintètics | Positiu canviant, `-16 kW` i `-40 kWh/100 km`; càrrega probable `-8 kW`, 0 km/h i consum instantani `--`: **verificats**. |
| Absència de mostres i connexió | `freeze` → `--` / Dades no disponibles; `disconnect` → Desconnectat; `reconnect` → valors de nou: **verificats al DHU**. |
| Sortir i reobrir la pantalla | Totals de la font de prova continuen; no tornen a zero: **verificat**. La independència dels observadors també es comprova al nucli. |
| No escriure dades sintètiques reals | SHA-256 de `ptrack.db` i `ptrack.db-wal` idèntics abans/després de les proves de pantalla. |
| Errors/bloquejos | Sense crash del procés final de PTrack observat al buffer crash. Les pantalles han continuat refrescant i navegant durant les proves. |
| OBD físic, registre mentre el cotxe connecta/desconnecta AA, tall/reconnexió Bluetooth real i signe al Peugeot | **Pendent de prova al vehicle.** Els escenaris lògics estan provats amb font de prova; no s'atribueix una prova física inexistent. |

Captures a `artifacts/android-auto-dhu/`: `dashboard-final-current.png`, `dashboard-final-details.png`, `dashboard-final-battery.png`, `dashboard-stale.png`, `dashboard-disconnected.png`, `dashboard-reconnected.png`, `dashboard-regen.png`, `dashboard-final-charge.png`, `dashboard-final-reopened.png`, `dashboard-final-real-empty.png`. Algunes proves de transicions es van capturar abans de l'última compactació visual; el productor, la caducitat i els càlculs són els mateixos que a l'APK final. El disseny de les tres pàgines s'ha comprovat després d'instal·lar-lo. L'últim ajust del limitador, que també limita peticions mentre el host no respon, s'ha tornat a instal·lar i comprovar amb `drive` i `freeze`: `dashboard-throttled-live.png` i `dashboard-throttled-stale.png`.

Els intents de captura Windows van donar timeout; les captures verificades provenen de l'ordre nativa `screenshot` del DHU. No s'ha utilitzat una maqueta ni una imatge generada.

## Fitxers modificats

| Fitxer | Canvi |
| --- | --- |
| `app/src/main/java/cat/ptrack/RecordingService.java` | Publicació després de persistència, estats, cable ja llegit i final de trajecte. |
| `app/src/main/java/cat/ptrack/VehicleTelemetry.java` | Capa compartida, instantànies atòmiques, acumulador `TripStats`, caducitat i classificació conservadora. |
| `app/src/main/java/cat/ptrack/CarDashboard.java` | Model de presentació i format dels valors; no captura dades. |
| `app/src/debug/java/cat/ptrack/car/PTrackCarScreen.java` | Tres pantalles de plantilles, subscripcions, coalescència i caducitat. |
| `app/src/debug/java/cat/ptrack/car/PTrackCarAppService.java` | Manté el mateix punt d'entrada; comentari actualitzat. |
| `app/src/debug/java/cat/ptrack/car/CarTestFeed.java` | Font sintètica separada, només en memòria. |
| `app/src/debug/java/cat/ptrack/car/CarTestReceiver.java` i manifest debug | Comandes de prova restringides al shell autoritzat. |
| `core-tests/TelemetryTests.java`, `scripts/test-core.ps1` | Proves noves mantenint les suites anteriors. |
| `scripts/android-auto-dhu.ps1` | Acció `Simulate` amb escenaris explícits. |
| `scripts/test-android-auto-apk.ps1` | Comprova també l'absència del receptor de prova a release. |
| README i docs | Estat actual, resultats i instruccions. |

## Com provar el Peugeot

1. Actualitza amb l'APK debug lliurada, sense desinstal·lar ni esborrar dades. Executa `-Action Simulate -Scenario real` si has fet proves (o reinicia el procés perquè torni al mode real). Comprova que no hi diu SIMULACIÓ.
2. Amb el vehicle estacionat, obre PTrack al mòbil i inicia la connexió OBD com sempre. Android Auto **no** l'inicia. Comprova que el mòbil rep lectures reals abans d'obrir el quadre al cotxe.
3. Connecta Android Auto al Peugeot i obre PTrack. Compara SOC, potència i intensitat amb el mòbil. Comprova Detalls i Bateria. L'acceptació d'una APK manual al vehicle encara és pendent: haver superat DHU no l'acredita.
4. Contrasta el signe amb una càrrega auxiliar coneguda estant estacionat. Per verificar regeneració en moviment, deixa la revisió a un acompanyant o revisa després el registre; comprova energia entrant i el signe. Amb càrrega externa, comprova si el senyal de cable és recent abans d'interpretar l'etiqueta «Càrrega probable».
5. Amb un viatge real ja enregistrant-se, obre/tanca PTrack al cotxe i desconnecta Android Auto: el mòbil ha de continuar registrant. Prova un tall OBD curt i un >45 s segons el comportament existent; comprova que cap pantalla inicia, acaba o duplica un trajecte.
6. Desa versions i logs locals amb `-Action Logs` si hi ha un problema. No s'ha fet cap merge a `main` ni publicació Play.
