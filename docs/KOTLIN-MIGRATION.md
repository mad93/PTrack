# Migració completa a Kotlin

4 de novembre de 2026 · branca `feature/android-auto-minimal`.

## Abast

Els 27 fonts de l'app (21 main, 5 debug i la instrumentació) i les quatre suites del nucli s'han traslladat a **31 fitxers Kotlin**. No queda codi Java propi a `app/src` ni a `core-tests`. Els models són `data class`, els serveis/componentes Android mantenen els mateixos noms de classe, i els recursos i el manifest conserven les mateixes funcions.

Rutes actuals:

- `app/src/main/kotlin/cat/ptrack/`: app del mòbil, OBD, perfil, dades, aprenentatge, PDF i models.
- `app/src/debug/kotlin/cat/ptrack/car/`: Android Auto i font de prova separada.
- `app/src/androidTest/kotlin/cat/ptrack/SmokeInstrumentation.kt`: proves instrumentades amb bases aïllades.
- `core-tests/*.kt`: quatre suites Kotlin/JUnit.
- `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`: configuració Kotlin DSL.

Els fitxers Java generats per Android (`BuildConfig`) i els components de tercers no són fonts propis. Els scripts de Windows continuen en PowerShell. S'han conservat les Views/Canvas del mòbil i les plantilles de Car App Library, amb el mateix aspecte i distribució adaptable de rellotges.

## Eines

| Component | Versió |
| --- | --- |
| Kotlin / plugin Android Kotlin | 2.3.21 |
| Gradle | 8.13, distribució amb SHA-256 fixat |
| Android Gradle Plugin | 8.13.2 |
| JDK / bytecode JVM | 17 |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |
| Android Build Tools | 35.0.0 |
| Car App Library, només debug | 1.7.0 |

La combinació s'ha triat segons la [matriu oficial de Kotlin/Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html), els [requisits Android de Kotlin/D8/R8](https://developer.android.com/build/kotlin-support) i les [notes d'AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes). [Kotlin 2.3.21](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.android/2.3.21) és una versió estable. La primera compilació necessita xarxa per completar les dependències; els scripts admeten `-Offline` després.

## Conservació del comportament

- Mateix paquet `cat.ptrack`, versió d'aplicació i signatura debug local. Una actualització amb `adb install -r` conserva les dades; no es desinstal·la l'app.
- SQLite continua en **versió 3** i el resum intern en format 2. Les vuit sentències `CREATE TABLE/INDEX` de Store/ConsumptionRepository s'han comparat amb la referència Java: són idèntiques. La conversió de llenguatge no afegeix cap migració de dades.
- Es mantenen les consultes del perfil e-CMP, socket Bluetooth únic, quatre senyals bàsics, extres per torns i lectura raw cada deu cicles. Mateixos temps de sondeig, reconnexió i tancament del trajecte.
- Es conserven fórmules, convenció de signes, buits no integrats, tractament de NULL/NaN, filtres d'aprenentatge i caducitat de telemetria. Les traduccions de la pantalla principal s'han comparat amb els triplets de la referència Java.
- La font sintètica d'Android Auto continua separada de LIVE, Bluetooth i SQLite. El servei i receptor de prova continuen només a debug.
- PDF, JSON, historial, associació OBD i peu de build conserven les funcions existents.

Abans de començar s'han guardat els fonts Java, les proves, un diff i l'APK anterior a `artifacts/kotlin-migration/`, ignorat per Git. L'APK Java de referència té SHA-256 `84BD8F6F294C142D295EBFF3C021447C11C3CB52A6C38EC5F3C9BD7F4FF1DA2D`. La còpia privada de bases autoritzada anteriorment continua només a `artifacts/android-auto-graphics/pre-v3-databases.tar`.

## Proves

La referència Java i les quatre suites Kotlin han passat **les mateixes 1.198 comprovacions**:

| Suite | Comprovacions |
| --- | ---: |
| Integració, perfil, ELM i protecció d'escriptura | 32 |
| Aprenentatge del consum | 39 |
| Telemetria, concurrència, caducitat i cicle de vida | 1.065 |
| Resums, absències, energia BMS i geometria | 62 |

Les quatre suites s'executen amb JUnit a `:app:testDebugUnitTest`. No necessiten dispositiu. Logs comparables: `artifacts/kotlin-migration/core-baseline.txt` i `core-kotlin.txt`.

