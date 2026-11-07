# PTrack · Peugeot e-2008

Aplicació Android nativa escrita en **Kotlin**, per a un **Peugeot e-2008 de 2020** i un **ELM327 Bluetooth clàssic (SPP)**. Català per defecte, amb castellà i anglès seleccionables a Configuració. No és una aplicació oficial de Peugeot o Stellantis.

Migració completa del codi propi i les proves a Kotlin, amb configuració Gradle Kotlin DSL. [Abast, compatibilitat i validació de la migració](docs/KOTLIN-MIGRATION.md).

**Abans d'instal·lar o actualitzar per al cotxe:** segueix la [guia operativa Android Auto](docs/ANDROID-AUTO-VEHICLE.md). Al Pixel s'ha validat **KingInstaller + Shizuku per a cada APK nova**. Les [instruccions de continuïtat](AGENTS.md) ho recorden també a les futures sessions de treball.

## Què hi ha implementat

- Selecció de l’adaptador prèviament aparellat, estat de connexió i reconnexió amb espera de 15 s.
- Panell al mòbil: potència i corrent amb signe; càrrega percentual; velocitat, tensió i telemetria addicional.
- Lectures UDS `0x22` sobre CAN de diagnòstic, amb adreces del perfil e-CMP. No s’envien serveis d’escriptura, d’esborrat de fallades ni d’exploració d’adreces.
- Registre automàtic quan arriben les quatre lectures bàsiques vàlides. Una connexió Bluetooth sola no genera un viatge buit.
- Servei en primer pla amb notificació i bloqueig parcial de CPU renovable mentre hi ha dades, per registrar amb la pantalla apagada.
- Associació opcional amb Companion Device Manager. A Android 12 o superior, detecció de presència de l’OBD per activar el servei en segon pla després de configurar-la.
- Base de dades SQLite local amb viatges, mostres i respostes originals. Un procés interromput conserva les dades ja desades.
- Historial, resum de cada viatge i exportació del registre complet a JSON.
- PDF automàtic al final del viatge, recuperable i exportable des de l’historial. Dues pàgines: resum, SOC, potència, corrent, velocitat i altitud. Les gràfiques utilitzen tota l’amplada disponible i l’eix temporal correspon a la durada del viatge; els pics es preserven amb mínims i màxims per columna.
- Mode de simulació marcat explícitament a la pantalla, a l’historial, al JSON i al PDF.

## Instal·lació i primer ús

1. Prepara una APK actual en un Android 8.0 o superior. **Si la vols utilitzar al cotxe, instal·la-la o actualitza-la amb [KingInstaller + Shizuku](docs/ANDROID-AUTO-VEHICLE.md), conservant paquet i certificat.** El fitxer històric `artifacts/PTrack-0.1.0-debug.apk` no identifica necessàriament l'última compilació; comprova la variant i la data de build. No està preparada per publicar a Google Play.
2. Aparella l’ELM327 des dels ajustos Bluetooth d’Android. Aquesta versió no admet ELM327 BLE, Wi-Fi ni USB. «ELM327» no identifica el transport: cal comprovar que el teu adaptador ofereix Bluetooth clàssic.
3. Obre PTrack, prem **Connecta OBD** i concedeix el permís Bluetooth. Ubicació precisa i notificacions són recomanables, però la telemetria OBD no depèn del GPS.
4. Selecciona l’adaptador. Amb el vehicle despert, l’app començarà el registre quan obtingui corrent, tensió, SOC i velocitat vàlids.
5. A **Configuració → Associa l’OBD per a l’inici automàtic**, accepta l’associació que presenta Android. Mantén activat el registre automàtic. Aquesta configuració és necessària per a l’arrencada per presència amb l’app tancada a Android 12+; aparellar als ajustos Bluetooth no és equivalent a associar l’app.
6. Una interrupció de menys de 45 s manté el viatge obert; després es tanca i genera el PDF. **Atura i desa el viatge** també el tanca i desactiva l’arrencada automàtica fins que la tornis a activar.
7. A **Viatges**, consulta el resum o exporta PDF/JSON amb el selector de fitxers d’Android.

L’arrencada en segon pla no demana ubicació permanent. Per això, un servei iniciat automàticament pot registrar OBD sense GPS fins que obris l’app amb permís d’ubicació. Obrir-la activa la localització del servei, que pot continuar amb la pantalla apagada. Si no hi ha altitud fiable, el PDF mostra «—»; no inventa desnivell. No s’ha implementat un receptor d’arrencada després de reiniciar el mòbil; les restriccions del fabricant i les aturades forçades poden impedir la detecció automàtica.

## Android Auto: quadre de telemetria experimental

