# Validació

## Projecció real DHU superada — 3 de novembre de 2026

Pixel 10 físic autoritzat per ADB, Android 17 i Android Auto `17.9.664044-release`, DHU oficial `2.0-windows` sobre Windows. `Install` retorna **Success** i PackageManager resol `PTrackCarAppService`. Després de netejar una sessió bloquejada d'Android Auto (`IllegalStateException: Already connected`), el DHU ha negociat protocol 1.7 i TLS 1.2 i ha mostrat Android Auto.

**Resultats executats:** icona **PTrack** visible al menú del DHU; obertura des de la icona; títol **PTRACK** i cos **Android Auto connected** visibles; retorn al menú i reobertura correctes. Captures natives del DHU inspeccionades: `artifacts/android-auto-dhu/dhu-menu-page4.png` i `ptrack-minimal-verified.png`. Logs `Android Auto session created` i `Minimal template requested by host` observats. Sessió correcta del script: `dhu-20261103-232458.log`. Guia reproduïble: [DHU-QUICKSTART.md](DHU-QUICKSTART.md).

No s'ha utilitzat cap AVD, Hyper-V, root, publicació Play ni modificació del vehicle. No s'ha provat al Peugeot ni amb captura OBD activa, ni s'han executat les proves instrumentades de regressió en aquesta sessió. La validació funcional és de descoberta i renderització al DHU; no certifica l'acceptació al vehicle. Els estats de bloqueig de les seccions següents són històrics.

## Configuració DHU posterior — 3 de novembre de 2026

DHU oficial 2.0 i ADB 37.0.1 ja instal·lats. Comprovats amb els seus executables. `scripts/android-auto-dhu.ps1 -Action Doctor` i `-Action Build -Offline` executats correctament; APK de la branca actual a `artifacts/android-auto-dhu/PTrack-dhu-debug.apk`. Compilació debug i APK instrumentada completades; lint amb 0 errors i 4 avisos. Selecció automàtica verificada del JDK 17, perquè la instal·lació nova d'Android Studio porta JDK 25 incompatible amb Gradle 8.4.

DHU executat realment amb la configuració guardada `scripts/dhu/ptrack.ini`: la carrega i intenta connectar, però acaba amb **Failed to start Google Automotive Link** a `localhost:5277`. `adb devices -l` no mostra cap dispositiu; no hi ha AVD configurat. `Install` es nega a continuar sense un target autoritzat, comportament comprovat. **No s'ha instal·lat l'APK al Pixel, no hi ha projecció i no s'ha comprovat el menú ni la pantalla mínima.** S'ha demanat connectar el Pixel, autoritzar la depuració USB i iniciar el servidor Android Auto. Instruccions curtes i estat: [DHU-QUICKSTART.md](DHU-QUICKSTART.md). Cap canvi a OBD/Bluetooth/històric/aprenentatge.

## Android Auto mínim — estat actual, 3 de novembre de 2026

Branca `feature/android-auto-minimal`, creada després de revisar `feature/android-auto`. Base de codi `8563b0b`, igual que `origin/main` després de `git fetch origin`. Es conserva la documentació pendent anterior. No s'ha fusionat ni publicat res. Guia i llista de fitxers: [ANDROID-AUTO-MINIMAL.md](ANDROID-AUTO-MINIMAL.md).

- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, `:app:assembleRelease`: **BUILD SUCCESSFUL** amb JDK local, Gradle 8.4, AGP 8.2.2 i SDK 34. La primera execució ha descarregat Car App Library 1.4.0; la verificació final s'ha fet amb `--offline` i `-PptrackBuildRoot=$env:TEMP/PTrack-auto-minimal-build`.
- Lint final: **0 errors, 4 avisos**. Dos de còpies de seguretat ja existents; un de versió més recent de la biblioteca (es fixa 1.4.0 compatible amb les eines actuals), i un de servei exportat (necessari per a CarAppService, amb HostValidator debug). Informe a `artifacts/android-auto-minimal/lint-results-debug.html` i `.txt`.
- Primer lint: tres falsos positius `ForegroundServiceType` al codi existent. El manifest binari de l'APK ja contenia `RecordingService` amb `connectedDevice|location`. Repetir exactament aquest atribut al manifest debug permet al detector d'AGP 8.2 veure'l. No s'ha suprimit el detector ni canviat el codi del registre. Segona execució superada.
- `./scripts/test-core.ps1`: **32 comprovacions del nucli + 39 d'aprenentatge superades**.
- `./scripts/test-android-auto-apk.ps1` amb APK debug i release finals: **PASS**. Inspecció `aapt` dels XML binaris: servei exportat, acció `androidx.car.app.CarAppService`, categoria experimental `IOT`, descriptor `template`, metadata i entrada mòbil. Release no declara el client experimental. És una comprovació estàtica de paquet, no d'un host Android Auto.
- `aapt dump permissions`: llista de permisos idèntica a l'APK anterior. Recursos binaris: contenen exactament `PTRACK` i `Android Auto connected`. `git diff --quiet -- app/src/main app/src/androidTest core-tests`: fonts mòbils, OBD i proves prèvies sense canvis. Dumps del manifest i descriptor guardats a `artifacts/android-auto-minimal/`.
- `apksigner verify --print-certs`: signatura vàlida i mateix certificat que `artifacts/PTrack-0.1.0-debug.apk`. Certificat SHA-256: `d57b78e1d06d62ce309942ec9a4d392530cb006f530ded9d8298295b0dba0731`. No es coneix directament la signatura instal·lada al Pixel.
- APK lliurada: `artifacts/android-auto-minimal/PTrack-android-auto-minimal-debug.apk`, **2.722.906 bytes**. SHA-256: `2c08d1c8fe8c8cee21d39945e69c49ce02079303d9a1b128d6fc4fc18496b719`.
- APK instrumentada compilada: `artifacts/android-auto-minimal/PTrack-android-auto-minimal-tests.apk`. **No executada**: `adb devices -l` no mostra dispositius. Tampoc s'han executat proves instrumentades anteriors, no es dona per comprovada la regressió al mòbil.
- **DHU no executat**: executable absent a l'SDK i cap mòbil/emulador disponible. No s'ha instal·lat un DHU sense dispositiu només per simular una prova. **Peugeot no provat**. No s'afirma descoberta al llançador ni renderització dels textos. La guia descriu com distingir registre del servei, filtre del host i error de plantilla amb logs reals.

Les seccions següents són resultats històrics de les fases anteriors; les referències a una APK sense Android Auto corresponen a aquelles versions.

## Estudi Android Auto — 3 de novembre de 2026

Branca `feature/android-auto`, base `origin/main` a `8563b0b`, que ja inclou `3a94ede` d'aprenentatge del consum. L'estudi i la decisió de no implementar un client sense una categoria compatible acreditada són a [ANDROID-AUTO.md](ANDROID-AUTO.md). Canvis exclusivament de documentació; cap prova nova d'integració Android Auto, perquè aquesta integració no existeix.

Resultats d'aquesta execució:

- `./scripts/test-core.ps1`: **32 comprovacions del nucli + 39 d'aprenentatge superades**, sense errors en la repetició amb accés al SDK.
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: **BUILD SUCCESSFUL**, 61 tasques executades, 50 s. Lint: **0 errors, 2 avisos preexistents** sobre còpies de seguretat.
- `adb devices`: cap dispositiu connectat. `emulator -list-avds`: cap AVD configurat. Proves instrumentades compilades però **no executades**.
- DHU no instal·lat a `SDK/extras/google/auto`; cap prova al DHU ni al Peugeot. La compilació no acredita ni Bluetooth físic ni admissió a Android Auto.
- Diferència de codi respecte d'`origin/main`: cap. Es conserven servei de registre, historial, migració SQLite i aprenentatge del consum.

Ordres de la compilació verificada (PowerShell, JDK 21.0.10 i SDK 34 locals):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'C:/Users/mad93/.gradle'
./scripts/test-core.ps1
./gradlew.bat --offline --no-daemon "-PptrackBuildRoot=$env:TEMP/PTrack-android-auto-build" :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

S'ha requerit execució fora del sandbox per accedir a les eines locals. El primer intent de proves dins del sandbox ha mostrat `AccessDeniedException` de `javac` sobre `android.jar`, encara que imprimís els PASS; no es considera una compilació neta. El primer intent Gradle buscava el lock a `C:/.gradle` i ha fallat. La repetició amb `GRADLE_USER_HOME` explícit i accés a les eines ha resolt tots dos problemes sense canviar el codi.

Resultats copiats a `artifacts/android-auto-review/` (directori ignorat per Git): `PTrack-baseline-debug.apk`, `PTrack-baseline-debug-androidTest.apk`, `lint-results-debug.txt` i `lint-results-debug.html`. Són la base existent recompilada, **sense Android Auto**. La configuració Android Auto no és aplicable; el procediment de comprovació del registre amb Android Auto connectat es descriu a l'estudi i encara no s'ha executat.

## Aprenentatge del consum — 3 de novembre de 2026

Branca `feature/consumption-learning`. Resultats de la versió final:

