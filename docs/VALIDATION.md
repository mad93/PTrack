# Validació

## PTrack 0.2.4 al Pixel — 8/11/2026

- S’han executat les 28 proves unitàries i s’han compilat les proves instrumentades i l’APK de depuració des del commit `26d7084b8ee5` de `main`. L’APK `artifacts/ptrack-pixel-024-20261108/PTrack-0.2.4.apk` conserva el certificat de la 0.2.3. SHA-256 verificat al PC, a Baixades i al paquet instal·lat `9DDC1DE2DD1305FD757A387781BCCA15215549620AC8FE0FEDD8C98A3C69E9D0`.
- PTrack habitual (`cat.ptrack`) actualitzada a la **0.2.4 (6)** a les 10:42 CET amb KingInstaller 2.5 i Shizuku 13.6.0. Play Protect ha completat l’anàlisi autoritzada. Instal·lador `com.android.vending`, iniciador `com.google.android.packageinstaller` i servei d’Android Auto resoluble. Shizuku aturat. Informe `artifacts/android-auto-car-check/20261108-104253-128/`.
- Còpia prèvia exportada i verificada a **Baixades** del Pixel (`PTrack-20261108-103652.ptrack.json.gz`). En obrir la versió nova, s’han verificat el perfil actiu e-2008, el darrer SOH, la MAC vinculada i els tres viatges previs al calendari del perfil. Queda pendent la prova amb OBD reals i Android Auto al Peugeot.

## Perfils de vehicle — 8/11/2026

