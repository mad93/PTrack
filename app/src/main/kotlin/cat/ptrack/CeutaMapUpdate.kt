package cat.ptrack

import android.content.Context
import android.system.Os
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal object CeutaMapUpdate {
    private const val PILOT_PACKAGE = "cat.ptrack.cartest"
    private const val ASSETS = "ceuta-builder"
    private const val LOADER = "libceuta_loader.so"
    private const val BUILDER = "libceuta_builder.so"
    private const val ROADS = "https://download.geofabrik.de/europe/spain/ceuta-latest.osm.pbf"
    private const val HEIGHTS = "https://elevation-tiles-prod.s3.amazonaws.com/skadi/N35/N35W006.hgt.gz"

    fun available(context: Context) = context.packageName == PILOT_PACKAGE &&
        File(context.applicationInfo.nativeLibraryDir, LOADER).canExecute()

    fun update(context: Context) {
        check(available(context))
        OfflineValhalla.install(context) { map ->
            val work = File(map, "build")
            val tiles = File(work, "tiles")
            check(tiles.mkdirs())
            val elevation = File(map, OfflineMapArchive.ELEVATION)
            val height = File(elevation, "N35/N35W006.hgt.gz")
            check(height.parentFile!!.mkdirs())
            val pbf = File(work, "ceuta.osm.pbf")
            download(ROADS, pbf, 4 * 1024 * 1024)
            download(HEIGHTS, height, 20 * 1024 * 1024)

            val native = File(context.applicationInfo.nativeLibraryDir)
            val libraries = File(work, "libraries")
            check(libraries.mkdir())
            val names = JSONObject(context.assets.open("$ASSETS/files.json").bufferedReader().use { it.readText() })
            for (alias in names.keys()) {
                Os.symlink(File(native, names.getString(alias)).absolutePath, File(libraries, alias).absolutePath)
            }

            val config = JSONObject(context.assets.open("$ASSETS/valhalla.json").bufferedReader().use { it.readText() })
            val graph = File(map, OfflineMapArchive.GRAPH)
            config.getJSONObject("mjolnir").apply {
                put("tile_dir", tiles.absolutePath)
                put("tile_extract", graph.absolutePath)
            }
            config.getJSONObject("additional_data").put("elevation", elevation.absolutePath)
            val configFile = File(work, "valhalla.json").apply { writeText(config.toString()) }
            run(work, File(native, LOADER).absolutePath, "--library-path", libraries.absolutePath,
                File(native, BUILDER).absolutePath, "-c", configFile.absolutePath, "-j", "2", pbf.absolutePath)

            val levels = tiles.listFiles().orEmpty().filter { it.isDirectory && it.name in listOf("0", "1", "2") }
            check(levels.isNotEmpty()) { "No graph tiles were built" }
            run(work, "/system/bin/tar", "-cf", graph.absolutePath, "-C", tiles.absolutePath,
                *levels.map { it.name }.sorted().toTypedArray())
            File(map, OfflineMapArchive.MANIFEST).writeText(
                """{"version":1,"origin":[35.8895,-5.3190],"destination":[35.9143,-5.3688]}"""
            )
            // Delete the symlinks before recursing over the temporary build output.
            libraries.listFiles().orEmpty().forEach { check(it.delete()) }
            check(work.deleteRecursively())
        }
    }

    private fun download(address: String, file: File, limit: Int) {
        val connection = URL(address).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} for ${connection.url.host}" }
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        check(bytes <= limit) { "Ceuta download exceeded its size limit" }
                        output.write(buffer, 0, count)
                    }
                    check(bytes > 0) { "Empty Ceuta download" }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun run(work: File, vararg command: String) {
        val log = File(work, "builder.log")
        val process = ProcessBuilder(*command).directory(work)
            .redirectErrorStream(true).redirectOutput(log).start()
        try {
            check(process.waitFor(90, TimeUnit.SECONDS)) { "Ceuta map builder timed out" }
            if (process.exitValue() != 0) {
                Log.w("PTrackPlanner", log.readText().takeLast(2500))
                error("Ceuta map builder exited with code ${process.exitValue()}")
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
