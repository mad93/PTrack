# Third-party notices

## Kotlin standard library

PTrack uses the Kotlin standard library, copyright JetBrains s.r.o. and Kotlin contributors, under the Apache License 2.0. Source and license: [JetBrains/Kotlin](https://github.com/JetBrains/kotlin) and [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt).

## Stellantis e-CMP car profile

`app/src/main/assets/stellantis-ecmp.json` is an unmodified copy of the v4 profile from Esco EV OBD Scanner, commit `722f12c203a1ff1ced0fbcff01316c5a67d22ad2`.

Esco EV OBD Scanner car files by **Esa Pelttari**, licensed under [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).

Source: https://github.com/EsaHub/EscoEvObdScanner

The profile contains its own source attributions for individual identifiers. These are retained. PTrack does not imply endorsement by Esa Pelttari, Peugeot, Stellantis, or the cited projects.

## Reference implementation

The Esco EV OBD Scanner implementation was consulted for ELM327 setup, ECU targeting and automatic-format reply framing. Its MIT license and copyright notice are included in `app/src/main/assets/ESCO-LICENSE.txt` and are accessible in app settings. The Esco name and logo are not reused as PTrack branding.

## Valhalla Mobile

PTrack uses [Valhalla Mobile 0.6.4](https://github.com/Rallista/valhalla-mobile/tree/0.6.4) and its Valhalla engine for offline routing. Their MIT notice is included in `app/src/main/assets/VALHALLA-MOBILE-LICENSE.txt`. Road and elevation data have separate terms, documented with the [offline map format](docs/EV-PLANNER.md#paquets-de-rutes-locals).
