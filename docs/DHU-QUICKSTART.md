# PTrack amb Desktop Head Unit

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

El script llegeix `sdk.dir` de `local.properties` i permet `-SdkPath` / `-JavaHome`. Detecta un JDK **17** complet; el JDK 25 d'Android Studio actual no és compatible amb Gradle 8.4. Compila fora de OneDrive, a `$env:TEMP/PTrack-auto-minimal-build`, sense canviar els fonts de PTrack.

## Comprovació d'aquest PC — 3 de novembre de 2026

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
