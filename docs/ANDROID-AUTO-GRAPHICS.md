# Quadre gràfic i identificació de build

Branca: `feature/android-auto-minimal`. Estat del 4 de novembre de 2026.

**Actualització 6/11:** el codi Android Auto ara és a `main`, també a release. Aquest document conserva els resultats històrics. Per a l'absència al cotxe, segueix la [nova prova d'instal·lació amb KingInstaller/Shizuku i variant independent](ANDROID-AUTO-VEHICLE.md); la seva admissió física encara està pendent.

## Implementació

- Tres indicadors semicirculars en una `SectionedItemTemplate`: **tots tres en línia quan PTrack disposa de ≥600 dp d'amplada**. Amb menys amplada es reserven dues seccions: potència/bateria a dalt i intensitat a baix a l'esquerra. Es mesura l'espai assignat a PTrack, també quan Android Auto mostra una altra app al costat. Potència kW i corrent A amb zero central, SOC i energia BMS restant. Verd entrant, taronja sortint, blau SOC. Escales inicials de potència −100/+150 kW i corrent −200/+300 A, ampliades segons els pics observats. Els números principals s'arrodoneixen per llegibilitat.
- Les imatges són `CarIcon` de cada `GridItem`, no una superfície de projecció lliure. El host decideix amplades, alineació i desplaçament. **La nova plantilla permet imatges d'uns 160 px amb `GridSection.ITEM_SIZE_LARGE`**, superant els 88 px de l'anterior `GridTemplate`. Els valors de mida de les dues classes no són equivalents: LARGE és 3 a GridSection i 4 a GridTemplate. Es conserva un marge transparent dins la imatge perquè els arcs no es toquin. En una fila se sol·licita LARGE; en dues files, LARGE amb alçada ≥600 dp o MEDIUM amb menys alçada. No es garanteix ocupar tota la superfície ni posicionar cada columna amb píxels fixos.
- Car App Library debug 1.7.0 negocia Car API 8 amb el Pixel. Després de la [migració Kotlin](KOTLIN-MIGRATION.md), la configuració és Kotlin 2.3.21 / AGP 8.13.2 / Gradle 8.13, mantenint SDK 34. En hosts amb API inferior a 8 hi ha una alternativa textual `PaneTemplate`, encara no provada en un host antic.
- Revisió visual a petició de l'usuari: la principal mostra només els tres rellotges i el seu nom, sense les línies de consum, velocitat ni estat. Aquestes dades són als detalls. El fons de les imatges és **transparent**, perquè sigui exactament el mateix que el del host, sense quadrats blaus. Arcs ampliats, números més grans i sense les anotacions minúscules de rang/sentit del corrent. El SOC conserva l'energia BMS dins del rellotge. El sentit del flux i l'energia actual també són als detalls. La superfície de plantilles observada és obscura també amb mapes de dia; els números es mantenen clars independentment del mode dia/nit del mapa.
- «Detalls del trajecte» utilitza `ParkedOnlyOnClickListener`. Sis files agrupen estat, informació general, balanç energètic, pics, bateria i autonomia. No obre SQLite al fil d'interfície.

`RecordingService` conserva el mateix transport i calendari de consultes. Només publica també el resultat del senyal `energy` que ja consultava: BMS D865, `[B4:B5]/64` kWh. No es calcula energia restant a partir d'una capacitat nominal inventada. No es modifica l'aprenentatge del consum.

### Mides disponibles a GridSection 1.7.0

| Mida | Constant | Imatge aproximada observada en aquest DHU |
| --- | --- | --- |
| Petita | `ITEM_SIZE_SMALL` (1) | 64 px |
| Mitjana | `ITEM_SIZE_MEDIUM` (2) | 88 px |
| Gran | `ITEM_SIZE_LARGE` (3) | 160 px |

Són preferències de mida per al host, no dimensions fixes universals. El dibuix té un marge transparent dins la imatge. El llindar de 600 dp és una decisió de disseny de PTrack; no una garantia de columnes imposada per Android Auto. La branca estreta 2+1 no s'ha verificat amb els rellotges en el perfil Narrow.

## Acumulació i historial

`TripStats` és l'únic algorisme d'integració. `TripSummary` afegeix màxims, SOC/energia inicial i final, deltes, mitjanes i autonomia. Es manté la integració trapezoïdal i no s'interpolen buits de més de 10 s. L'energia entrant inclou regeneració i càrrega; els pics de regeneració s'infereixen només amb velocitat ≥5 km/h, separats dels pics entrants totals. El signe físic al Peugeot continua pendent de contrast.

