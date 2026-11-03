# Android Auto — prova mínima

Data: **3 de novembre de 2026**. Branca: `feature/android-auto-minimal`.

## Lliurable i resultat

APK: `artifacts/android-auto-minimal/PTrack-android-auto-minimal-debug.apk`.
És PTrack complet amb un client **real de Car App Library**, només a la variant debug:

`Android Auto host → PTrackCarAppService → Session → PTrackCarScreen → MessageTemplate`

Títol: **PTRACK**. Cos: **Android Auto connected**. El host determina la tipografia i posició, i afegeix els seus controls. El text és fix i només es demana en obrir la pantalla de cotxe; no indica connexió OBD ni una connexió permanent monitorada.

**Descoberta i visualització verificades al DHU oficial 2.0**, amb el Pixel 10 real (Android 17, Android Auto `17.9.664044-release`). L'APK s'ha actualitzat amb ADB, s'ha vist PTrack al menú i s'ha obert/reobert des de la seva icona amb els dos textos exactes. Captura: `artifacts/android-auto-dhu/ptrack-minimal-verified.png`; menú: `dhu-menu-page4.png`. **No s'ha provat al Peugeot.** A la primera implementació només hi havia validació estàtica; el resultat funcional posterior es documenta a [DHU-QUICKSTART.md](DHU-QUICKSTART.md).

La branca anterior `feature/android-auto` s'ha revisat abans de crear aquesta. Apuntava a `8563b0b`, igual que `origin/main`, amb documentació local pendent i cap implementació de cotxe. S'han conservat aquests documents; l'estudi anterior és històric. No s'ha fusionat a `main`.

## Què permet i què no garanteix aquesta via

| Capa | Restricció concreta |
| --- | --- |
| Google Play | Revisió, elegibilitat de categoria i distribució. No cal publicació pública per desenvolupar o usar el DHU. La guia també ofereix Internal App Sharing / Internal Test Track per provar al vehicle sense revisió pública. No s'ha utilitzat ni es requereix com a pas del lliurament. |
| API Car App Library | El servei ha de declarar una categoria reconeguda. La pantalla usa plantilles, no una Activity Android arbitrària. El mode desenvolupador no converteix qualsevol categoria en vàlida. |
| Android del Pixel | L'APK es pot instal·lar amb ADB o autoritzant el gestor de fitxers. La signatura ha de coincidir per actualitzar conservant dades. Poder instal·lar o resoldre un servei no obliga Android Auto a mostrar-lo. |
| Host / projecció Android Auto | Descoberta, origen de confiança i acceptació de plantilles els decideix Android Auto. **Segons Google, «Unknown sources» no s'aplica a les apps Car App Library.** No hi ha una declaració de manifest que substitueixi aquest control. El Peugeot rep la projecció del mòbil; no instal·la aquesta APK ni executa Android Automotive OS. |

