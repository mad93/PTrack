package cat.ptrack

import android.content.Context
import android.util.AtomicFile
import cat.ptrack.ElectricRoutePlanner.Point
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

internal object OfflineValhalla {
    private val mapName = Regex("map-[0-9a-f-]{36}")

    private fun root(context: Context) = File(context.filesDir, "offline-routing")
    private fun active(context: Context) = AtomicFile(File(root(context), "active"))
    fun installed(context: Context) = active(context).baseFile.isFile

    @Synchronized
    fun install(context: Context, input: InputStream) {
        val directory = File(root(context), "map-${UUID.randomUUID()}")
        check(directory.mkdirs())
        var activated = false
        try {
            OfflineMapArchive.extract(input, directory)
            val manifest = JSONObject(File(directory, OfflineMapArchive.MANIFEST).readText())
            require(manifest.getInt("version") == 1) { "Unsupported offline map version" }
            val from = probe(manifest, "origin")
            val to = probe(manifest, "destination")
            require(from != to)
            open(context, directory).use { engine ->
                val route = JSONObject(engine.routeRaw(ValhallaPlanner.request(from, to)))
                val leg = route.getJSONObject("trip").getJSONArray("legs").getJSONObject(0)
                require(leg.getString("shape").isNotEmpty())
                if (File(directory, OfflineMapArchive.ELEVATION).isDirectory) {
                    val heights = leg.getJSONArray("elevation")
                    require((0 until heights.length()).any { heights.optDouble(it) in -500.0..9000.0 }) {
                        "Missing probe elevation"
                    }
                }
            }
            val pointer = active(context)
            val output = pointer.startWrite()
            try {
                output.write(directory.name.toByteArray(Charsets.UTF_8))
                pointer.finishWrite(output)
            } catch (error: Exception) {
                pointer.failWrite(output)
                throw error
            }
            // AtomicFile logs some failures internally. Verify the activated pointer before cleaning older data.
            check(pointer.readFully().toString(Charsets.UTF_8) == directory.name)
            activated = true
            root(context).listFiles()?.filter { it != directory && mapName.matches(it.name) }?.forEach { it.deleteRecursively() }
        } finally {
            if (!activated) directory.deleteRecursively()
        }
    }

    @Synchronized
    fun <T> withEngine(context: Context, block: (Valhalla) -> T): T {
        val name = active(context).readFully().toString(Charsets.UTF_8)
        check(mapName.matches(name)) { "Invalid offline map pointer" }
        return open(context, File(root(context), name)).use(block)
    }

    private fun open(context: Context, directory: File): Valhalla {
        val graph = File(directory, OfflineMapArchive.GRAPH)
        require(graph.isFile) { "Offline road graph is missing" }
        val elevation = File(directory, OfflineMapArchive.ELEVATION)
        val config = ValhallaConfigFactory.usingTileExtract(graph.absolutePath)
        return Valhalla(context, if (elevation.isDirectory) config.copy(
            additionalData = config.additionalData?.copy(elevation = elevation.absolutePath)
                ?: error("Valhalla defaults lack elevation configuration")
        ) else config)
    }

    private fun probe(manifest: JSONObject, key: String): Point {
        val pair: JSONArray = manifest.getJSONArray(key)
        require(pair.length() == 2)
        return Point(pair.getDouble(0), pair.getDouble(1))
    }
}