L'autonomia és una estimació basada en energia BMS i consum net, només amb ≥30 mostres, ≥1 km, ≥60 s coberts, cobertura ≥80%, consum net positiu i sense entrada estacionària detectada. L'energia dels extrems ha de ser a menys de 10 s de la primera/última mostra. Dades absents o antigues es mostren com a no disponibles.

SQLite passa de v2 a **v3** afegint `trip_summaries`, sense eliminar mostres, lectures, trajectes ni taules d'aprenentatge. El resum (format JSON intern 2) es desa amb transacció en finalitzar o recuperar un trajecte interromput. Els antics es deriven de les seves lectures originals en consultar-los; SQL NULL no es converteix en zero. L'historial del mòbil mostra el resum complet i l'exportació JSON esquema 2 inclou la nova taula. La còpia local privada autoritzada és a `artifacts/android-auto-graphics/pre-v3-databases.tar`, ignorada per Git; no s'ha de pujar.

## Identificar l'APK al mòbil

Peu fix sota el contingut desplaçable, visible a les tres pestanyes:

- Versió i codi, variant debug/release i presència d'Android Auto experimental.
- Branca, commit de 12 caràcters i estat `clean`/`modified`/`unknown`.
- Data i hora de generació de la build en UTC.

Els camps s'incorporen a `BuildConfig` en compilar. `modified` indica que l'APK inclou canvis no representats només pel commit mostrat. Si no hi ha Git, es mostra `unknown`. El text es pot seleccionar/copiar. Release no inclou el servei experimental d'Android Auto; debug sí. No canvia l'identificador de paquet ni obliga a desinstal·lar o perdre dades.

## Evidències i límits de les proves

- Nucli: **32 + 39 + 1065 + 62 = 1198 comprovacions PASS**.
- Debug, APK instrumentada i lint compilats. `SmokeInstrumentation` al Pixel: **PASS**, incloent migracions v1/v2→v3, conservació de dades/NULL, desament automàtic, reobertura, derivació de trajectes antics, aprenentatge, PDF i activitat del mòbil. Les proves utilitzen bases aïllades.
- DHU real amb Pixel: tres rellotges vistos a 800×480, valors sintètics amb marca SIMULACIÓ, detall obert en mode aparcat i entrada bloquejada amb restriccions de conducció. Captures locals `gauges-800-final.png`, `parked-click-message.png` i `details-driving-blocked.png` dins `artifacts/android-auto-graphics/`. Corresponen a la build anterior al peu informatiu i als últims ajustos de textos/pics.
- Build final amb peu informatiu: **Wide 1280×720 provat**, amb PTrack a la zona principal de 770 dp i Waze simultàniament a la zona secundària. Verificats visualment `drive`, `regen`, `charge`, `standstill`, `disconnect`, `reconnect`, `finish` i `freeze` (caducitat després de 12 s). El detall mostra «Finalitzat», distància, durada, mitjanes, energies i pics conservats. Captures locals `gauges-wide-drive.png`, `wide-regen.png`, `wide-charge.png`, `wide-standstill.png`, `wide-disconnect.png`, `wide-reconnect.png`, `wide-finish.png`, `wide-finished-details.png` i `split-stale.png`. Alguns textos secundaris es trunquen: el host controla la mida de les cel·les; les unitats i valors complets són al detall.
- Perfil Narrow carregat i projecció Android Auto observada, però **els rellotges de PTrack no s'hi han validat**. Aquest perfil és només una prova tècnica de redimensionament; no representa el requisit de pantalla partida aclarit per l'usuari.
- La font de prova del cotxe és només en memòria: no escriu trajectes ficticis a la base real. El desament de finalització es verifica amb les proves instrumentades aïllades.
- En acabar s'ha executat `-Scenario real` i s'ha reobert PTrack al DHU: sense marca SIMULACIÓ, valors actuals no disponibles amb l'OBD desconnectat. Captura `final-real.png`. El DHU queda obert; no s'ha fusionat a `main`.
- L'usuari ha provat el Peugeot i **PTrack no apareix**. Tampoc apareix a «Personalitza el menú». Per tant, no es declara compatibilitat funcional al vehicle.

## Pantalla partida: dues apps alhora

L'usuari ha aclarit que es refereix al dashboard d'Android Auto amb una app principal i una de secundària, no a distribuir verticalment els rellotges.

| Cas | Resultat executat |
| --- | --- |
| PTrack principal + Waze secundària | **Verificat al DHU Wide**, tres rellotges simultanis, dades canviants i detalls. La graella rep 770 dp i s'adapta a aquest espai. |
| Waze principal + PTrack secundària | **No aconseguit**. En prémer el mapa secundari, el host passa a mapa principal i una targeta de l'app multimèdia activa; PTrack desapareix de la vista. `GridTemplate` no selecciona el destí secundari. |
| Widgets nous d'Android Auto | **Disponibilitat no confirmada al Pixel**. No s'ha implementat ni donat per provat un widget de PTrack. |

