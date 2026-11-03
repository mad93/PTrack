# PTrack · Peugeot e-2008

Primera versió Android nativa per a un **Peugeot e-2008 de 2020** i un **ELM327 Bluetooth clàssic (SPP)**. Català per defecte, amb castellà i anglès seleccionables a Configuració. No és una aplicació oficial de Peugeot o Stellantis.

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

1. Instal·la l’APK de desenvolupament `artifacts/PTrack-0.1.0-debug.apk` en un Android 8.0 o superior. En una compilació estàndard també es genera a `app/build/outputs/apk/debug/app-debug.apk`. No està preparada per publicar a Google Play.
2. Aparella l’ELM327 des dels ajustos Bluetooth d’Android. Aquesta versió no admet ELM327 BLE, Wi-Fi ni USB. «ELM327» no identifica el transport: cal comprovar que el teu adaptador ofereix Bluetooth clàssic.
3. Obre PTrack, prem **Connecta OBD** i concedeix el permís Bluetooth. Ubicació precisa i notificacions són recomanables, però la telemetria OBD no depèn del GPS.
4. Selecciona l’adaptador. Amb el vehicle despert, l’app començarà el registre quan obtingui corrent, tensió, SOC i velocitat vàlids.
5. A **Configuració → Associa l’OBD per a l’inici automàtic**, accepta l’associació que presenta Android. Mantén activat el registre automàtic. Aquesta configuració és necessària per a l’arrencada per presència amb l’app tancada a Android 12+; aparellar als ajustos Bluetooth no és equivalent a associar l’app.
6. Una interrupció de menys de 45 s manté el viatge obert; després es tanca i genera el PDF. **Atura i desa el viatge** també el tanca i desactiva l’arrencada automàtica fins que la tornis a activar.
7. A **Viatges**, consulta el resum o exporta PDF/JSON amb el selector de fitxers d’Android.

L’arrencada en segon pla no demana ubicació permanent. Per això, un servei iniciat automàticament pot registrar OBD sense GPS fins que obris l’app amb permís d’ubicació. Obrir-la activa la localització del servei, que pot continuar amb la pantalla apagada. Si no hi ha altitud fiable, el PDF mostra «—»; no inventa desnivell. No s’ha implementat un receptor d’arrencada després de reiniciar el mòbil; les restriccions del fabricant i les aturades forçades poden impedir la detecció automàtica.

## Android Auto: requisit pendent

**Aquesta APK encara no inclou Android Auto.** Android Auto no és una projecció lliure de l’activitat del mòbil. La via oficial requereix una categoria admesa i plantilles amb normes específiques; la documentació actual no garanteix una categoria per a un panell OBD amb rellotges propis. No s’ha declarat una categoria fictícia de navegació, multimèdia o IoT.

El component de telemetria i registre és independent de la pantalla, de manera que un futur client de cotxe podrà consumir les mateixes dades. Cal resoldre la viabilitat de la distribució i de la UI oficial abans de comprometre un panell Android Auto. Una prova de desenvolupador al cotxe no equival a compatibilitat aprovada.

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

Mòdul local independent amb perfils SQLite per velocitat i pendent net observat, filtres de qualitat, mitjana ponderada per distància i indicador de confiança. Sense prou observacions retorna «dades insuficients». La interfície Java per al futur planificador i els límits es documenten a [docs/CONSUMPTION-LEARNING.md](docs/CONSUMPTION-LEARNING.md). Aquesta fase no afegeix planificació ni una pantalla nova.

## Persistència i exportació

La base de dades és privada de l’app. Els informes automàtics són a `files/reports` dins del seu espai privat; l’exportació en crea una còpia on triïs. Sense xarxa ni núvol, sense còpia automàtica d’Android. Desinstal·lar elimina les dades privades.

El JSON inclou esquema, vehicle, simulació, metadades del viatge, totes les mostres i lectures originals. És una còpia consultable i processable; **encara no hi ha importador JSON a l’app**. El PDF no substitueix aquest registre. Si Android mata el procés, el viatge queda marcat com a interromput a la pròxima obertura i es pot regenerar l’informe des de l’historial.

## Compilar i comprovar

Requisits: JDK 17–21, Android SDK 34 amb Build Tools 34.0.0. Gradle Wrapper 8.4 inclòs, Android Gradle Plugin 8.2.2. Primera compilació amb xarxa per descarregar dependències. Aquest projecte utilitza el SDK 34 disponible a l’entorn de desenvolupament; cal actualitzar eines i target SDK abans de plantejar la publicació a Play.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
# Crea local.properties amb sdk.dir=<ruta al teu SDK> (fitxer ignorat per Git).
./gradlew.bat :app:assembleDebug :app:lintDebug
./scripts/test-core.ps1
```

Les proves del nucli comproven integració, regeneració, buits, desnivell, fórmules del perfil, respostes ELM i bloqueig d’ordres d’escriptura. El script admet `JAVA_HOME` i `ANDROID_HOME`; els valors per defecte són els d’aquesta estació Windows. Les proves instrumentades addicionals es descriuen a [docs/VALIDATION.md](docs/VALIDATION.md).

## Procedència

Perfil `stellantis-ecmp.json` v4 d’[Esco EV OBD Scanner](https://github.com/EsaHub/EscoEvObdScanner), commit `722f12c203a1ff1ced0fbcff01316c5a67d22ad2`. Atribució: Esa Pelttari, **CC BY 4.0**. Còpia sense canvis del perfil. Es conserva també la llicència MIT del projecte, utilitzat com a referència de protocol. Vegeu [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

El perfil està contrastat principalment en altres vehicles e-CMP; això no certifica un e-2008 de 2020 ni el teu adaptador. La primera prova real ha de validar signe del corrent, SOC, velocitat, estabilitat del vehicle i reconnexió. El mode simulat no valida la connexió física.