Fonts: [proves i origen de confiança](https://developer.android.com/training/cars/testing), [configuració i categories](https://developer.android.com/training/cars/apps/library/set-up-project), [descriptor de projecció](https://developer.android.com/training/cars/apps/auto).

### Categoria experimental declarada

El manifest **debug** declara `androidx.car.app.category.IOT` perquè el host necessita una categoria existent per descobrir el servei de plantilles. En aquesta fase PTrack només és una prova de transport, **no implementa funcionalitat IoT i no acredita elegibilitat IoT**. La guia oficial descriu accions sobre dispositius connectats; aquest missatge fix no les implementa. És una declaració experimental, no una homologació del producte. El host pot filtrar o rebutjar l'app i no es pot extrapolar el resultat a un futur panell OBD. No s'ha declarat navegació, reproducció d'àudio, missatgeria ni un joc fictici.

S'utilitza `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` exclusivament en fonts debug, per acceptar hosts de desenvolupament. Això valida qui entra al servei; **no** elimina la comprovació d'origen de l'app que fa Android Auto. No s'exposen dades ni ordres OBD des d'aquest servei. La variant release no incorpora ni el servei, ni el descriptor, ni la biblioteca.

### Alternatives personals examinades

- **DHU oficial:** primera via per provar aquesta pantalla real sense root ni canvis al Peugeot. Necessita el Pixel connectat i el servidor de head unit actiu. Passar el DHU no prova el control de confiança d'una connexió normal al Peugeot.
- **Distribució privada de confiança:** Google documenta Internal App Sharing / Internal Test Track per a proves al vehicle. No és una publicació pública, però implica Play Console, pujar un artefacte i gestionar signatura/target SDK; queda fora del flux d'APK manual demanat. No s'ha pujat res. No es promet que aquesta prova IoT sigui elegible per distribuir-se.
- **AAAD / instal·ladors alternatius / KingInstaller:** són mecanismes d'instal·lació, no una API per projectar qualsevol Activity. El [README del mateix AAAD](https://github.com/shmykelsa/AAAD) declara incerta la compatibilitat d'Android 14+ i no garanteix el funcionament de les apps. No hi ha prova amb el Pixel 10 d'aquest usuari. Canviar el nom de l'instal·lador amb ADB tampoc acredita una instal·lació de confiança; no es presenta com a solució verificada.
- **Mirroring no oficial:** [AA Stream](https://github.com/endyrubbin/AAStream) requereix root i prefereix una versió antiga d'Android Auto; no satisfà les condicions. No s'ha trobat en les fonts consultades una garantia verificable de mirroring lliure, sense root, amb el Pixel 10 i el Peugeot disponibles. Això no és una prova d'impossibilitat universal.
- **Dongle amb mode desenvolupador:** AAAD i [AA Torque](https://github.com/agronick/aa-torque) esmenten AAWireless com a possible alternativa per exposar apps. És maquinari addicional i una ruta dependent del firmware i del host; no s'ha provat, no s'ha comprat ni s'assumeix disponible. No és equivalent a activar el mode desenvolupador del mòbil.
- **Activity estacionada / multimèdia:** són experiències limitades a tipus concrets; no ofereixen una API genèrica per a qualsevol pantalla PTrack. No s'ha disfressat aquesta prova com a contingut multimèdia o joc.

Conclusió tècnica: la UI mínima es pot expressar amb una plantilla pública i s'ha implementat. El punt pendent per a l'APK manual al Peugeot és **l'acceptació i descoberta del servei pel host**, especialment l'origen de confiança; no és Bluetooth ni la impossibilitat de dibuixar aquests dos textos. No s'afirma que el conjunt de condicions de l'usuari sigui impossible ni que ja estigui resolt al vehicle.

## Instal·lació al Pixel 10

1. Conserva la instal·lació i dades actuals. No desinstal·lis PTrack ni esborris les seves dades. L'APK manté `applicationId=cat.ptrack` i `versionCode=1`.
2. Activa opcions de desenvolupador d'Android: Ajustos → Quant al telèfon → prem set vegades Número de compilació; després Sistema → Opcions de desenvolupador → Depuració USB. Connecta'l al PC i accepta la clau RSA.
3. Amb `adb` del SDK al PATH, des de l'arrel del repositori:

```powershell
adb devices -l
adb install -r artifacts/android-auto-minimal/PTrack-android-auto-minimal-debug.apk
```

Alternativament, copia l'APK al Pixel, obre'l amb el gestor de fitxers i autoritza aquest gestor a instal·lar apps desconegudes. Aquest permís d'Android és diferent de l'opció d'Android Auto.

La signatura de l'APK lliurada coincideix amb `artifacts/PTrack-0.1.0-debug.apk` conservada localment (comprovació amb `apksigner`). Si apareix `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, atura't: la instal·lació del Pixel té una altra signatura. No ho resolguis desinstal·lant. Cal recompilar amb la clau original; l'exportació JSON actual no té importador.

4. Obre PTrack al mòbil i comprova que la pantalla habitual funciona. Per a la primera prova de projecció no cal connectar l'OBD ni concedir permisos nous a aquest client.

## Opcions d'Android Auto

1. Actualitza Android Auto i completa la configuració inicial al Pixel. Cerca Android Auto als Ajustos; habitualment també és a Apps → Android Auto → Ajustos addicionals de l'app.
2. A la part inferior, prem Versió per desplegar la informació i després prem deu vegades la informació de versió/permisos. Accepta el mode desenvolupador.
3. Menú ⋮ → Configuració de desenvolupador. Si hi ha «Application mode», selecciona «Developer». Activa «Unknown sources» per a la prova; **no suprimeix el control de confiança de Car App Library**.
4. A Personalitza el llançador, cerca PTrack i marca'l si hi figura. No trobar-lo aquí és un resultat de descoberta, no una prova que `MainActivity` o OBD fallin.

No cal iniciar el servidor de head unit per connectar al Peugeot; només s'utilitza amb DHU. [Procediment oficial de desenvolupador](https://developer.android.com/training/cars/testing).

## Prova amb Desktop Head Unit

Instal·la des d'Android Studio → SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator. A Android Auto del Pixel, menú ⋮ → Start head unit server. Mantén el telèfon desbloquejat, connectat per USB i amb «Add new cars to Android Auto» habilitat.

```powershell
adb forward tcp:5277 tcp:5277
& "$env:ANDROID_HOME/extras/google/auto/desktop-head-unit.exe"
```

Si no tens `ANDROID_HOME`, substitueix-lo pel directori del SDK. Accepta al Pixel els diàlegs inicials. Obre el llançador del DHU → PTrack. Comprova títol i missatge, surt i torna-hi a entrar, i comprova que el mòbil continua obrint PTrack normalment. Desa captura i versions del Pixel/Android Auto/DHU. En acabar: `adb forward --remove tcp:5277` i atura el servidor al Pixel.

[Instruccions oficials del DHU](https://developer.android.com/training/cars/testing/dhu). La prova del menú, obertura i reobertura de PTrack s'ha executat amb èxit. No s'han provat encara tots els escenaris de reconnexió o regressió OBD; el Peugeot continua pendent.

## Prova al Peugeot

1. Amb el vehicle estacionat, connecta el Pixel amb el cable USB habitual que ja funciona amb Android Auto. Comprova primer que Android Auto ordinari s'obre.
2. Busca PTrack al menú d'apps del Peugeot. Si hi és, obre'l i comprova literalment **PTRACK** i **Android Auto connected**. Fotografia el resultat i anota versions d'Android, Android Auto i sistema del Peugeot.
3. Torna al menú, reobre PTrack, desconnecta/reconnecta Android Auto i repeteix. La pantalla no ha d'iniciar cap captura OBD per si mateixa.
4. Obre PTrack al mòbil i comprova les pantalles existents. En una prova separada amb el registre habitual ja actiu, comprova que obrir/tancar el client de cotxe no l'interromp. Aquesta regressió al dispositiu encara no s'ha executat.

### Si no apareix o no s'obre

Amb el Pixel connectat al PC per ADB (després de reproduir el problema al cotxe, o amb depuració sense fil si ja la tens configurada):

```powershell
adb shell pm path cat.ptrack
adb shell dumpsys package cat.ptrack > artifacts/android-auto-minimal/pixel-package.txt
adb shell cmd package query-services --components -a androidx.car.app.CarAppService -p cat.ptrack
adb logcat -d -v threadtime > artifacts/android-auto-minimal/pixel-logcat.txt
adb logcat -d -s PTrackCar:I '*:S'
```

Els logs poden contenir dades privades; revisa'ls abans de compartir-los. No s'executa `logcat -c`, no s'esborren dades i no es força l'aturada del registre.

- Servei absent de `query-services`: comprova que has instal·lat **l'APK debug mínima**, no l'APK anterior ni release.
- Servei registrat però cap entrada al menú: inspecciona Personalitza el llançador i els logs complets d'Android Auto. El control de confiança/categoria és una hipòtesi documentada, no un diagnòstic provat sense logs del Pixel.
- `Android Auto session created` però cap plantilla: s'ha arribat al servei; busca errors de negociació/API o excepcions.
- `Minimal template requested by host`: el host ha demanat la plantilla; això sol **no** demostra que s'hagi dibuixat. Cal veure els textos al DHU o al Peugeot.

No confonguis la icona del mòbil amb la del cotxe, ni instal·lació ADB correcta amb acceptació del host.

## Fitxers i compilació

| Fitxer | Canvi |
| --- | --- |
| `app/build.gradle` | `debugImplementation androidx.car.app:app:1.4.0`, estable compatible amb SDK 34 / AGP 8.2.2. No s'utilitza cap API nova que exigeixi actualitzar tota l'eina. |
| `gradle.properties` | Activa AndroidX, necessari per a la biblioteca. |
| `app/src/debug/AndroidManifest.xml` | Servei exportat, categoria experimental IoT, metadata Android Auto i Car API mínim 1. Repeteix el tipus FGS existent per evitar un fals positiu de lint amb el manifest superposat. |
| `app/src/debug/res/xml/automotive_app_desc.xml` | Capacitat `template`. |
| `app/src/debug/res/values/car_strings.xml` | Els dos textos exactes, no traduïbles. |
| `app/src/debug/java/cat/ptrack/car/PTrackCarAppService.java` | Validador debug, sessió i logs de diagnòstic. |
| `app/src/debug/java/cat/ptrack/car/PTrackCarScreen.java` | Plantilla estàtica. Sense timers, subscripcions, Bluetooth o SQLite. |
| `scripts/test-android-auto-apk.ps1` | Inspecció reproduïble de la configuració binària debug/release. No substitueix DHU. |
| Documents | README, VALIDATION, estudi anterior marcat històric i aquesta guia. |

No s'han canviat `MainActivity`, `RecordingService.java`, Bluetooth, SQLite, aprenentatge ni pantalles existents. La declaració FGS repetida té exactament el valor que ja existia al manifest principal. No s'han afegit permisos de cotxe, ubicació, xarxa o Bluetooth.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'C:/Users/mad93/.gradle'
./gradlew.bat --no-daemon "-PptrackBuildRoot=$env:TEMP/PTrack-auto-minimal-build" :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease
./scripts/test-core.ps1
./scripts/test-android-auto-apk.ps1 -DebugApk "$env:TEMP/PTrack-auto-minimal-build/app/outputs/apk/debug/app-debug.apk" -ReleaseApk "$env:TEMP/PTrack-auto-minimal-build/app/outputs/apk/release/app-release-unsigned.apk"
```

Després de la primera descàrrega de dependències es pot afegir `--offline`. Resultats d'execució: [VALIDATION.md](VALIDATION.md). L'APK release unsigned només es compila per comprovar l'aïllament; no és el lliurable per al Pixel.