«Widgets de la barra de tasques · Mostra controls ràpids per a apps» és la funció de controls de música/trucades/navegació. No és un selector de rellotges OBD ni l'opció nova «Car widgets». No s'ha canviat aquesta preferència.

Google ha [anunciat widgets mòbils a Android Auto el 2026](https://android-developers.googleblog.com/2026/05/android-for-cars-unifying-platforms-premium-experiences.html) i ha publicat una [guia d'adaptació actualitzada el 20/10/2026](https://developer.android.com/design/ui/mobile/guides/widgets/adapt). Per això no es declara impossible qualsevol widget futur basant-se en els widgets tradicionals. La disponibilitat en aquesta instal·lació, l'admissió d'un widget local i la seva freqüència d'actualització s'han de provar abans d'oferir-lo com a solució funcional. Un widget de mòbil per si sol no demostraria projecció al cotxe. No es falseja una categoria multimèdia o navegació per intentar ocupar la zona secundària.

## Bloqueig al vehicle

El 4/11, ADB confirma `cat.ptrack` debug habilitat i instal·lat, amb `PTrackCarAppService` resoluble i categoria IoT experimental. L'instal·lador consta com a `null` (instal·lació local). Logcat actual no conserva un rebuig específic suficient per atribuir amb certesa el cas del vehicle a una única comprovació.

La [documentació oficial de proves en vehicles](https://developer.android.com/training/cars/testing#test-in-real-vehicles) exigeix un origen de confiança per a apps Car App Library. «Unknown sources» no s'aplica a aquesta biblioteca. La via oficial de prova en vehicle és Internal App Sharing o una pista de prova interna, sense publicació pública ni revisió de Play. Això requeriria configurar distribució privada i comprovar-ne l'acceptació: no s'ha fet ni es promet que validi la categoria IoT, que no descriu realment PTrack. No s'han falsejat instal·ladors ni canviat categories per simular una acceptació.

El DHU funciona com a entorn de desenvolupament i no prova aquest filtre del vehicle. Activar el mode desenvolupador no elimina les restriccions de categoria o confiança. No hi ha evidència que més canvis de dibuix resolguin l'absència al menú.

## Repetir les proves

```powershell
./scripts/test-core.ps1
./scripts/android-auto-dhu.ps1 -Action Build -Offline
./scripts/android-auto-dhu.ps1 -Action Install
./scripts/android-auto-dhu.ps1 -Action Start -Profile Normal
# Tanca el DHU abans de canviar de perfil:
./scripts/android-auto-dhu.ps1 -Action Start -Profile Wide
./scripts/android-auto-dhu.ps1 -Action Start -Profile Narrow
```

Perfils: Normal 800×480; Wide 1280×720; Narrow 1280×720 amb marge de 880, àrea lògica de 400×720. El host pot redistribuir el contingut.

En una altra PowerShell: `-Action Simulate -Scenario drive`, després `regen`, `charge`, `standstill`, `freeze` (>10 s), `disconnect`, `reconnect`, `finish`. Sempre acabar amb `-Scenario real`. Per reprendre després de `finish`, tornar primer a `real`. Al Pixel, servidor de la unitat principal actiu i depuració USB autoritzada.

Al Peugeot: comprovar primer el peu de build al mòbil, confirmar variant debug amb AA experimental, i comprovar que PTrack apareix a «Personalitza el menú» abans de provar indicadors. Si continua absent, el següent pas és resoldre l'admissió del host; no és una prova OBD. La projecció final i els signes reals es validaran només quan pugui obrir-se al vehicle.

## Fitxers

Build/peu: `app/build.gradle.kts`, `MainActivity.kt`. Càlcul/publicació: `Sample.kt`, `TripStats.kt`, `TripSummary.kt`, `TripSummaryText.kt`, `GaugeScale.kt`, `VehicleTelemetry.kt` i el punt de publicació d'energia a `RecordingService.kt`. Persistència: `Store.kt`. Cotxe debug: `PTrackCarScreen.kt`, `GaugeRenderer.kt`, `CarTestFeed.kt`. Proves: `SummaryTests.kt`, `SmokeInstrumentation.kt`, `test-core.ps1`. Els fonts de l'app són a `app/src/{main,debug,androidTest}/kotlin`. Entorn: `android-auto-dhu.ps1`, `dhu/wide.ini`, `dhu/narrow.ini`. Documents: aquest fitxer, README, VALIDATION i DHU-QUICKSTART. Les captures anteriors a la migració documenten les builds Java d'aquelles revisions.