- Han passat les proves unitàries, inclosos els casos de MAC única, compartida amb el perfil actiu i ambigua. S’han compilat l’APK de depuració i les proves instrumentades.
- La comprovació local amb SQLite ha executat la migració v5 amb dades de prova. Ha conservat els identificadors de viatges i càrrega DC, ha recuperat el darrer SOH real i ha separat les consultes d’historial.
- L’emulador de prova ha quedat fora de línia a ADB. Queda pendent executar les proves instrumentades en Android i validar la connexió amb adaptadors OBD reals i Android Auto al Peugeot. [Procediment de prova](ANDROID-AUTO-VEHICLE.md#prova-de-linici-autom%C3%A0tic-obd).

## Perfil DC i previsió a les parades — 8/11/2026

- Han passat `testDebugUnitTest` i `compileDebugAndroidTestKotlin`. Les proves unitàries cobreixen la discriminació AC/DC amb lectura de cable i tensió, les temperatures comparables entre sessions, el límit de potència del punt, la corba de referència i el càlcul de l'hora de les parades següents.
- Una consulta HTTPS real d'Open-Meteo per a dos punts públics ha retornat dues sèries de 48 temperatures horàries amb marques de temps UNIX. Queda pendent la consulta des del Pixel i la captura d'una càrrega DC real al Peugeot, seguida d'una càrrega AC per contrastar la classificació.

## Inici automàtic OBD al codi — 8/11/2026

- `testDebugUnitTest` ha passat, inclosos els tres casos nous de durada de la sessió OBD. `compileDebugAndroidTestKotlin` ha compilat les proves instrumentades.
- ADB no ha detectat cap dispositiu. Queda pendent verificar el disparador de l’ELM327 o del Peugeot amb el Pixel 10 bloquejat, la reconnexió i l'aturada sense crear viatges buits. [Procediment i diagnòstic](ANDROID-AUTO-VEHICLE.md#prova-de-linici-autom%C3%A0tic-obd).

## PTrack 0.2.3 al Pixel — 8/11/2026

- Correcció del toc dels suggeriments de cerca. `testDebugUnitTest`, `compileDebugAndroidTestKotlin` i `assembleDebug` han passat. APK `artifacts/ptrack-pixel-023-20261108/PTrack-0.2.3.apk`, paquet `cat.ptrack`, versió 0.2.3 (5), amb certificat coincident amb la 0.2.2. SHA-256 verificat al PC, a Baixades i al paquet instal·lat `5DD04E93FFD186F32B046F8AF1C949BDA618362AB4A328E1828FF1BFD75AEB64`.
- Actualització a les 02:34 CET amb KingInstaller 2.5 i Shizuku 13.6.0, després de l'anàlisi autoritzada de Play Protect. Instal·lador `com.android.vending`, iniciador `com.google.android.packageinstaller` i servei d'Android Auto resoluble. Shizuku aturat. Informe `artifacts/android-auto-car-check/20261108-023449-784/`.
- Còpia prèvia a `%LOCALAPPDATA%\PTrack\Backups\ptrack-before-0.2.3-20261108.tar`. SQLite manté la integritat i conserva els mateixos identificadors de 3 viatges, 3.889 mostres i 4.286 lectures. El mapa nacional continua actiu. La prova al Peugeot queda pendent.
- Al Pixel s'ha seleccionat Rupit amb Photon i el Colomer de Taradell amb l'ICGC. Els tres controls lliscants són visibles. El càlcul ha retornat dues propostes, els botons dels navegadors i una capacitat de referència de 46 kWh. La proposta seleccionada mostrava 51 km i 152 min, una durada pendent de contrastar. La ruta s'ha deixat com a esborrany.

## PTrack 0.2.2 al Pixel — 8/11/2026

- `testDebugUnitTest`, `compileDebugAndroidTestKotlin` i `assembleDebug` han passat. APK `artifacts/ptrack-pixel-022-20261108/PTrack-0.2.2.apk`, paquet `cat.ptrack`, versió 0.2.2 (4), amb el mateix certificat que la 0.2.1. SHA-256 local, copiat i instal·lat `987AE3A809A5AED8F8A6A0163AC3FA8CAA2A3CAECBA6B874B4320F0FA4000F90`.
- Actualització a les 02:25 CET amb KingInstaller 2.5 i Shizuku 13.6.0. Play Protect ha completat l'anàlisi autoritzada. Android ha confirmat l'actualització i ha registrat `com.android.vending` com a instal·lador i `com.google.android.packageinstaller` com a iniciador. El servei d'Android Auto és resoluble i Shizuku s'ha aturat. Informe `artifacts/android-auto-car-check/20261108-022609-587/`. La prova al Peugeot queda pendent.
- Còpia prèvia validada a `%LOCALAPPDATA%\PTrack\Backups\ptrack-before-0.2.2-20261108.tar`. SQLite ha passat la comprovació d'integritat i es conserven els mateixos identificadors de 3 viatges, 3.889 mostres i 4.286 lectures. El mapa nacional de Valhalla continua actiu.
- Al Pixel, Photon ha mostrat suggeriments per a Rupit, però tocar un resultat no el seleccionava. La llista d'Android reutilitzava el gestor de tocs de l'estat de càrrega.

## PTrack 0.2.1 amb el planificador visual al Pixel — 8/11/2026

- `testDebugUnitTest` i `assembleDebug` han passat. APK `artifacts/ptrack-visual-pixel-20261108/PTrack-0.2.1-Planificador.apk`, paquet `cat.ptrack`, versió 0.2.1 (3), amb el mateix certificat que la 0.2.0. SHA-256 verificat abans de copiar i després d'instal·lar `FA8CF3C41F1628C4B164BDB00493071B04A03A36F1E352ED6B38EBD1102299B0`.
- Actualització a les 01:45 CET amb KingInstaller 2.5 i Shizuku 13.6.0. Play Protect ha completat l'anàlisi autoritzada per l'usuari i Android ha confirmat l'actualització. L'instal·lador és `com.android.vending`, l'iniciador és `com.google.android.packageinstaller` i el servei de PTrack és resoluble. Shizuku s'ha aturat. Informe `artifacts/android-auto-car-check/20261108-014542-092/`.
- Còpia prèvia validada a `%LOCALAPPDATA%\PTrack\Backups\ptrack-before-visual-planner-20261108.tar`. SQLite ha superat la comprovació d'integritat i conserva els identificadors dels 3 viatges, les 3.889 mostres i les 4.286 lectures. El mapa nacional de Valhalla continua actiu. La prova al Peugeot queda pendent.
- Obert el planificador al Pixel amb el 80% per defecte. Una ruta directa de prova a Barcelona ha mostrat el traçat al mapa, 5 km, 7 minuts i un 77,4% previst a l'arribada. S'han verificat els botons **Google Maps** i **Waze** a la pantalla del resultat. L'esborrany s'ha mantingut sense activar per conservar l'itinerari existent. Queda pendent provar l'obertura dels navegadors i una ruta amb recàrregues.

## Planificador visual al codi — 8/11/2026

- `testDebugUnitTest`, `compileDebugAndroidTestKotlin` i `assembleDebug` han passat. La prova del motor confirma que es conserva la geometria dels trams seleccionats i que la reserva reduïda només s'aplica a l'arribada indicada.
- Valhalla 3.9.1 ha retornat tres corredors amb geometria i elevació per a Barcelona Sants–Madrid Atocha al graf nacional, de 619,34, 691,84 i 670,22 km. És una comprovació del càlcul viari a l'ordinador. PTrack avalua després quins corredors són viables amb la bateria i les parades.
- L'estil Liberty d'OpenFreeMap ha respost i Photon ha retornat Barcelona Sants amb les capçaleres de llengua catalana, castellana i anglesa. Queda pendent comprovar al Pixel la pantalla, les accions sobre parades i l'obertura efectiva dels navegadors.

## Mapa d'Espanya sense illes al Pixel — 8/11/2026

- Paquet `PTrack-Mapa-Espanya-sense-illes.zip`, generat el 7/11 amb Valhalla 3.9.1 a partir de l'extracte OSM de Geofabrik del 5/11 i 109 fitxers d'elevació de Mapzen. Cobreix l'Espanya peninsular, Ceuta i Melilla. Mida del ZIP **1.897.361.887 bytes** i del paquet extret **3.006.985.245 bytes**. SHA-256 verificat a l'ordinador i al Pixel `bb4979681ca4a2aac8bd54516023c0a75685abd0ee7af752396b9ffdba80ac71`.
- A l'ordinador, el graf empaquetat ha calculat Barcelona Sants–Madrid Atocha (**619,344 km** i **20.646 mostres d'elevació vàlides**) i una variant de **628,081 km** passant per tres punts CCS seleccionats del NAP. També ha calculat rutes locals a Ceuta i Melilla. Les coordenades de prova de Mallorca i Canàries han retornat l'error de punt fora de cobertura.
- Al Pixel 10, **Planificador → Importa un mapa de rutes** de PTrack 0.2.0 ha activat el graf nacional. La comprovació del manifest inclou la ruta Barcelona–Madrid amb elevació. El graf actiu de **2.178.744.320 bytes** coincideix amb el de l'ordinador.
- El planificador ha proposat automàticament **quatre parades** en uns **12 s** amb l'opció **Arribar abans**, bateria inicial del 75%, reserva del 10% i mínim de 50 kW CCS. Amb els valors de prova de 46 kWh útils, 18 kWh/100 km de referència i 100 kW màxims DC, ha estimat **434 min** de trajecte, **80 min** de càrrega i **14%** de bateria al destí. Parades proposades: Repsol CRED La Bisbal (50 kW), àrea de servei de Fraga (180 kW), Saragossa (250 kW) i TotalEnergies Hostal Torremar (100 kW).
- L'itinerari de prova s'ha tancat sense activar-lo. Els tres valors de prova de capacitat, consum de referència i potència màxima DC queden preomplerts al formulari. La prova s'ha fet al telèfon. La navegació al Peugeot i les estimacions amb dades reals del vehicle queden pendents.