**7/11/2026 — prova positiva al Peugeot confirmada per l'usuari.** «PTrack Prova cotxe» funciona després d'instal·lar-la amb KingInstaller + Shizuku; Shizuku es va aturar en acabar la instal·lació. [Procediment validat, pas a la PTrack habitual i actualitzacions](docs/ANDROID-AUTO-VEHICLE.md). `carTest` comparteix el codi de l'app habitual i només separa la instal·lació i les dades per a proves. Els paràgrafs datats següents documenten les fases anteriors.

**6/11/2026 — prova al cotxe sense Play Console:** [procediment amb KingInstaller i Shizuku, APK separada i diagnòstic](docs/ANDROID-AUTO-VEHICLE.md). La variant `carTest` (`cat.ptrack.cartest`, **PTrack Prova cotxe**) permet provar una instal·lació nova sense desinstal·lar la PTrack que conté els viatges. És una alternativa no oficial pendent de prova física amb PTrack. «Fonts desconegudes» no habilita les apps Car App Library al vehicle; la via oficial continua sent distribució privada des de Play. No es garanteix l'admissió per haver compilat ni per funcionar al DHU.

La configuració actual inclou Android Auto a `main` i, per tant, també a release; les referències següents a «només debug» descriuen versions anteriors. El receptor de simulació ADB només s'habilita a debug. S'ha retirat la categoria POI sense funcionalitat associada i es conserva IoT experimental.

**Actualització 4/11/2026:** [quadre gràfic, historial ampliat i identificació de build](docs/ANDROID-AUTO-GRAPHICS.md). El mòbil mostra al peu versió/variant, branca, commit, estat dels canvis i hora UTC de compilació. APK actual: `artifacts/android-auto-dhu/PTrack-dhu-debug.apk`. **Prova de l'usuari al Peugeot: PTrack no apareix al menú ni a «Personalitza el menú».** ADB confirma que la variant debug i el servei són presents; l'admissió pel host del vehicle continua pendent. El resultat al DHU no garanteix acceptació al cotxe. Les línies següents descriuen la fase textual anterior.

La branca `feature/android-auto-minimal` amplia el client de **Car App Library només a debug** que ja funcionava: potència, consum mitjà i SOC visibles alhora, amb pantalles Detalls i Bateria per a consum instantani, velocitat, energies, tensió i intensitat. Consumeix les mostres que `RecordingService` ja ha desat i reutilitza `Sample.power()` / `TripStats`; no inicia connexions ni duplica consultes OBD. APK actual: `artifacts/android-auto-dashboard/PTrack-android-auto-dashboard-debug.apk`.

**Quadre provat al DHU amb Pixel 10, Android 17 i Android Auto 17.9:** valors canviants, lectures negatives, velocitat zero, caducitat, desconnexió/reconnexió i navegació verificades amb una font sintètica separada, sempre marcada **SIMULACIÓ**. En acabar s'ha tornat a la font real. Nucli: **1136 comprovacions superades**; proves instrumentades al Pixel: **PASS**. **Lectures reals i acceptació al Peugeot pendents de prova.** Es conserva la categoria IoT experimental de la integració existent; no s'afirma homologació ni compatibilitat garantida al vehicle.

[Antecedent de la integració mínima i configuració del Pixel 10](docs/ANDROID-AUTO-MINIMAL.md). [Resultats de les proves](docs/VALIDATION.md). Es conserva l'[estudi anterior](docs/ANDROID-AUTO.md) com a document històric; el quadre ja està implementat i validat al DHU, amb la prova física al Peugeot encara pendent.

[Arquitectura del quadre, indicadors, límits de signe/càrrega, escenaris de prova, fitxers i procediment al Peugeot](docs/ANDROID-AUTO-DASHBOARD.md). L'energia entrant inclou regeneració i possible càrrega; no s'inventa una separació. La potència positiva conserva la convenció de descàrrega documentada al perfil e-CMP, que cal contrastar físicament al Peugeot.

Entorn local reutilitzable: [inici ràpid DHU](docs/DHU-QUICKSTART.md). `./scripts/android-auto-dhu.ps1 -Action Doctor` comprova les eines; `-Action Run -Offline` compila, actualitza l'APK al Pixel i inicia el DHU quan el mòbil està autoritzat i el servidor Android Auto està actiu.

Fonts oficials consultades:

- [Categories Android for Cars](https://developer.android.com/training/cars)
- [Car App Library i plantilles](https://developer.android.com/training/cars/apps/library)
- [Associació de dispositius](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [Restriccions dels serveis en segon pla](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

## Càlculs i límits

- Signe positiu: descàrrega de bateria. Signe negatiu: energia entrant a la bateria, incloent regeneració o càrrega si el vehicle està endollat.
- Potència de bateria: `tensió × corrent / 1000` kW. No és potència mecànica a les rodes.
- Energia consumida i recuperada: integració trapezoïdal del senyal de potència. Els canvis de signe es divideixen al punt de creuament per separar les dues energies.
- Distància: integració de velocitat OBD. No es barreja amb distància GPS o un comptaquilòmetres amb una altra escala.
- Consum net: `(energia consumida − recuperada) × 100 / km`. Es deixa sense valor en recorreguts inferiors a 100 m.
- Velocitat mitjana: distància / temps cobert per lectures vàlides, incloent les aturades registrades.
- No s’interpolen buits de més de 10 s. L’informe mostra durada total i cobertura de dades perquè es pugui detectar un registre incomplet.
- Desnivell estimat: altitud GPS recent amb precisió horitzontal ≤30 m i vertical ≤10 m, filtre de 5 m i reinici després de 30 s sense altitud. Encara pot acumular soroll i no és una mesura topogràfica.
- Lectures bàsiques seqüencials, amb un cicle màxim acceptat de 8 s; tensió i corrent no són simultanis. La precisió i freqüència reals depenen de l’ELM327.
- La resta de senyals escalars es consulten per torns; les matrius de cel·les/sondes i metadades, cada deu cicles. Les respostes no disponibles no es converteixen en zero. Els camps de metadades sense fórmula es mostren en hexadecimal.
- Els adaptadors econòmics poden rebutjar les ordres de control de flux necessàries per a respostes llargues. En aquest cas, les cel·les poden no estar disponibles encara que les dades bàsiques funcionin.
- S’implementen els camps `live` del perfil, **no totes les eines del programa Windows**: explorador d’ECUs, lectura de DTC, informes d’avaluació de salut i estimador d’autonomia queden pendents.

## Aprenentatge del consum real

Mòdul local independent amb perfils SQLite per velocitat i pendent net observat, filtres de qualitat, mitjana ponderada per distància i indicador de confiança. Sense prou observacions retorna «dades insuficients». El contracte Kotlin per al futur planificador i els límits es documenten a [docs/CONSUMPTION-LEARNING.md](docs/CONSUMPTION-LEARNING.md). Aquesta fase no afegeix planificació ni una pantalla nova.

## Persistència i exportació

La base de dades és privada de l’app. Els informes automàtics són a `files/reports` dins del seu espai privat; l’exportació en crea una còpia on triïs. Sense xarxa ni núvol, sense còpia automàtica d’Android. Desinstal·lar elimina les dades privades.

El JSON inclou esquema, vehicle, simulació, metadades del viatge, totes les mostres i lectures originals. És una còpia consultable i processable; **encara no hi ha importador JSON a l’app**. El PDF no substitueix aquest registre. Si Android mata el procés, el viatge queda marcat com a interromput a la pròxima obertura i es pot regenerar l’informe des de l’historial.

## Compilar i comprovar

Requisits: **JDK 17**, Android SDK 34 i Build Tools 35.0.0. **Kotlin 2.3.21, Gradle Wrapper 8.13 i Android Gradle Plugin 8.13.2**. Primera compilació amb xarxa per descarregar dependències. `minSdk 26`, `compileSdk 34` i `targetSdk 34` es conserven.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE/.jdks/jbr-17.0.14"
# Crea local.properties amb sdk.dir=<ruta al teu SDK> (fitxer ignorat per Git).
./gradlew.bat :app:assembleDebug :app:lintDebug
./scripts/test-core.ps1
```

Les quatre suites Kotlin/JUnit del nucli conserven 1.198 comprovacions d'integració, regeneració, buits, desnivell, perfil, ELM, aprenentatge i telemetria. `test-core.ps1` detecta JDK 17 i executa `:app:testDebugUnitTest`; admet `-Offline` i `-BuildRoot`. Les proves instrumentades es descriuen a [docs/VALIDATION.md](docs/VALIDATION.md).

## Procedència

Perfil `stellantis-ecmp.json` v4 d’[Esco EV OBD Scanner](https://github.com/EsaHub/EscoEvObdScanner), commit `722f12c203a1ff1ced0fbcff01316c5a67d22ad2`. Atribució: Esa Pelttari, **CC BY 4.0**. Còpia sense canvis del perfil. Es conserva també la llicència MIT del projecte, utilitzat com a referència de protocol. Vegeu [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

El perfil està contrastat principalment en altres vehicles e-CMP; això no certifica un e-2008 de 2020 ni el teu adaptador. La primera prova real ha de validar signe del corrent, SOC, velocitat, estabilitat del vehicle i reconnexió. El mode simulat no valida la connexió física.
