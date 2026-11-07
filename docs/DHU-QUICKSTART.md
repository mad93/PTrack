# PTrack amb Desktop Head Unit

Per a la prova física del cotxe, segueix [Android Auto al vehicle](ANDROID-AUTO-VEHICLE.md). `Install` i `Run` d'aquest script instal·len amb ADB per desenvolupar al DHU; no conserven necessàriament l'origen configurat amb KingInstaller o Play.

**Fase actual (4/11/2026):** [quadre gràfic i build visible](ANDROID-AUTO-GRAPHICS.md). `Start` admet `-Profile Normal` (800×480), `Wide` (1280×720) i `Narrow` (400×720 lògics dins 1280×720 amb marge). Tanca el DHU abans de canviar de perfil. `Simulate` també admet `standstill` i `finish`; torna sempre a `-Scenario real`. El peu de l'app del mòbil identifica l'APK instal·lada. **Al Peugeot l'usuari no veu PTrack al menú: encara no funciona al vehicle.** Els resultats de la pantalla mínima que segueixen són històrics.

**Versió actual: quadre de telemetria.** La pantalla mínima s'ha ampliat amb dades de `RecordingService`, tres pàgines i una font de prova separada. [Implementació, resultats funcionals i prova al Peugeot](ANDROID-AUTO-DASHBOARD.md). Per injectar dades de pantalla: `./scripts/android-auto-dhu.ps1 -Action Simulate -Scenario drive`; en acabar: `-Scenario real`. La capçalera mostra SIMULACIÓ a totes les pàgines mentre la prova està activa.

**Prova funcional superada el 3 de novembre de 2026:** PTrack apareix al menú del DHU oficial, s'obre des de la icona i mostra **PTRACK / Android Auto connected**. Sortida al menú i reobertura també comprovades. S'ha utilitzat el Pixel 10 físic amb Android 17 i Android Auto `17.9.664044-release`; no cal Hyper-V ni un AVD per aquest flux. **El Peugeot encara no està provat.**

Des de PowerShell, a l'arrel del repositori:

```powershell
./scripts/android-auto-dhu.ps1 -Action Doctor
./scripts/android-auto-dhu.ps1 -Action Run -Offline
```

`Run` selecciona un únic Android autoritzat, compila amb JDK 17, instal·la l'APK debug amb `adb install -r`, comprova el servei i inicia el **DHU oficial** amb un túnel ADB. No desinstal·la PTrack ni esborra dades. Si hi ha diversos dispositius, afegeix `-Serial <serial>`. La primera descàrrega de dependències necessita executar sense `-Offline`.

Abans de `Run`, al Pixel 10:

1. Connecta un cable USB de dades, desbloqueja'l i activa Depuració USB a les opcions de desenvolupador d'Android. Accepta **al mòbil** l'autorització RSA del PC. `Doctor` l'ha de mostrar amb estat `device`, no `unauthorized`.
2. A Android Auto, prem deu vegades la informació de versió per activar-ne el mode desenvolupador. Menú ⋮ → **Start head unit server / Inicia el servidor de la unitat principal**.
3. Mantén el mòbil desbloquejat i accepta tu els permisos i diàlegs inicials d'Android Auto. Habilita afegir cotxes nous si estava desactivat. «Unknown sources» no garanteix l'acceptació d'apps Car App Library.

Al DHU, obre el menú d'apps → **PTrack**. El resultat esperat és títol **PTRACK** i cos **Android Auto connected**. Una compilació correcta, el registre del servei o un missatge de log no substitueixen veure aquesta pantalla.

Ordres separades per reutilitzar una compilació:

```powershell
./scripts/android-auto-dhu.ps1 -Action Build -Offline
./scripts/android-auto-dhu.ps1 -Action Install
./scripts/android-auto-dhu.ps1 -Action Start
./scripts/android-auto-dhu.ps1 -Action Logs
./scripts/android-auto-dhu.ps1 -Action Stop
```

`Start` queda en execució mentre la finestra del DHU és oberta. Tanca-la per tornar a PowerShell. `Stop` només retira el túnel propi; tanca el DHU i atura el servidor des del Pixel. No atura `RecordingService`.

APK i diagnòstic: `artifacts/android-auto-dhu/` (ignorat per Git). `Logs` guarda PackageManager i Logcat localment, sense buidar-los. Revisa dades privades abans de compartir-los. L'acció `Start` guarda el log del DHU i detecta el seu error de connexió encara que l'executable retorni codi zero. No sobreescriu túnels d'altres dispositius; si el port està ocupat, usa `-Port 5278`.

El script llegeix `sdk.dir` de `local.properties` i permet `-SdkPath` / `-JavaHome`. Detecta un JDK **17** complet per a Kotlin 2.3.21 / AGP 8.13.2 / Gradle 8.13. Compila fora de OneDrive, a `$env:TEMP/PTrack-auto-minimal-build`, sense canviar els fonts de PTrack. La primera compilació després de migrar a Kotlin s'ha de fer sense `-Offline` per completar les dependències; després es pot reutilitzar la memòria cau.

