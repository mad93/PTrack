# Validació

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
