# PTrack: continuïtat entre sessions

## Instal·lació i actualitzacions per a Android Auto

Abans de compilar per lliurar, instal·lar o actualitzar PTrack al telèfon, llegeix
`docs/ANDROID-AUTO-VEHICLE.md`. És la guia operativa vigent. Consulta també l'entrada
més recent de `docs/VALIDATION.md`; els documents antics descriuen estats superats.

- El 7/11/2026 l'usuari va confirmar funcionament al Peugeot amb Pixel 10,
  Android 17 i Android Auto 17.9.664044, després d'instal·lar `cat.ptrack.cartest`
  amb **KingInstaller 2.5 + Shizuku oficial 13.6.0**, sense root.
- Per instal·lar o actualitzar una APK destinada al cotxe, segueix aquest mateix
  flux: iniciar Shizuku, KingInstaller amb Shizuku activat, seleccionar APK,
  confirmar Instal·la/Actualitza, verificar origen i servei, aturar Shizuku.
- No utilitzis `adb install`, Android Studio Run, el gestor de fitxers ni
  `scripts/android-auto-dhu.ps1 -Action Install/Run` per lliurar una actualització
  destinada al cotxe: poden substituir l'origen d'instal·lació validat. ADB sí que
  serveix per copiar l'APK, iniciar Shizuku i llegir diagnòstics.
- No repeteixis `pm set-installer` com a solució: aquest Pixel el va rebutjar amb
  `SecurityException` de certificat. La instal·lació completa amb Shizuku sí que
  va establir els camps correctes. «Fonts desconegudes» sola tampoc ho resol.
- Conserva paquet i certificat en actualitzar. No desinstal·lis la PTrack habitual
  per resoldre un conflicte de signatura: es perdrien els viatges. No esborris dades.
- `cat.ptrack` és l'app habitual; `cat.ptrack.cartest` és una variant del mateix
  codi amb dades separades. No confonguis variant de compilació amb branca Git.
  El 7/11/2026 també s'ha actualitzat la normal amb KingInstaller i Shizuku i
  se n'han verificat l'origen i el servei. La prova física positiva és de
  `cartest`; queda pendent provar la normal al Peugeot.
- Verifica `installerPackageName=com.android.vending`,
  `initiatingPackageName=com.google.android.packageinstaller` i servei resoluble
  amb `scripts/android-auto-car-check.ps1 -PackageName <paquet>`.
  Aquests camps no substitueixen la prova al vehicle.
- Shizuku només es necessita durant la instal·lació. Es va aturar abans de la
  prova física positiva. No cal deixar-lo actiu per utilitzar Android Auto.

Registra cada canvi de procediment i resultat real a la guia i a VALIDATION.
No donis per confirmades lectures OBD o altres dispositius a partir d'aquesta prova.
