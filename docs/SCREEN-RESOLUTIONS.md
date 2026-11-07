# Especificacions de Pantalla, Emulador i Vehicle (PTrack)

Aquest document resumeix les especificacions de resolució, mides d'element i comportament del disseny visual de PTrack a Android Auto, l'emulador DHU i el Peugeot e-2008 (2020).

---

## 1. Comportament i Proporcions a Pantalles de 800 × 480 px (7")

A pantalles estàndard de 800 × 480 px (com la de 7" del Peugeot e-2008), Android Auto utilitza la interfície **Coolwalk / Dashboard**, que permet 3 modes de visualització diferents:

> **Nota sobre l'espai real disponible**: Android Auto inclou una barra d'eines inferior o lateral (Taskbar) d'uns ~64 dp. L'àrea útil de contingut per a les apps és d'aproximadament **800 × 416 dp**.

| Mode de visualització | Proporció aproximada | Amplada útil PTrack | Disposició de PTrack (`PTrackCarScreen.kt`) |
| :--- | :--- | :--- | :--- |
| **1. Pantalla completa** | **100%** (1/1) | **~800 dp** | **1 Fila (4 rellotges en línia)**<br>Com que `width >= 600 dp`, utilitza `ITEM_SIZE_LARGE` (~160 px). |
| **2. Finestra Principal** *(Dashboard)* | **~67%** (2/3 de pantalla) | **~530 dp** | **2 Files (2 rellotges a dalt + 2 a baix)**<br>Com que `530 dp < 600 dp`, canvia a 2 files amb `ITEM_SIZE_MEDIUM` (~88 px). |
| **3. Finestra Secundària** *(Dashboard)* | **~33%** (1/3 de pantalla) | **~260 dp** | **Reservat pel host d'Android Auto**<br>Reservat per a targetes de reproductor de música, trucades o navegació. Les plantilles `SectionedItemTemplate` no s'executen a la zona secundària d'1/3. |

---

## 2. Indicadors Visuals de PTrack (4 Elements)

PTrack disposa de 4 indicadors en la graella principal:

1. **Potència (kW)**: Limitat dinàmicament segons els pics de regeneració i descàrrega.
2. **Bateria (SOC %)**: Percentatge de càrrega actual de la bateria i energia restant en kWh.
3. **Intensitat (A)**: Corrent de la bateria en ampers (entrada/sortida).
4. **Velocitat (km/h)**: Velocitat actual del vehicle en escala de 0 a 150 km/h.

---

## 3. Mides d'elements i l'API Car App Library (`PTrackCarScreen.kt`)

A Android Auto, les aplicacions no controlen els píxels exactes ni la posició percentual dels elements. Utilitzen plantilles predefinides (`SectionedItemTemplate`) on el **host d'Android Auto (el cotxe)** s'encarrega del layout final.

### Constants de mida de `GridSection` (Car App Library 1.7.0)

| Constant | Valor intern | Mida aproximada d'imatge al cotxe / DHU |
| :--- | :--- | :--- |
| **`GridSection.ITEM_SIZE_LARGE`** | `3` (Gran) | **~160 px** |
| **`GridSection.ITEM_SIZE_MEDIUM`** | `2` (Mitjana) | **~88 px** |
| **`GridSection.ITEM_SIZE_SMALL`** | `1` (Petita) | **~64 px** |

### Generació d'imatges (`GaugeRenderer.kt`)
* Cada rellotge/indicador es genera com un `Bitmap` quadrat de **256 × 256 px** amb fons transparent i un límit interior del 88% (`Canvas.scale(0.88f, 0.88f)`).
* El host d'Android Auto escala aquest bitmap segons la constant de mida indicada (`ITEM_SIZE_LARGE` o `ITEM_SIZE_MEDIUM`).

### Lògica de selecció de mida i disposició de PTrack
```kotlin
val singleRow = width >= 600
val size = if (singleRow || height >= 600) GridSection.ITEM_SIZE_LARGE else GridSection.ITEM_SIZE_MEDIUM
```
* **Disposició 1 Fila (4 rellotges en línia)**: Quan `width >= 600 dp`. Utilitza `ITEM_SIZE_LARGE` (~160 px).
* **Disposició 2 Files (2 rellotges a dalt + 2 a baix)**: Quan `width < 600 dp`. Utilitza `ITEM_SIZE_MEDIUM` (~88 px) si `height < 600 dp`.

---

## 4. Emulador Android Auto (Desktop Head Unit - DHU)

El projecte disposa de 3 perfils de prova d'emulador a `scripts/dhu/`:

| Perfil DHU | Fitxer `.ini` | Resolució en Píxels | Densitat | Comportament a PTrack |
| :--- | :--- | :--- | :--- | :--- |
| **Normal** | `scripts/dhu/ptrack.ini` | **800 × 480 px** | 160 DPI | Resolució estàndard de 7". Mostra 2 files simètriques (2+2 rellotges, `ITEM_SIZE_MEDIUM`). |
| **Wide** | `scripts/dhu/wide.ini` | **1280 × 720 px** | 160 DPI | Pantalla panoràmica (HD 720p). En pantalla ampla o dividida (amb ~770 dp útils) mostra **1 fila (4 rellotges en línia, `ITEM_SIZE_LARGE`)**. |
| **Narrow** | `scripts/dhu/narrow.ini` | **1280 × 720 px** | 160 DPI | Àrea lògica útil reduïda (400 × 720 dp). |

---

## 5. Especificacions del Peugeot e-2008 (Model 2020)

El Peugeot e-2008 (any 2020, plataforma e-CMP) equipa dues opcions de pantalla d'infotainment (sistemes RCC o NAC):

### A. Pantalla de 7 polzades (*Acabats Active / Allure*)
* **Resolució física real**: **800 × 480 px** (relació d'aspecte 16:9).
* **Finestra de projecció d'Android Auto**: **800 × 480 px** (ocupa el 100% de la pantalla).

### B. Pantalla de 10 polzades HD (*Acabats GT Line / GT*)
* **Resolució física total de la pantalla**: **1920 × 720 px** (relació ultra-ampla ~24:9).
* **Finestra de projecció real d'Android Auto**:
  * El sistema Peugeot (NAC Wave 3 / Wave 4) reserva les dues franges laterals d'uns ~320 px cadascuna per als controls fixos de la climatització.
  * La finestra central on es projecta Android Auto és de **1280 × 720 px** o **800 × 480 px**.