## PTrack 0.2.0 desplegada al Pixel per a la prova al Peugeot — 7/11/2026

- `assembleDebug` i les proves unitàries han passat. APK `artifacts/ptrack-planner-pixel-20261107/PTrack-0.2.0.apk`, paquet `cat.ptrack`, versió 0.2.0 (2), SHA-256 local i instal·lat `FC230F9512A7FBABF69A267C923CC3A65F77F8128FE72DD386A12679B27EB464`. Certificat coincident amb el de la versió 0.1.0 que hi havia al Pixel.
- Actualització a les 20:43 CET amb **KingInstaller 2.5 i Shizuku 13.6.0**. Play Protect ha completat l'anàlisi autoritzada per l'usuari i ha permès finalitzar la instal·lació.
- Diagnòstic `artifacts/android-auto-car-check/20261107-204356-976/`. Android 17 i Android Auto 17.9.664044. `installerPackageName=com.android.vending`, `initiatingPackageName=com.google.android.packageinstaller` i `cat.ptrack/.car.PTrackCarAppService` resoluble. Shizuku aturat després de verificar-ho.
- Còpia prèvia de dades a `%LOCALAPPDATA%\PTrack\Backups\ptrack-before-planner-20261107-203554184.tar`. SQLite ha passat la comprovació d'integritat abans i després de l'actualització i conserva els identificadors dels 3 viatges, les 3.889 mostres i les 4.286 lectures.
- Oberta la PTrack 0.2.0 al Pixel i importat el mapa pilot de Ceuta. El planificador mostra **Mapa local disponible**. Pendent de prova al Peugeot de l'obertura de l'app i de l'acció **Detalls del trajecte → Navega al pròxim punt**.

## Valhalla Mobile i mapa pilot local — 7/11/2026

- **Valhalla Mobile 0.6.4** substitueix les peticions HTTPS de rutes. A **Planificador → Importa un mapa de rutes**, PTrack valida la ruta del manifest abans d'activar el paquet. El càlcul de parades continua a PTrack. [Format del paquet](EV-PLANNER.md#paquets-de-rutes-locals)
- Paquet pilot `artifacts/PTrack-Mapa-Pilot-Ceuta.zip`, 11.636.859 bytes, SHA-256 `A710EDED1F27C824F9C14B2E262F13C1712A1E54AC2E4172B13036A94CCC7686`. Valhalla 3.9.1 a l'ordinador ha llegit el graf i calculat la ruta pilot de **7,114 km**, amb **239 mostres d'elevació** entre 1,7 i 73,3 m.
- `testDebugUnitTest`, `assembleDebug` i `assembleDebugAndroidTest` han passat. Al **Pixel 10**, la instrumentació del paquet temporal separat `cat.ptrack.offlinetest` ha confirmat importació, ruta nativa amb elevació, càlcul elèctric i conservació del mapa després d'un intent de substitució invàlid. Resultat `INSTRUMENTATION_CODE: -1` i `PASS: native offline routing, elevation, EV route and failed map replacement`.
- Els dos paquets temporals s'han retirat després de la prova. Queda pendent un paquet de cobertura més àmplia i una prova completa amb parades reals de recàrrega i obertura dels navegadors al Peugeot.

