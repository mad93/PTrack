# PTrack al cotxe: guia d'instal·lació i actualització

Revisió: 8/11/2026. **L'usuari confirma que PTrack Prova cotxe funciona al Peugeot després de la instal·lació amb KingInstaller i Shizuku.** La confirmació correspon a la variant `cat.ptrack.cartest` i al procediment del 6/11; no s'extrapola a altres telèfons o versions d'Android Auto, ni acredita per si sola tots els càlculs o lectures OBD.

## Ús habitual i actualitzacions

El 8/11/2026 a les 02:34 CET s'ha actualitzat la PTrack habitual a **0.2.3 (5)** amb el mateix procediment de KingInstaller i Shizuku per corregir la selecció dels resultats del planificador. Play Protect ha completat l'anàlisi autoritzada. S'han verificat l'APK, l'origen, el servei, els viatges i el mapa nacional. Shizuku s'ha aturat. La prova al Peugeot queda pendent. [Registre de validació](VALIDATION.md#ptrack-023-al-pixel--8112026)

El 8/11/2026 a les 02:25 CET s'ha actualitzat la PTrack habitual a **0.2.2 (4)** amb KingInstaller 2.5 i Shizuku 13.6.0. Play Protect ha completat l'anàlisi autoritzada. S'han verificat l'APK instal·lada, l'origen, el servei d'Android Auto i la conservació dels viatges i del mapa nacional. Shizuku s'ha aturat. La prova al Peugeot queda pendent. [Registre de validació](VALIDATION.md#ptrack-022-al-pixel--8112026)

El 8/11/2026 s'ha actualitzat la PTrack habitual a **0.2.1 (3)** al Pixel amb KingInstaller 2.5 i Shizuku 13.6.0. Play Protect ha completat l'anàlisi autoritzada. S'han verificat l'APK instal·lada, l'origen, el servei d'Android Auto i la conservació dels viatges i del mapa nacional. Shizuku s'ha aturat. La prova d'aquesta versió al Peugeot queda pendent. [Registre de validació](VALIDATION.md#ptrack-021-amb-el-planificador-visual-al-pixel--8112026)

El 7/11/2026 s'ha actualitzat la PTrack habitual a **0.2.0 (2)** al Pixel amb KingInstaller 2.5 i Shizuku 13.6.0. Play Protect ha analitzat l'APK amb l'autorització de l'usuari. S'han verificat la conservació dels viatges, l'origen de la instal·lació i el servei d'Android Auto. Shizuku s'ha aturat i s'ha importat el mapa pilot de Ceuta a Planificador. Queda pendent la prova d'aquesta versió al Peugeot. [Registre de validació](VALIDATION.md).

El 7/11/2026 s'ha actualitzat `cat.ptrack.cartest` al Pixel amb l'indicador Bluetooth OBD. KingInstaller i Shizuku han completat l'actualització després que Play Protect analitzés l'APK amb el vistiplau de l'usuari. S'han verificat l'APK instal·lada, l'origen i el servei d'Android Auto, i s'ha aturat Shizuku. La prova de l'indicador al Peugeot queda pendent. [Registre de validació](VALIDATION.md).

`carTest` és una variant de compilació del mateix codi, amb paquet, dades i nom independents. La integració Android Auto és a les fonts compartides `main`. El 7/11/2026 també s'ha actualitzat la PTrack habitual (`cat.ptrack`) al Pixel amb KingInstaller i Shizuku, conservant el paquet, el certificat i els viatges. Android ha confirmat l'origen esperat i el servei resoluble. Queda pendent provar la variant habitual al Peugeot. Els viatges de la variant de prova no es fusionen automàticament amb els de l'original. [Registre de validació](VALIDATION.md).

Per a cada APK nova destinada al cotxe, repetir el flux validat: iniciar Shizuku, seleccionar l'APK a KingInstaller amb Shizuku activat, acceptar **Actualitza** i verificar l'origen registrat. No cal reinstal·lar les eines cada cop, ni mantenir Shizuku actiu per conduir: es va aturar abans de la prova positiva. Instal·lar després amb Android Studio, ADB o el gestor de fitxers pot alterar de nou l'origen, de manera que no és el flux validat per a l'ús al cotxe. Conservar `carTest` com a variant opcional de proves evita afectar els viatges habituals.

## Recepta per reprendre sense el context del xat

1. **Identifica l'app que s'actualitza.** Habitual: `cat.ptrack`, nom PTrack. Prova: `cat.ptrack.cartest`, nom PTrack Prova cotxe. Tenen dades separades. La prova física confirmada fins ara és de `cartest`.
2. **Prepara i comprova l'APK.** Si la versió instal·lada ja ofereix còpies de seguretat, desa una còpia dels viatges des de **Configuració → Còpies de seguretat** abans d'actualitzar. Compila la variant correcta, incrementa la versió quan correspongui i conserva la clau de signatura existent. Compara el certificat amb el de l'app instal·lada o una APK anterior verificada (`apksigner verify --print-certs`). Un APK amb un certificat diferent no és una actualització compatible. No desinstal·lis per superar aquest error. No assumeixis que una APK antiga a `artifacts/` és la compilació actual.
3. **Copia l'APK al mòbil, sense instal·lar-la per ADB.** Pots utilitzar USB o `adb -s <serial> push <APK> /sdcard/Download/<nom>.apk`. Això només copia el fitxer. No executis `adb install` ni Android Studio Run sobre la variant destinada al cotxe.
4. **Inicia Shizuku.** Les eines només s'instal·len una vegada. En la prova validada es va utilitzar Shizuku oficial 13.6.0 per USB: connectar/desbloquejar el Pixel, autoritzar depuració USB, obrir Shizuku i executar des del PC l'ordre que mostra a l'opció d'inici amb ordinador. Si es localitza l'executable via ADB, comprovar la ruta actual: a 13.6.0 és `libshizuku.so` dins de `applicationInfo.nativeLibraryDir`. Les rutes `/data/app/...` canvien en reinstal·lar; **no reutilitzar la ruta ni el PID d'una sessió anterior**. També es pot iniciar amb la depuració sense fil seguint la guia de Shizuku.
5. **KingInstaller → Shizuku.** Obre KingInstaller (2.5 en la prova validada), activa l'interruptor **Shizuku** i deixa **Root** desactivat. Si Shizuku acaba d'iniciar-se i KingInstaller no el detecta, tanca i torna a obrir només KingInstaller. Quan aparegui «Allow KingInstaller to access Shizuku?», l'usuari ha de concedir el permís. Comprova que l'interruptor Shizuku queda activat.
6. **Instal·la o actualitza.** A **Select APK to install**, tria el fitxer de Baixades i prem **Install as king**. A la confirmació d'Android comprova el nom de l'app i prem **Instal·la** si és nova o **Actualitza** si ja existeix. Si Play Protect recomana enviar l'APK a Google, amb l'autorització de l'usuari prem **Analitza l'aplicació** i, si el resultat permet continuar, **Instal·la** al diàleg de Play Protect. Espera el resultat final. Mentre l'actualització o l'anàlisi estan en curs, el diagnòstic encara pot mostrar l'APK anterior.
7. **Verifica abans de donar-ho per acabat.** Android ha de confirmar la instal·lació. Executa el diagnòstic amb el paquet correcte (ordres a sota). Esperat: instal·lador `com.android.vending`, iniciador `com.google.android.packageinstaller`, servei `cat.ptrack.car.PTrackCarAppService` resoluble. Si falla, conserva el text i els logs; no repeteixis el canvi posterior `pm set-installer`, que ja ha estat rebutjat en aquest Pixel.
8. **Finalitza i prova.** Atura Shizuku des de la seva interfície o, si l'has iniciat per ADB, identifica i atura només el procés actual `shizuku_server`. No cal root ni apagar Play Protect. Reconnecta Android Auto amb el cotxe estacionat, comprova el menú i obre l'app. Registra el resultat real a `docs/VALIDATION.md`. En futures actualitzacions, repeteix des del pas 1; per conduir o reiniciar el mòbil sense actualitzar l'APK no cal iniciar Shizuku.

Diagnòstic des de PowerShell a l'arrel del projecte, amb depuració USB autoritzada:

```powershell
# App habitual:
./scripts/android-auto-car-check.ps1 -PackageName cat.ptrack
# O variant de prova:
./scripts/android-auto-car-check.ps1 -PackageName cat.ptrack.cartest
```

Si hi ha diversos dispositius, afegeix `-Serial <serial>` obtingut amb `adb devices -l`.
Els informes queden a `artifacts/android-auto-car-check/`. Aquesta carpeta i les APK
estan ignorades per Git; el procediment persistent és aquest document i `AGENTS.md`,
no depèn de conservar els artefactes ni el xat. No desa claus privades de signatura.

## Prova de l'inici automàtic OBD

1. A **PTrack → Configuració → Perfils de vehicle**, selecciona el perfil del cotxe. Vincula-hi l’ELM327 aparellat i prem **Associa l’OBD per a l’inici automàtic**. Repeteix-ho per a cada adaptador i comprova’n la MAC si tenen el mateix nom. Si el mòbil ha d'iniciar la connexió amb l'ELM327, prem també **Associa el Bluetooth del cotxe** i tria el Peugeot aparellat. Comprova que **Registre automàtic en connectar** queda activat.
2. Amb el cotxe apagat, surt de PTrack, espera que desaparegui la notificació d'un intent anterior i bloqueja el telèfon. Engega el cotxe sense obrir l'app. Comprova que apareix la notificació i que es desa un sol viatge amb mostres reals. Repeteix-ho amb Android Auto per cable per comprovar si en aquesta configuració el Peugeot estableix la connexió Bluetooth que fa de disparador.
3. Per distingir el senyal Bluetooth, l'arrencada del servei, la connexió SPP i la primera mostra vàlida, consulta `adb logcat -s PTrackObdAuto:D`. Sense dades inicials, la connexió automàtica té una finestra de 60 segons. Amb dades, 45 segons sense cap mostra vàlida tanquen el viatge.
4. Si el cotxe i l'OBD no generen cap senyal útil, prem **Espera l’OBD durant 30 minuts** abans de sortir. El servei manté la notificació i la CPU disponible durant l'espera, i deixa 45 segons entre intents quan no hi ha cap viatge actiu. Si comença un viatge, el registre pot continuar més enllà dels 30 minuts. L'acció **Atura** de la notificació acaba la sessió.
5. Deixa actiu un perfil diferent i connecta l’OBD vinculat a un únic vehicle. Verifica que el perfil canvia en rebre la primera lectura vàlida i que el viatge apareix a l’historial d’aquell vehicle. Comparteix la mateixa MAC entre dos perfils i repeteix-ho. Amb un perfil vinculat actiu, s’ha de mantenir. Amb un tercer perfil actiu, la notificació ha de demanar que triïs el vehicle abans que es desi el viatge. Comprova que una reconnexió durant el viatge en conserva el perfil.

La prova amb el Pixel 10 bloquejat, l'ELM327 i el Peugeot queda pendent. Una arrencada automàtica pot registrar l'OBD amb l'app en segon pla. El GPS continua disponible quan s'obre PTrack i hi ha permís de localització precisa.

Un perfil identifica un vehicle concret. Els perfils separen l’historial i els càlculs del planificador. La lectura de senyals OBD continua utilitzant el descodificador Stellantis e-CMP.

## Què explica la diferència amb el DHU

PTrack fa servir Car App Library (`CarAppService` i descriptor `template`). El manifest actual ja declara aquests elements. Google exigeix un origen de confiança per provar aquestes apps en vehicles: l'opció «Fonts desconegudes» d'Android Auto **no** s'aplica a aquesta biblioteca. El DHU és un entorn de desenvolupament amb condicions diferents. [Documentació oficial](https://developer.android.com/training/cars/testing#test-in-real-vehicles).

A les traces històriques del projecte, la instal·lació era local i el servei es podia descobrir, però no es va capturar el rebuig concret del cotxe. El filtre d'instal·lació és una explicació fonamentada, encara no una causa única demostrada per a aquest telèfon.

## Primera opció: prova gratuïta amb KingInstaller

**És un procediment no oficial i experimental.** KingInstaller modifica les dades d'origen de la instal·lació perquè consti Google Play com a instal·lador. El seu projecte declara suport de Shizuku en Pixel amb Android 17, sense root. [Projecte i instruccions](https://github.com/fcaronte/KingInstaller), [versió 2.5](https://github.com/fcaronte/KingInstaller/releases/tag/2.5).

L'anàlisi publicada d'Android Auto 17.7 descriu la comprovació tant de l'instal·lador com de qui inicia la instal·lació, i inclou els serveis `TEMPLATE`. Per això `adb install -i com.android.vending` sol no és equivalent a aquest procediment. Aquesta anàlisi va orientar la prova de PTrack, confirmada per l'usuari el 7/11 amb la configuració indicada. [Anàlisi tècnica al repositori Fermata](https://github.com/AndreyPavlenko/Fermata/issues/846).

El desenvolupador d'AAGearUP documenta una prova amb Pixel 10 Pro, Android Auto 17.9, KingInstaller 2.4 i Shizuku 13.5. És una altra aplicació: no certifica PTrack. [Referència de compatibilitat](https://www.aagearup.com/install-shizuku). Segueix els passos de PTrack següents; no cal copiar d'aquesta referència la desinstal·lació ni la desactivació de Play Protect.

### Fitxer que has de provar

`artifacts/android-auto-vehicle/PTrack-car-test.apk`

- Nom al mòbil i al menú: **PTrack Prova cotxe**.
- Paquet independent: `cat.ptrack.cartest`.
- Conviu amb `cat.ptrack`; té dades, permisos i configuració propis. No importa l'historial existent.
- Signada amb clau de desenvolupament; no és una versió de producció. Receptor ADB de simulació desactivat i validació del host activada.

### Passos al telèfon

1. Copia `PTrack-car-test.apk` a **Baixades**, sense instal·lar-la encara amb el gestor de fitxers o Android Studio. Conserva la PTrack habitual instal·lada.
2. Instal·la **KingInstaller 2.5** des dels Assets de la [release del seu autor](https://github.com/fcaronte/KingInstaller/releases/tag/2.5). Concedeix l'accés al fitxer i el permís d'instal·lació que necessiti.
3. Utilitza directament el mètode **Shizuku** validat en aquest Pixel: la instal·lació normal de KingInstaller va deixar l'origen incorrecte i va provocar l'avís de compatibilitat.
4. Si encara falta, instal·la **Shizuku** des dels enllaços del [projecte oficial](https://shizuku.rikka.app/download/). Inicia'l per USB com a la recepta anterior, o bé amb **Depuració sense fil**, aparellant el codi amb el telèfon connectat a Wi-Fi. [Manual oficial](https://shizuku.rikka.app/guide/setup/). Això és independent del mode desenvolupador d'Android Auto.
5. A KingInstaller activa **Shizuku Trick**, autoritza KingInstaller a Shizuku i torna a instal·lar la mateixa APK. Shizuku li dona privilegis de depuració per a la instal·lació; no cal root. Si exigeix una instal·lació neta, elimina només **PTrack Prova cotxe**, després de conservar qualsevol dada de prova que vulguis guardar; mai la PTrack habitual.
6. Desconnecta i reconnecta Android Auto amb el cotxe estacionat. A **Android Auto → Personalitza el menú**, busca **PTrack Prova cotxe** i comprova també el menú del vehicle. Si la llista continua igual, reinicia el telèfon i reconnecta. No cal esborrar les dades d'Android Auto.
7. Obre l'app al cotxe. Sense OBD connectat els indicadors poden mostrar `—`: veure i obrir el panell ja valida la primera part. Per provar dades reals, configura els permisos i l'adaptador a la variant de prova. Atura el registre de l'altra PTrack abans de connectar-la a l'ELM327: les dues apps no han de competir per la connexió.
8. En acabar la instal·lació, pots aturar Shizuku i desactivar la depuració sense fil. Després d'un reinici cal tornar a iniciar Shizuku si necessites reinstal·lar. Mantén el mateix procediment per a futures actualitzacions; instal·lar després amb ADB o Android Studio pot tornar a canviar l'origen registrat.

La prova no requereix apagar Play Protect. Si Android bloqueja un dels APK, conserva el missatge concret per diagnosticar-lo abans de canviar proteccions.

## Si no apareix: captura útil

### Avís «App installed, but might NOT be AA compatible»

KingInstaller 2.5 mostra aquest avís quan la seva comprovació de les dades d'origen retorna fals. A Android 11+ exigeix que l'instal·lador sigui `com.android.vending` i que l'iniciador sigui Play o Package Installer. No inspecciona el descriptor `template`, el servei ni el dibuix de PTrack. [Condició al codi de KingInstaller 2.5](https://github.com/fcaronte/KingInstaller/blob/2.5/app/src/main/java/com/example/kinginstaller/InstallationUtils.kt#L109-L145).

**Captura real del Pixel 10, 6/11/2026:** Android 17, Android Auto 17.9.664044, KingInstaller 2.5 i PTrack `0.1.0-car-test`. Instal·lador i iniciador: `com.google.android.packageinstaller`; origen: `com.example.kinginstaller`. La condició d'iniciador es compleix, però la d'instal·lador falla. `cat.ptrack.cartest/cat.ptrack.car.PTrackCarAppService` es resol correctament i l'app està habilitada. Això explica l'avís de KingInstaller; no és encara una prova d'admissió o rebuig del cotxe.

Informe local: `artifacts/android-auto-car-check/20261106-161224-657/`. En aquesta captura no s'ha reinstal·lat ni modificat l'app. El projecte de KingInstaller inclou una correcció posterior de l'instal·lador amb Shizuku/ADB; Android encara pot denegar aquest canvi per permisos.

**Intent posterior autoritzat per l'usuari:** el Pixel ha rebutjat la modificació posterior de l'instal·lador amb `SecurityException: Caller does not have same cert as new installer package com.android.vending`. S'ha verificat que instal·lador i iniciador continuen sent Package Installer i que el servei de cotxe continua resolent-se. La PTrack original conserva la seva versió i data d'actualització. Resultat guardat a `artifacts/android-auto-car-check/installer-change-*/`. Repetir aquesta correcció amb el mateix permís no resol la denegació. El flux d'instal·lació de KingInstaller amb Shizuku inicia Package Installer amb identitat shell i els atributs d'origen durant la instal·lació; és un mecanisme diferent, que es va provar a continuació amb èxit (resultat següent). [Implementació](https://github.com/fcaronte/KingInstaller/blob/2.5/app/src/main/java/com/example/kinginstaller/ShizukuUtils.kt).

### Recollir una captura nova

**Resultat posterior del 6/11/2026: instal·lació amb Shizuku completada.** S'ha instal·lat Shizuku oficial 13.6.0, iniciat amb ADB sense root, i l'usuari ha concedit a KingInstaller el permís de Shizuku al telèfon. Amb aquest mètode s'ha actualitzat `cat.ptrack.cartest` sobre la instal·lació existent. Android ha mostrat «Aplicació actualitzada» i la lectura posterior confirma `installerPackageName=com.android.vending`, `initiatingPackageName=com.google.android.packageinstaller`, servei de cotxe resoluble. La PTrack original conserva la data d'actualització anterior. Informe: `artifacts/android-auto-car-check/20261106-221311-353/`. Això va resoldre la condició d'origen que provocava l'avís de KingInstaller. El 7/11 l'usuari va confirmar també el funcionament al Peugeot. No s'ha usat root ni s'ha desactivat Play Protect.

Connecta el mòbil al PC i autoritza la depuració USB. Executa just després de la prova:

```powershell
./scripts/android-auto-car-check.ps1 -PackageName cat.ptrack.cartest
```

Amb diversos dispositius, afegeix `-Serial`. L'informe queda a `artifacts/android-auto-car-check/<data-hora>/`. No modifica el telèfon. Desa versions, descoberta del servei, dades d'instal·lació i logs filtrats. Revisa `summary.txt`, `app-package.txt` i `car-logcat.txt`.

Cal distingir `installerPackageName` de `initiatingPackageName`. L'objectiu del procediment KingInstaller és un instal·lador Play i un iniciador Package Installer, però ni aquests noms ni un servei resoluble demostren admissió: cal veure el menú i obrir PTrack al vehicle. Busca també rebutjos `CAR.VALIDATOR` o errors `PTrackCar`. L'absència de logs no prova acceptació; el buffer pot haver caducat.

## Alternativa oficial si la gratuïta falla

Google documenta **Internal App Sharing** o una pista de prova interna. Requereix accés a Play Console, però no publicació pública. A Internal App Sharing es poden pujar APK signades amb clau de desenvolupament; Google les torna a signar. Per això s'ha preparat el paquet separat: evita haver de desinstal·lar l'original per un conflicte de certificat. [Ajuda oficial](https://support.google.com/googleplay/android-developer/answer/9844679?hl=en).

Si es tria aquesta via: configura l'app `cat.ptrack.cartest` i l'accés privat al compte de prova, puja l'APK i instal·la-la **des de l'enllaç de Play**, amb Internal App Sharing activat a Play Store. Si la variant ja estava instal·lada amb KingInstaller, pot caldre eliminar només aquesta variant pel canvi de signatura. Play Console pot exigir completar la configuració o una pista de prova perquè el tester tingui accés a l'app.

PTrack conserva la categoria **IoT experimental**, que no acredita que un panell de telemetria compleixi la categoria comercial d'IoT. Pagar el compte o superar el DHU no garanteix admissió ni publicació. S'ha retirat la categoria POI perquè PTrack no ofereix punts d'interès. [Definició oficial d'IoT](https://developer.android.com/training/cars/apps/iot).

## Recompilar

```powershell
./gradlew.bat :app:assembleCarTest
```

Sortida: `<ptrackBuildRoot>/app/outputs/apk/carTest/app-carTest.apk` si hi ha `ptrackBuildRoot` configurat; altrament `app/build/outputs/apk/carTest/app-carTest.apk`. Per comprovar la distribució nova, instal·la aquesta APK amb el procediment escollit, no amb el script de DHU.

La variant habitual debug continua sent `cat.ptrack`. El script `android-auto-dhu.ps1 -Action Install` serveix per al DHU i no acredita l'admissió al vehicle.
