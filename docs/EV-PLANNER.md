# Pla unificat de rutes elèctriques i navegació

PTrack calcula i desa l'itinerari. Waze o Google Maps guien fins al punt següent. PTrack manté les parades, la previsió de bateria i els objectius de càrrega com a referència quan el navegador escull un altre recorregut.

## Hora de sortida i corba de càrrega DC

- A **Opcions de viatge → Sortida**, PTrack fa servir l'hora actual en calcular o permet triar data i hora. Per a cada carregador candidat consulta la temperatura horària d'[Open-Meteo](https://open-meteo.com/en/docs) a l'hora estimada d'arribada, incloses les parades prèvies. La petició envia les coordenades dels carregadors candidats. La ruta es pot calcular sense previsió.
- Amb l'OBD connectat i el registre actiu, PTrack separa la càrrega externa del viatge quan el cotxe va a 1 km/h o menys, rep més de 0,5 kW i detecta el cable. Identifica DC quan la lectura de tensió AC del carregador de bord és de 0–5 V. Desa el SOC, la potència entrant, la temperatura mitjana de la bateria i la temperatura exterior en una taula local separada. Les exportacions de viatges no inclouen aquesta taula.
- En cada tram de 5 punts de SOC, PTrack estima la temperatura de la bateria a partir de les diferències observades entre bateria i ambient. Pren la mediana de potència quan té almenys dues sessions comparables i aplica el límit del carregador. Per als trams sense prou dades o sense previsió aplica la corba de referència. La pantalla mostra la temperatura prevista i si ha pogut aplicar algun tram de la corba apresa.

## Planificador visual al telèfon

- La pestanya **Planificador** mostra l'origen, el destí i les opcions de viatge sobre una pantalla amb mapa. Cada camp té un desplegable que cerca carrers, adreces, pobles i topònims amb Photon. L'acció **Cerca a Catalunya (ICGC)** consulta el geocodificador de l'Institut Cartogràfic i Geològic de Catalunya amb el text introduït. En seleccionar un resultat, el camp en mostra el nom i PTrack fa servir les coordenades per calcular la ruta. També es pot seleccionar un punt al mapa, introduir coordenades o posar la ubicació actual com a origen. La bateria de sortida és editable i es preomple amb el SOC OBD real i recent, o amb el 80% si no està disponible. [Geocodificador ICGC](https://www.icgc.cat/ca/Eines-i-visors/Eines/Geocodificador-ICGC)
- MapLibre mostra la cartografia d'OpenFreeMap. La cartografia i la cerca usen connexió, mentre que el càlcul de rutes usa el graf de Valhalla importat. PTrack pot dibuixar les línies i les parades sobre un fons senzill si falla la càrrega de l'estil. La descàrrega de regions per a la cartografia visual queda pendent. [OpenFreeMap](https://openfreemap.org/quick_start/) · [Photon](https://github.com/komoot/photon)
- Valhalla pot retornar fins a dues alternatives addicionals. PTrack comprova l'energia i dibuixa els trams realment calculats. La llista i el mapa permeten seleccionar la proposta. El nombre d'opcions depèn del graf, del consum previst i dels carregadors disponibles.
- Sobre l'esborrany es pot saltar una parada, excloure-la de la cerca d'alternatives o desfer el canvi. Si saltar-la només funciona per sota de la reserva triada, PTrack demana acceptació i conserva almenys el 5% previst al punt següent en aquesta excepció. La tria final entre **Google Maps** i **Waze** desa l'itinerari i obre el primer punt pendent. El recàlcul des de la posició i la bateria actuals durant la navegació activa queda pendent.

## 1. Catàleg i font de preus

- **Implementat.** Catàleg del NAP amb descàrrega inicial, setmanal i manual. Desa ubicacions, connectors i potències en SQLite amb substitució transaccional.
- **Pendent.** Verificar el recurs successor DATEX II 3.7 en producció i la migració entre publicacions. Accedir a l'API oficial de REVE si els preus continuen absents del NAP. Confirmar l'accés, el límit de peticions i el camp que identifica la tarifa pública sense subscripció abans d'integrar-lo. Associar tarifes als connectors per identificadors verificables i desar-ne la vigència. Els preus desconeguts no entren a la comparació de cost. [NAP de la DGT](https://nap.dgt.es/es/dataset/puntos-de-recarga-electrica-para-vehiculos) · [API oficial de REVE](https://www.mapareve.es/api-contacto)

## 2. Ruta, energia i parades

- **Càlcul local.** PTrack calcula les rutes amb **Valhalla Mobile 0.6.4** i un mapa importat. Utilitza el SOC OBD si és recent i real. Calcula el consum amb les franges de velocitat i pendent apreses dels viatges. Per als trams sense prou dades aplica un model de referència segons el ritme previst i el desnivell, calibrat amb l'historial disponible. [API Android de Valhalla Mobile](https://rallista.github.io/valhalla-mobile/)
- **Capacitat útil.** PTrack parteix de 46 kWh. Per estimar-la fa servir la caiguda d'energia i de percentatge de bateria en viatges reals completats amb almenys 10 punts de descàrrega. Prioritza l'energia del BMS i pot integrar tensió i corrent quan les mostres cobreixen almenys el 95% del temps. Calcula la mediana de les estimacions recents coherents quan n'hi ha almenys tres. La capacitat utilitzada es mostra al resultat.
- **Recàrrega.** El planificador filtra els punts CCS per potència mínima i calcula el temps de càrrega aproximat amb la potència publicada de cada punt i una reducció de potència per damunt del 80%. Prova diverses quantitats de càrrega per complir la reserva. Hi ha un mode de temps total i un de minuts de càrrega.
- **Validat al Pixel.** El mapa peninsular amb Ceuta i Melilla s'ha importat i ha permès calcular Barcelona–Madrid amb quatre recàrregues proposades automàticament en uns 12 s. [Resultats de la prova](VALIDATION.md)
- **Pendent.** Definir com es distribueixen i s'actualitzen els mapes. Comparar més d'un carregador per sector en el mateix càlcul. La matriu de Valhalla Mobile pot ajudar a triar quins trams cal calcular en detall. Presentar opcions amb les diferències de minuts i d'euros. Afegir la tarifa pública al càlcul de la sessió, incloent IVA i recàrrecs aplicables, i habilitar **Pagar menys** amb independència de la durada. Calibrar la corba de càrrega i el marge d'energia amb sessions i viatges reals del Peugeot. [Operacions disponibles](https://github.com/Rallista/valhalla-mobile/blob/0.6.4/README.md#supported-actions)

### Paquets de rutes locals

**Planificador → Importa un mapa de rutes** accepta un ZIP amb aquests fitxers:

- `manifest.json` amb `version` igual a 1 i `origin` i `destination` com a parells `[latitud,longitud]` d'una ruta de comprovació dins del mapa.
- `valhalla_tiles.tar`, l'extracte del graf creat amb `valhalla_build_extract` a partir de tessel·les de carretera compatibles amb Valhalla 3.9.1.
- Opcionalment, `elevation/N35/N35W006.hgt.gz` i altres fitxers Skadi equivalents. S'admeten les extensions `.hgt`, `.hgt.gz` i `.hgt.lz4`. Quan hi ha elevació, la ruta de comprovació ha de retornar mostres vàlides.

PTrack comprova la ruta abans d'activar el paquet nou. L'actualització del mapa i la del catàleg de recàrrega són independents. Per regenerar un paquet cal preparar l'extracte OSM, configurar `mjolnir.tile_dir`, `mjolnir.tile_extract` i el directori d'elevació, executar `valhalla_build_tiles` i `valhalla_build_extract` i posar els fitxers resultants al ZIP amb l'estructura anterior. [Eines oficials de Valhalla](https://valhalla.github.io/valhalla/README_python/)

El paquet pilot de Ceuta usa l'extracte de Geofabrik del 3/11/2026. Dades viàries © contribuïdors d'OpenStreetMap, ODbL 1.0. L'elevació prové de Mapzen Terrain Tiles via AWS Open Data. Les coordenades de comprovació són `35.8895,-5.3190` i `35.9143,-5.3688`. [Extracte de Ceuta](https://download.geofabrik.de/europe/spain/ceuta.html) · [Atribució d'OpenStreetMap](https://www.openstreetmap.org/copyright) · [Atribució de l'elevació](https://github.com/tilezen/joerd/blob/master/docs/attribution.md)

`PTrack-Mapa-Espanya-sense-illes.zip` cobreix l'Espanya peninsular, Ceuta i Melilla. Usa l'extracte de Geofabrik del 5/11/2026 i elevacions de Mapzen. El ZIP ocupa 1,77 GiB i el paquet actiu, 2,80 GiB. Si es copia el ZIP a Baixades abans d'importar-lo, cal preveure uns 4,57 GiB per als dos fitxers durant la importació, més l'espai de qualsevol mapa anterior. PTrack llegeix el graf importat i el catàleg de carregadors des del telèfon per calcular la ruta. [Extracte d'Espanya](https://download.geofabrik.de/europe/spain.html) · [Resultats de validació](VALIDATION.md)

## 3. Itinerari editable i navegadors

- **Implementat al codi.** Es pot activar la ruta calculada com a itinerari per trams. Els botons **Obre amb Waze** i **Obre amb Google Maps** obren el punt pendent i es pot previsualitzar la llista de parades a Maps quan l'enllaç hi cap. Les parades pendents es poden editar manualment. El progrés només avança quan l'usuari ho confirma.
- **Pendent.** Desar al costat de l'itinerari les estimacions de bateria, preu i temps de cada parada perquè es puguin consultar durant el viatge. Quan es fixa, es treu o es canvia una parada, recalcular els trams pendents amb la bateria actual i mostrar si el nou itinerari és viable. Mostrar alternatives per potència, preu i serveis publicats. Provar els dos botons i la previsualització amb Waze i Maps reals al Pixel. Validar al DHU i al Peugeot l'acció estacionada **Navega al pròxim punt** d'Android Auto.

## Criteri de finalització

Una ruta real ha de passar per tot el flux, des de l'origen i la bateria inicial fins a l'últim tram al navegador, amb possibilitat de canviar una parada i recalcular. Cal verificar trajectes directes, amb diverses recàrregues, amb poca oferta i sense ruta viable. Per validar **Pagar menys**, comparar el cost calculat amb tarifes oficials vigents i amb el cost final de sessions reals. Els resultats es registren a [VALIDATION.md](VALIDATION.md).