```powershell
./scripts/test-core.ps1
./scripts/android-auto-dhu.ps1 -Action Build
# Després de la primera resolució de dependències:
./scripts/test-core.ps1 -Offline
./scripts/android-auto-dhu.ps1 -Action Build -Offline
```

Els resultats de compilació, lint i proves de dispositiu de la build final es recullen a [VALIDATION.md](VALIDATION.md). Després de reconnectar el Pixel, s'han executat les proves instrumentades i la projecció al DHU amb aquesta mateixa APK Kotlin.

- Instal·lació al Pixel 10 amb `adb install -r`: **Success**. Llançament de MainActivity i peu de build verificats amb UI Automator; coincideix l'hora UTC indicada més avall.
- Instrumentació: **PASS** explícit per migracions v1/v2→v3, resums automàtics/reobertura/NULL antics, migració i reconstrucció de consum, persistència SQLite, recuperació de trajecte interromput, SOC, PDF en ca/es/en, renderització i activitat. Log: `artifacts/kotlin-migration/pixel-instrumentation.txt`.
- DHU oficial Wide 1280×720: **projecció verificada**, amb PTrack principal (770×700 dp, Car API 8) i Waze secundària. Tres rellotges grans en línia, consum, regeneració, càrrega, vehicle aturat, caducitat després de congelar lectures, desconnexió/reconnexió i detalls finalitzats amb totals conservats: comprovats visualment amb dades sintètiques. Captures `artifacts/kotlin-migration/dhu-*.png`.
- Les dues pàgines del PDF català sintètic s'han inspeccionat visualment; les proves instrumentades també generen i renderitzen les versions castellana i anglesa. No s'afirma una comparació píxel a píxel amb la versió Java.
- En acabar s'ha restaurat `Scenario real`: captura `dhu-final-real.png` sense marca de simulació i amb lectures `--`, esperant telemetria real. La prova sintètica de pantalla no registra un trajecte real.

Build final local: **BUILD SUCCESSFUL** amb `--offline`, incloent les quatre suites, debug, release, APK instrumentada i lint. Lint: **0 errors / 8 avisos** (SDK/target antics, actualitzacions d'eines, anotació de disponibilitat, servei experimental exportat, regles de backup i dos recursos antics sense ús). Es mantenen les API de Companion Device Manager compatibles amb els Android anteriors, amb avisos de deprecació del compilador. No s'ha desactivat cap comprovació del lint.

El Wrapper JAR regenerat té el SHA-256 oficial `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`. El llançador Windows s'ha tornat a executar després de regenerar-lo i la passada final ha acabat amb codi zero. El certificat debug de l'APK antiga i la Kotlin és idèntic: `d57b78e1d06d62ce309942ec9a4d392530cb006f530ded9d8298295b0dba0731`.

APK preparada: `artifacts/kotlin-migration/PTrack-kotlin-debug.apk`, SHA-256 `4B08A98E2DF1957BD58232C3C41BD630740C8ADAEF5FC5CFC3A3BC2580961469`. Peu de build UTC: `2026-11-04T19:52:48.287793300Z`. Els logs, XML JUnit, lint HTML i metadades són a la mateixa carpeta. L'APK instrumentada és `PTrack-kotlin-androidTest.apk`. Les còpies habituals a `artifacts/android-auto-dhu/` i `artifacts/android-auto-graphics/` s'han actualitzat.

### Repetir les proves amb el Pixel

Amb un únic dispositiu autoritzat (si n'hi ha més, afegir `-s` a ADB i `-Serial` al script):

```powershell
./scripts/android-auto-dhu.ps1 -Action Doctor
./scripts/android-auto-dhu.ps1 -Action Install
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adb install -r artifacts/kotlin-migration/PTrack-kotlin-androidTest.apk
& $adb shell am instrument -w cat.ptrack.test/cat.ptrack.SmokeInstrumentation
# Comprovar explícitament el missatge PASS; el codi del procés ADB sol no és suficient.
# Al Pixel: iniciar el servidor de la unitat principal d'Android Auto.
./scripts/android-auto-dhu.ps1 -Action Start -Profile Wide
```

Després es poden repetir la projecció i els escenaris de pantalla amb `-Action Simulate`; acabar amb `-Scenario real`. La instal·lació, instrumentació i projecció Wide s'han executat amb la build Kotlin el 4 de novembre de 2026. Els perfils Normal i Narrow no s'han repetit amb aquesta build.

No hi ha prova física nova de l'OBD ni del Peugeot amb la build Kotlin. La migració del llenguatge tampoc demostra que el host del vehicle admeti l'app.
