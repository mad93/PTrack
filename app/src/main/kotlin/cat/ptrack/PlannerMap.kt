package cat.ptrack

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point as MapPoint
import cat.ptrack.ElectricRoutePlanner.Plan
import cat.ptrack.ElectricRoutePlanner.Point

internal class PlannerMap(
    context: Context,
    private val selectRoute: (Int) -> Unit,
    private val selectStop: (Int) -> Unit,
    private val pickPoint: (Point) -> Unit
) : FrameLayout(context) {
    init { MapLibre.getInstance(context) }
    val mapView = MapView(context)
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var plans = emptyList<Plan>()
    private var selected = 0
    private var endpoints = emptyList<Point>()
    private var picking = false

    init {
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mapView.onCreate(null)
        mapView.getMapAsync { ready ->
            map = ready
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isTiltGesturesEnabled = false
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(40.3, -3.7), 5.0))
            ready.addOnMapClickListener { position -> click(ready, position) }
            ready.addOnMapLongClickListener { position ->
                pickPoint(Point(position.latitude, position.longitude))
                true
            }
            var fallback = false
            mapView.addOnDidFailLoadingMapListener {
                if (!fallback && style == null) {
                    fallback = true
                    mapView.post {
                        ready.setStyle(Style.Builder().fromJson(
                            """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#dfe9e3"}}]}"""
                        ), ::setup)
                    }
                }
            }
            ready.setStyle("https://tiles.openfreemap.org/styles/liberty", ::setup)
        }
    }

    fun update(routes: List<Plan>, selection: Int, points: List<Point>, fit: Boolean) {
        plans = routes
        selected = selection
        endpoints = points
        draw()
        if (fit) fitRoutes()
    }

    fun setPicking(enabled: Boolean) {
        picking = enabled
    }

    private fun setup(loaded: Style) {
        style = loaded
        for (id in listOf("other-routes", "selected-route", "stops", "endpoints")) {
            loaded.addSource(GeoJsonSource(id))
        }
        for ((id, color, width) in listOf(
            Triple("other-routes", 0xFF697995.toInt(), 5f),
            Triple("selected-route", 0xFF155BD7.toInt(), 6f)
        )) {
            loaded.addLayer(LineLayer(id, id).withProperties(
                lineColor(color), lineWidth(width), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        }
        loaded.addLayer(CircleLayer("stops", "stops").withProperties(
            circleColor(0xFF007D59.toInt()), circleRadius(8f), circleStrokeColor(Color.WHITE), circleStrokeWidth(2f)))
        loaded.addLayer(CircleLayer("endpoints", "endpoints").withProperties(
            circleColor(0xFF153B33.toInt()), circleRadius(7f), circleStrokeColor(Color.WHITE), circleStrokeWidth(2f)))
        draw()
        fitRoutes()
    }

    private fun draw() {
        val loaded = style ?: return
        fun features(id: String, items: List<Feature>) {
            loaded.getSourceAs<GeoJsonSource>(id)?.setGeoJson(FeatureCollection.fromFeatures(items))
        }
        val routes = plans.mapIndexedNotNull { index, plan ->
            val shape = plan.legs.flatMap { it.shape }.map { it.geo() }
            if (shape.size < 2) null else Feature.fromGeometry(LineString.fromLngLats(shape)).apply {
                addNumberProperty("index", index)
            }
        }
        features("other-routes", routes.filter { it.getNumberProperty("index").toInt() != selected })
        features("selected-route", routes.filter { it.getNumberProperty("index").toInt() == selected })
        features("stops", plans.getOrNull(selected)?.charges.orEmpty().mapIndexed { index, charge ->
            Feature.fromGeometry(charge.stop.point.geo()).apply { addNumberProperty("index", index) }
        })
        features("endpoints", endpoints.map { Feature.fromGeometry(it.geo()) })
    }

    private fun click(ready: MapLibreMap, position: LatLng): Boolean {
        if (picking) {
            pickPoint(Point(position.latitude, position.longitude))
            return true
        }
        val point = ready.projection.toScreenLocation(position)
        val radius = 9 * resources.displayMetrics.density
        val hit = RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius)
        ready.queryRenderedFeatures(hit, "stops").firstOrNull()?.let {
            selectStop(it.getNumberProperty("index").toInt())
            return true
        }
        if (ready.queryRenderedFeatures(hit, "selected-route").isNotEmpty()) return true
        ready.queryRenderedFeatures(hit, "other-routes").firstOrNull()?.let {
            selectRoute(it.getNumberProperty("index").toInt())
            return true
        }
        return false
    }

    private fun fitRoutes() {
        val points = (endpoints + plans.flatMap { plan -> plan.legs.flatMap { it.shape } }).distinct()
        if (points.isEmpty()) return
        mapView.post {
            val ready = map ?: return@post
            if (!isAttachedToWindow || mapView.width == 0) return@post
            val padding = (32 * resources.displayMetrics.density).toInt()
            if (points.size == 1) {
                val point = points.single()
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), 12.0))
            } else {
                val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
                ready.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            }
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(event)
    }

    private fun Point.geo() = MapPoint.fromLngLat(lon, lat)
}