- `./scripts/test-core.ps1`: **32 comprovacions existents + 39 noves superades**. Les noves cobreixen consum constant, ponderació per distància, regeneració, conservació d'energia i distància en creuar intervals, configuració de velocitats, confiança, mínims de suport, dades incompletes/impossibles, GPS nul/congelat/salts, buits, duplicats, retrocessos temporals, salts de SOC, càrrega estacionària, fragments curts i pendent conegut/ambigu.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`: **BUILD SUCCESSFUL**. Lint: **0 errors i 2 avisos preexistents** sobre còpies de seguretat.
- Compilació amb JDK local i `--offline -PptrackBuildRoot=$env:TEMP/PTrack-consumption-build`. El primer intent al directori de build de OneDrive ha fallat per `AccessDeniedException`; la destinació temporal ha resolt el bloqueig sense canviar el projecte.
- `adb devices`: cap dispositiu/emulador connectat. Les proves instrumentades s'han compilat però **no s'han executat**. Ara comproven també migració v1→v2 conservant originals i NULL, selecció de viatges reals/tancats, persistència del perfil, recàlcul idempotent, nous trajectes, canvi d'amplada i consulta sense dades. Cal executar-les en Android abans de donar per validada la migració en un dispositiu.
- No s'ha validat el model amb un vehicle real. No s'han utilitzat dades sintètiques per omplir el model de producció.

Arquitectura, fitxers, API, filtres i limitacions: [CONSUMPTION-LEARNING.md](CONSUMPTION-LEARNING.md).

## Validació inicial del projecte

Resultat local del 3 de novembre de 2026: `assembleDebug`, `assembleDebugAndroidTest` i `lintDebug` completats. Nucli: **32 comprovacions superades**. Lint: **0 errors i 2 avisos** referits als atributs de còpia de seguretat per a versions antigues d’Android. Les APK de l’app i de proves són a `artifacts/`; el resum Lint s’ha copiat a la mateixa carpeta.

OneDrive ha bloquejat l’eliminació d’un directori temporal durant una compilació incremental. La compilació verificada ha utilitzat `-PptrackBuildRoot=<directori absolut fora de OneDrive>`. Aquesta propietat opcional canvia només la destinació dels fitxers de compilació; el projecte i els fonts es mantenen al workspace.

## Comprovacions automatitzades locals

Executa `./gradlew.bat :app:assembleDebug :app:lintDebug` i `./scripts/test-core.ps1`. Les proves cobreixen consum d’una hora a potència constant, creuament de signe, separació d’energia regenerada, distància, mitjana, buits, duplicats temporals, filtre d’altitud, fórmules reals SOC/corrent/velocitat/temperatura/cel·les, valors fora de rang i trames incompletes.

## Prova instrumentada en un Android o emulador

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w cat.ptrack.test/cat.ptrack.SmokeInstrumentation
```

El runner comprova persistència SQLite després de reobrir la base de dades, recuperació d’un viatge interromput, generació de PDF a partir de mostres i arrencada de la pantalla del mòbil. Utilitza una base de dades de prova i una carpeta d’informes separades. No es connecta a Bluetooth ni acredita la integració amb el vehicle.

## Prova manual necessària

1. Sense adaptador: missatge d’OBD no trobat i cap mostra inventada.
2. ELM327 Bluetooth clàssic i e-2008 de 2020: SOC coherent amb el quadre, tensió i velocitat plausibles, corrent positiu amb consum i negatiu en regeneració.
3. Pantalla apagada: es continuen desant mostres; el registre no depèn de mantenir l’activitat oberta.
4. Tall curt de Bluetooth: mateix viatge i cap integració fictícia del buit. Tall de més de 45 s: tancament, PDF i següent viatge separat.
5. Després de l’associació Companion: detecció de presència amb l’app tancada. Comprovar-ho en la marca i versió Android reals.
6. GPS denegat o sense precisió vertical: desnivell no disponible. GPS vàlid: estimació de pujada/baixada coherent amb la ruta, sense confondre-la amb una altitud de precisió.
7. Obrir un viatge, exportar PDF i JSON, inspeccionar dues pàgines en català, castellà i anglès. Revisar gràfiques en viatges curts i llargs, valors negatius, buits i dades absent.
8. Aturar el procés: recuperar les mostres ja desades, marcar viatge interromput i regenerar PDF des de l’historial.

No hi havia cap mòbil ni emulador configurat disponible durant la implementació inicial. No s’han executat les proves instrumentades ni s’ha inspeccionat visualment un PDF produït per Android. No s’ha validat Android Auto: no està implementat en aquesta versió.