## Primera integració de rutes elèctriques i navegació — 7/11/2026

- S'ha afegit **Planificador → Calcula una ruta elèctrica**. Demana l'URL HTTPS de Valhalla i les coordenades, permet ajustar la bateria i la potència i preomple el SOC si hi ha una lectura OBD real i recent. El resultat es pot activar com a itinerari per trams per obrir-lo amb Waze o Google Maps.
- `testDebugUnitTest` i `compileDebugAndroidTestKotlin` han passat. Les proves sintètiques del motor cobreixen més d'una recàrrega, reserva insuficient i una ruta diferent segons si es prioritza el temps total o els minuts de càrrega.
- La consulta amb dos punts públics de Barcelona al servidor de demostració de Valhalla ha fallat durant la negociació TLS. La verificació del flux complet amb un servidor configurat, un catàleg carregat i el Pixel queda pendent. [Pla unificat i criteris de finalització](EV-PLANNER.md)

## Traspàs a Waze i Google Maps — 7/11/2026

- La pestanya **Planificador** desa un itinerari manual amb una adreça o unes coordenades per línia i el destí al final. Els dos botons obren el pròxim punt. El progrés només avança quan es marca el punt com a completat. Google Maps també pot previsualitzar els punts pendents en ordre. [Waze Deep Links](https://developers.google.com/waze/deeplinks) · [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started)
- A **Detalls del trajecte** d'Android Auto, amb un itinerari actiu, es mostra l'acció experimental **Navega al pròxim punt** per fer-la servir amb el vehicle estacionat. L'acció delega en el navegador predeterminat del host. [API CarContext](https://developer.android.com/reference/androidx/car/app/CarContext)
- `testDebugUnitTest` i `compileDebugAndroidTestKotlin` han passat. Les proves cobreixen la codificació d'adreces i coordenades, l'ordre i els límits de les parades de Maps i l'avanç explícit de l'itinerari. L'obertura efectiva dels navegadors al Pixel i el traspàs al DHU i al Peugeot queden pendents.

## Catàleg de recàrrega del NAP — 7/11/2026

- [XML de producció de la DGT](https://nap.dgt.es/datex2/v3/miterd/EnergyInfrastructureTablePublication/electrolineras.xml) descarregat el 7/11. La publicació conté 12.086 emplaçaments i 43.022 elements de connector. El fitxer és un inventari DATEX II amb ubicacions i potències. La mostra no inclou tarifes de recàrrega.
- PTrack programa la primera descàrrega i una actualització aproximadament setmanal quan hi ha connexió. El botó **Configuració → Punts de recàrrega → Actualitza ara** programa una descàrrega addicional. La data i el recompte corresponen a l'última importació correcta a `charging.db`. La substitució del catàleg és transaccional.
- `testDebugUnitTest` i `compileDebugAndroidTestKotlin` han passat. El lector Kotlin ha processat l'XML oficial complet (publicació de les 10:16:37 CET) i ha retornat els 12.086 emplaçaments amb identificadors únics i els 43.022 connectors.
- L'emulador Pixel 10 no ha pogut arrencar perquè l'acceleració de maquinari requerida no estava disponible. S'ha compilat la prova Android que comprova la reversió de SQLite després d'un XML truncat. L'execució d'aquesta prova i la verificació de la tasca programada queden pendents.

## Primera actualització de la PTrack habitual amb KingInstaller i Shizuku — 7/11/2026

- `cat.ptrack` actualitzada sobre la instal·lació existent al Pixel 10 amb Android 17. Paquet i certificat verificats abans d'actualitzar. S'ha desat una còpia local prèvia de SQLite, WAL i preferències fora de OneDrive a `%LOCALAPPDATA%\PTrack\Backups\ptrack-before-20261107-163750.tar`.
- KingInstaller 2.5 amb Shizuku 13.6.0. Play Protect ha analitzat l'APK amb autorització de l'usuari i ha permès completar l'actualització. APK inicial verificada al Pixel amb SHA-256 `2C99E86A1D7D513E7EC5D2E8B2707CF52FBA343A57B3EFA3F94B677D0EDDD5A0`.
- Instal·lador `com.android.vending`, iniciador `com.google.android.packageinstaller` i servei `cat.ptrack/.car.PTrackCarAppService` resoluble. Informe `artifacts/android-auto-car-check/20261107-164415-602/`.
- Migració SQLite de l'esquema 3 al 4 verificada al Pixel amb comprovació d'integritat i comparació de les identitats dels 3 viatges, les 3.889 mostres i les 4.286 lectures. S'han obert la vista mensual, la vista anual i els detalls d'un viatge.
- La primera comprovació al Pixel ha detectat que l'engranatge quedava sota la barra del sistema a Android 17. S'han aplicat els marges del sistema i la compilació corregida s'ha desplegat sobre la mateixa app amb el mateix certificat, després d'una segona anàlisi autoritzada de Play Protect.
- **APK definitiva al Pixel a les 16:53:** `artifacts/ptrack-mobile-deploy-20261107-163750043/PTrack-mobil-ajustos.apk`, SHA-256 local i instal·lat `14961CC39DB388C3117ADFA1F7FBC378F2D2BCFA378B3ECDD006220DB3F2D24B`. `assembleDebug` i proves unitàries completades. S'han tornat a verificar l'origen i el servei amb l'informe `artifacts/android-auto-car-check/20261107-165406-240/`. Shizuku s'ha aturat.
- Verificats al Pixel amb l'APK definitiva els calendaris mensual i anual, el flux de dia a viatge, l'engranatge, el desplegable d'idioma i els controls de còpia amb data de darrera còpia. La segona comprovació d'integritat conserva les mateixes identitats de viatges, mostres i lectures. Queden pendents la prova de Drive entre dos dispositius i la prova de la variant habitual al Peugeot.

## Actualització al Pixel amb l'indicador Bluetooth OBD — 7/11/2026

- **PTrack Prova cotxe actualitzada** al Pixel 10, Android 17 i Android Auto 17.9.664044. Paquet `cat.ptrack.cartest`, versió `0.1.0-car-test`, certificat idèntic al de la instal·lació anterior. La PTrack habitual `cat.ptrack` conserva la data d'actualització anterior.
- `assembleCarTest` i les proves unitàries han passat. APK `artifacts/obd-action-deploy/PTrack-Prova-cotxe-OBD.apk`. SHA-256 local i instal·lat `3D709BCC771534F2DB59CE5E245DDF13117F42B32E1946630963A831C9A59152`.
- KingInstaller 2.5 amb Shizuku 13.6.0. La primera sessió va acabar amb `INSTALL_FAILED_VERIFICATION_FAILURE` en tancar-se el diàleg de Play Protect. En repetir-la, l'usuari va autoritzar l'anàlisi de Google. Play Protect va mostrar «Aquesta aplicació sembla segura» i es va completar l'actualització.
- Android confirma `installerPackageName=com.android.vending`, `initiatingPackageName=com.google.android.packageinstaller` i `cat.ptrack.cartest/cat.ptrack.car.PTrackCarAppService` resoluble. Shizuku aturat després del diagnòstic `artifacts/android-auto-car-check/20261107-151105-594/`. Pendent de prova visual de l'indicador al DHU i al Peugeot i de verificar-ne els estats amb l'adaptador OBD real.

## Prova física positiva comunicada per l'usuari — 7/11/2026

- L'usuari confirma que **PTrack Prova cotxe funciona al Peugeot** després de la instal·lació via KingInstaller 2.5 amb Shizuku oficial 13.6.0 al Pixel 10. Android 17 / Android Auto 17.9.664044. El servei Shizuku s'havia aturat després d'instal·lar.
- Variant `cat.ptrack.cartest`, APK i hash documentats a sota. La lectura posterior a la instal·lació havia verificat instal·lador `com.android.vending`, iniciador `com.google.android.packageinstaller` i servei de cotxe resoluble. Informe `artifacts/android-auto-car-check/20261106-221311-353/`.
- Resultat físic reportat per l'usuari; no s'han capturat noves traces des del vehicle ni s'ha confirmat la correcció de totes les lectures OBD. La PTrack habitual `cat.ptrack` encara no s'ha actualitzat amb aquest mètode.
- [Procediment validat i continuïtat de les actualitzacions](ANDROID-AUTO-VEHICLE.md). Les seccions següents conserven els estats i resultats històrics.

## Preparació de la prova al vehicle — 6/11/2026

- **Via gratuïta preparada, prova física pendent:** [KingInstaller/Shizuku amb variant independent](ANDROID-AUTO-VEHICLE.md). No hi havia cap dispositiu ADB connectat; no s'ha instal·lat res al mòbil ni s'ha verificat el Peugeot en aquesta sessió.
- APK signada: `artifacts/android-auto-vehicle/PTrack-car-test.apk`, paquet `cat.ptrack.cartest`, etiqueta **PTrack Prova cotxe**, versió `0.1.0-car-test`. Conviu amb l'original sense canviar-ne les dades. SHA-256: `F0A84E450EDAC4A5554C9045ECAA0716471F4052D1C1BA4A270235F25D032388`. `apksigner verify`: PASS.
- Eines actuals conservades: AGP 9.5.1 / Gradle 9.8.2 / JDK 17 / compileSdk 37 / targetSdk 35. Compilades les variants debug, release i carTest. La primera compilació completa va acabar a lint amb un error del camí Windows a `gradle.properties`, corregit escapant `C\:`; la repetició de lint i proves acaba **BUILD SUCCESSFUL**.
- Corregit el directori de proves per Kotlin integrat d'AGP 9: `.java.directories` deixava `testDebugUnitTest` com a **NO-SOURCE**; ara utilitza `.kotlin.directories`. **Quatre suites JUnit executades, 0 errors/fallades, 1.202 comprovacions** (39 + 32 + 66 + 1065). No es compta l'execució anterior sense fonts com un èxit de proves.
- Lint debug i carTest: **0 errors / 16 avisos** cadascun. Informes i XML de tests conservats a `artifacts/android-auto-vehicle/`. Els avisos inclouen target SDK, API obsoletes, recursos i l'allowlist de hosts de la biblioteca; no s'han silenciat globalment.
- Inspecció de les tres APK: **PASS** per paquet, etiqueta, servei exportat, descriptor compilat `template`, API mínim 1, categoria IOT sense POI, entrada mòbil i estat del receptor. El comprovador segueix el recurs compilat perquè AGP escurça els noms XML en release. Android Auto és ara a `main`; receptor de simulació actiu només a debug. Validació de hosts oficials a release/carTest.
- Scripts de diagnòstic i inspecció: parser PowerShell PASS. Diagnòstic nou comprovat amb mocks de captura completa, error parcial, filtratge i dispositiu no autoritzat. No modifica instal·lador, logs, serveis ni configuració del telèfon.
- Límit: compilació, signatura i manifest no acrediten admissió d'Android Auto. KingInstaller és una prova no oficial basada en evidència externa recent, encara sense resultat físic amb PTrack. No s'ha pagat, creat ni publicat cap app a Play Console.

## Migració completa a Kotlin — 4/11/2026

[Abast, fonts, eines, proves i ordres per repetir-les al Pixel](KOTLIN-MIGRATION.md).

- **31 fonts Kotlin, 0 fonts Java propis** a app/proves; tres scripts Gradle Kotlin DSL. Kotlin 2.3.21, Gradle 8.13, AGP 8.13.2, JDK 17. Es conserven minSdk 26 / compileSdk 34 / targetSdk 34, esquema SQLite v3 i resum intern v2.
- **1.198 comprovacions PASS**, tant a la referència Java com a les suites Kotlin: 32 + 39 + 1065 + 62. Les quatre suites JUnit passen a la build final. Les vuit sentències de creació de taules/índexs s'han comparat amb la referència i són idèntiques; també s'han comparat els triplets de traducció de MainActivity.
- Passada final `--offline`: **BUILD SUCCESSFUL**, codi zero, amb `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`, `assembleRelease`. Lint: **0 errors / 8 avisos**. APK debug/release inspeccionades: **PASS**, servei/receptor experimental absents de release. Certificat debug idèntic al de l'APK Java.
- APK: `artifacts/kotlin-migration/PTrack-kotlin-debug.apk`; SHA-256 `4B08A98E2DF1957BD58232C3C41BD630740C8ADAEF5FC5CFC3A3BC2580961469`; build UTC `2026-11-04T19:52:48.287793300Z`. XML de les proves, lint i log final conservats a `artifacts/kotlin-migration/`.
- **APK Kotlin instal·lada al Pixel 10 amb `-r`: Success**. MainActivity oberta i peu de build verificat amb UI Automator, amb la mateixa hora UTC indicada a dalt.
- **Instrumentació Kotlin al Pixel: PASS explícit**. Migracions v1/v2→v3, resums/reobertura/NULL antics, consum, persistència SQLite, recuperació de trajecte interromput, SOC, PDF ca/es/en, renderització i activitat. Log: `artifacts/kotlin-migration/pixel-instrumentation.txt`. Les dues pàgines del PDF català sintètic també s'han inspeccionat visualment.
- **DHU oficial Wide amb aquesta APK Kotlin: PASS visual**. PTrack principal (770×700 dp, API 8) amb Waze secundària; tres rellotges grans en línia, consum, regeneració, càrrega, aturat, lectures caducades, desconnexió/reconnexió i detalls finalitzats amb totals conservats. Captures sintètiques a `artifacts/kotlin-migration/dhu-*.png`. Simulació desactivada en acabar; `dhu-final-real.png` mostra lectures `--` sense marca de simulació.
- **Pendent:** nova prova física Bluetooth/OBD i al Peugeot amb Kotlin. Normal/Narrow no repetits amb aquesta APK. Les seccions següents documenten builds Java anteriors.
- Fonts Java/fixtures i APK anterior guardats localment abans de migrar; dades privades de trajectes no s'han tornat a copiar. Branca mantinguda `feature/android-auto-minimal`; cap merge automàtic.

## Tres rellotges grans en línia segons l'amplada — 4/11/2026

- Regla actual: amplada assignada a PTrack ≥600 dp → una `GridSection` amb tres elements LARGE. Amplada inferior → dues seccions 2+1, LARGE amb alçada ≥600 dp o MEDIUM en espais més baixos. El llindar és una decisió de PTrack; el host continua decidint la geometria final.
- **Verificat visualment amb la build final** al DHU Wide (PTrack 770×700 dp, Waze secundària) i al DHU Normal (800×400 dp sobre una pantalla 800×480): tres rellotges grans en una sola fila, llegibles i sense solapaments. Captures sintètiques `artifacts/android-auto-graphics/large-row-wide.png` i `large-row-800.png`. La branca estreta no s'ha tornat a validar amb Narrow.
- Debug + APK instrumentada + lint: **BUILD SUCCESSFUL**, 0 errors / 5 avisos. APK instal·lada al Pixel amb `-r`. Build UTC `2026-11-04T18:37:55.122229200Z`; SHA-256 `84BD8F6F294C142D295EBFF3C021447C11C3CB52A6C38EC5F3C9BD7F4FF1DA2D`. Artefacte actualitzat: `artifacts/android-auto-graphics/PTrack-android-auto-graphics-debug.apk`.
- Mides observades amb GridSection 1.7.0: SMALL ≈64 px, MEDIUM ≈88 px, LARGE ≈160 px. Són mides d'imatge aproximades d'aquest host, no mides fixes garantides a tots els vehicles. La taula i el criteri de selecció són a `ANDROID-AUTO-GRAPHICS.md`.
- Canvis d'aquesta revisió limitats a la selecció de distribució a `PTrackCarScreen.java` i documentació. Simulació de pantalla desactivada en acabar. Les seccions següents són resultats de revisions anteriors; no hi ha cap prova nova al Peugeot.

## Distribució 2+1 amb rellotges grans — 4/11/2026

- S'ha substituït `GridTemplate` per `SectionedItemTemplate` amb dues `GridSection`: potència/bateria a dalt i intensitat a baix a l'esquerra. El quart espai queda sense contingut. Biblioteca 1.7.0 i categoria experimental existents conservades.
- **DHU Wide 1280×720, zona principal 770×700 dp: verificat**, amb Waze al costat i tots tres rellotges visibles. Imatges d'uns 160 px (`GridSection.ITEM_SIZE_LARGE`) amb marge transparent per evitar que els arcs es toquin. Captura `artifacts/android-auto-graphics/sections-wide-final.png`. El límit anterior de 88 px era de la configuració de `GridTemplate`, no un límit general de totes les plantilles.
- **DHU Normal 800×480, zona principal 800×400 dp: verificat**, els tres rellotges i noms són visibles amb la mida MEDIUM. La mida LARGE en aquesta alçada deixava la segona fila fora de la vista; s'ha afegit adaptació per alçades <600 dp. Captura `sections-800-final.png`. El host governa l'amplada i alineació de les cel·les; no són quadrants amb amplades fixades per PTrack.
- Debug + APK instrumentada + lint: **BUILD SUCCESSFUL**, 0 errors / 5 avisos. APK final instal·lada amb `-r`, sense desinstal·lar. Build UTC `2026-11-04T18:32:31.429728700Z`, SHA-256 `9F9487811DA71C35DF93D4A900E229D4E97456238FD42237EC558509FE311D01`.
- Canvis d'aquesta revisió: `PTrackCarScreen.java` (seccions i mida segons alçada), `GaugeRenderer.java` (marge transparent), documentació. Les comprovacions següents documenten les revisions anteriors. Cap prova nova al Peugeot.
- Regeneració negativa i obertura de Detalls des de la nova capçalera: **verificades visualment** (`sections-wide-regen.png`, `sections-details.png`). En acabar s'ha desactivat la simulació i s'ha tornat a la principal amb font real.

## Revisió visual: rellotges sense targetes ni dades secundàries — 4/11/2026

- Principal simplificada: només els tres rellotges i els seus noms; consum instantani/mitjà, velocitat i estat consultables als detalls. Les anotacions minúscules de sentit del corrent es traslladen als detalls. SOC i energia BMS es mantenen dins del rellotge de bateria.
- Fons de bitmap transparent, sense rectangle blau; arcs ampliats i números més grans. Prova A/B real al DHU Wide: `ITEM_SIZE_LARGE` dona 64 px, `ITEM_SIZE_MEDIUM` 88 px. Es conserva MEDIUM. Treure text no elimina el límit d'imatge del host ni permet fer ocupar als rellotges tota la superfície.
- Debug + APK instrumentada + lint: **BUILD SUCCESSFUL**, 0 errors / 5 avisos. Actualització al Pixel: **Success**. Principal i detalls comprovats visualment al DHU amb dades sintètiques, captures `artifacts/android-auto-graphics/clean-medium.png` i `clean-details.png`. Els detalls mostren les dades retirades de la principal sense truncar-les en la prova Wide.
- Build instal·lada UTC `2026-11-04T18:14:16.014466600Z`. SHA-256 de l'APK `8D01B7A38EC48760D7E3BFE952BF86C0BBF8D50D7DA7D9C17B3AC962389D5714`. Artefacte actualitzat: `artifacts/android-auto-graphics/PTrack-android-auto-graphics-debug.apk`.
- Canvis d'aquesta revisió a `GaugeRenderer.java`, `PTrackCarScreen.java` i documentació. La simulació de pantalla es desactiva en acabar; no s'ha executat una nova prova al Peugeot.

## Quadre gràfic i build visible — 4 de novembre de 2026

[Resultats, límits i fitxers de la fase actual](ANDROID-AUTO-GRAPHICS.md). Nucli: **1198 comprovacions PASS**. Debug + APK instrumentada + lint i release: **BUILD SUCCESSFUL**. Instrumentació repetida al Pixel després d'afegir el peu: **PASS**, incloent migracions v1/v2→v3, resum automàtic, reobertura i dades antigues/NULL. APK instal·lada amb `-r`, peu verificat visualment i amb UI Automator: debug, AA experimental, `feature/android-auto-minimal`, commit `8c79b70d860e`, `modified`, hora UTC de build `2026-11-04T17:41:40.693603900Z`. Captura local: `artifacts/android-auto-graphics/phone-build-info.png`.

**Peugeot: resultat negatiu reportat per l'usuari.** No apareix a Android Auto ni a la personalització del menú. ADB confirma servei debug resoluble, paquet habilitat i instal·lador local (`null`). Sense rebuig específic conservat a Logcat: el filtre d'origen de confiança és una hipòtesi sustentada per la documentació oficial, no una causa única demostrada amb traces del vehicle.

Continuació de la tasca interrompuda per quota: **DHU Wide 1280×720 executat amb la build final**. PTrack principal (770 dp) + Waze secundària, amb acceleració, regeneració, càrrega, aturat, desconnexió/reconnexió, finalització i caducitat (>10 s) verificades amb captures de SIMULACIÓ. Detall finalitzat amb totals conservats. Waze principal + PTrack secundària no aconseguit: el host substitueix PTrack per la targeta multimèdia activa. El perfil Narrow no és una prova de pantalla partida i els indicadors no s'hi han validat. Lint final: **0 errors / 5 avisos**. Comprovació dels APK debug/release: **PASS**, sense servei experimental ni receptor de prova a release. `test-core.ps1` també detecta automàticament el JDK 17 complet d'aquesta estació.

## Quadre de telemetria Android Auto — 3–4 de novembre de 2026

**Implementat i provat al DHU amb Pixel 10 físic.** Resultats complets, arquitectura, fitxers i límits: [ANDROID-AUTO-DASHBOARD.md](ANDROID-AUTO-DASHBOARD.md).

- APK final: `artifacts/android-auto-dashboard/PTrack-android-auto-dashboard-debug.apk`. Compilació debug, instrumentada, lint i release: **BUILD SUCCESSFUL**. Lint: 0 errors / 6 avisos.
- Nucli: **32 + 39 + 1065 = 1136 comprovacions superades**. Les noves comproven càlculs, signe del perfil, publicació atòmica, concurrència, caducitat, desconnexió/reconnexió, buits, deduplicació, totals i independència dels observadors.
- `SmokeInstrumentation` executada al Pixel durant aquesta fase: **PASS**, incloent SQLite, recuperació, aprenentatge, PDF/render i activitat del mòbil. L'últim ajust del limitador de presentació s'ha tornat a provar amb dades canviants i caducitat al DHU.
- DHU: valors principals simultanis, valors canviants marcats SIMULACIÓ, pàgines Detalls/Bateria, regeneració negativa, càrrega probable estacionada, instantani a velocitat zero, caducitat, desconnexió i reconnexió, sortida/reobertura: **verificats visualment**. Prova sintètica separada del registre real. Captures i logs locals als artefactes.
- SHA-256 de la base real i del WAL idèntics abans/després de les proves de pantalla. Cap crash del procés final de PTrack observat. Components de prova absents del manifest release.
- Simulació aturada amb `Scenario real`; pantalla final `--` / Desconnectat, sense marca de simulació, verificada. **No s'han provat aquestes lectures al Peugeot ni amb un ELM físic en aquesta fase.**

Les seccions següents documenten les fases anteriors.

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