## Prova funcional amb el Pixel — 3 de novembre de 2026

- `Install`: **Success**. PackageManager resol `cat.ptrack/.car.PTrackCarAppService`. Actualització amb `-r`, sense desinstal·lar ni esborrar dades.
- `Start`: túnel ADB `tcp:5277 → tcp:5277`, protocol Android Auto **1.7**, TLS **1.2**, verificació del certificat correcta.
- Primera connexió bloquejada: el servidor escoltava però no processava les connexions; després ha aparegut un crash del procés Android Auto amb `java.lang.IllegalStateException: Already connected`, a `DeveloperHeadUnitNetworkService`. S'ha tancat el DHU, aturat només Android Auto amb `am force-stop` i l'usuari n'ha reobert els ajustos i iniciat el servidor. La connexió posterior ha projectat Android Auto correctament. No s'han esborrat dades.
- El validador havia mostrat `Package DENIED; failed all other checks [cat.ptrack]` durant la sessió fallida. Això **no ha impedit** la prova DHU posterior: el host ha creat la sessió i ha renderitzat PTrack. No s'utilitza aquell avís transitori com a prova d'un bloqueig definitiu d'origen.
- Captures obtingudes amb l'ordre nativa `screenshot` del DHU i inspeccionades: `artifacts/android-auto-dhu/dhu-menu-page4.png` (icona **PTrack**) i `ptrack-minimal-verified.png` (pantalla exacta). La captura Windows de Computer Use havia fallat per timeout; l'ordre nativa del DHU ha permès verificar la projecció directament.
- Navegació executada amb ordres natives `keycode home` i `tap`: tornar al menú, localitzar PTrack, prémer-ne la icona i tornar a veure el missatge. Log `PTrackCar: Minimal template requested by host` guardat a `ptrack-session.txt`.
- Sessió correcta amb el script: `artifacts/android-auto-dhu/dhu-20261103-232458.log`. S'ha deixat el DHU obert mostrant PTrack. Cap prova al vehicle ni prova de regressió amb l'OBD actiu; cap indicador OBD implementat.
- El script ara detecta també `Failed to read from transport`, encara que DHU retorni codi zero. Per automatització, l'execució necessita una consola interactiva/PTY: stdin tancat fa acabar el DHU immediatament. Des d'una PowerShell normal el script manté aquesta consola.

## Comprovació inicial del PC — abans de connectar el Pixel

- SDK existent: `C:/Users/mad93/AppData/Local/Android/Sdk`.
- ADB existent: `37.0.1-15733141`, protocol `1.0.41`.
- DHU existent: paquet `extras;google;auto`, revisió **2.0**, executable **2.0-windows**. No s'ha reinstal·lat.
- Emulador Android instal·lat, **cap AVD configurat**. Un AVD ordinari no substitueix el DHU: el DHU emula la unitat del cotxe i necessita Android Auto executant-se en un Android. Per aquesta prova s'utilitza el Pixel de l'usuari; no s'ha creat cap AVD ni iniciat sessió a Google.
- JDK funcional local: `C:/Users/mad93/.jdks/jbr-17.0.14`. La ruta antiga `Android Studio/jbr` és incompleta i la instal·lació actual `Android Studio1/jbr` porta JDK 25.
- Compilació actual `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: **BUILD SUCCESSFUL**. No s'han executat proves instrumentades al mòbil.
- Script executat realment amb `Doctor` i `Build -Offline`: **correcte**, incloent detecció automàtica de JDK 17 i còpia d'APK. Lint: **0 errors, 4 avisos**. `Install` sense dispositiu s'ha comprovat: s'atura abans d'instal·lar res amb `No authorized Android device`.
- Arrencada real del DHU intentada: **Failed to start Google Automotive Link**, connexió a `localhost:5277` fallida. No és un resultat sobre PTrack: no hi havia cap dispositiu ADB connectat ni servidor accessible.
- Configuració pròpia `scripts/dhu/ptrack.ini` carregada pel DHU sense errors (800×480, entrada tàctil). Log de l'intent a `artifacts/android-auto-dhu/dhu-no-device.log`. ADB i la consulta de dispositius de Windows no han mostrat cap Pixel/Android disponible.
- **Descoberta de PTrack i pantalla mínima: encara no comprovades.** Cal connectar/autoritzar el Pixel i iniciar el servidor per continuar. No s'ha modificat OBD, Bluetooth, històric ni aprenentatge.

Procediment de connexió contrastat amb la [guia oficial del DHU](https://developer.android.com/training/cars/testing/dhu). Detalls de la integració i categoria experimental: [ANDROID-AUTO-MINIMAL.md](ANDROID-AUTO-MINIMAL.md).
