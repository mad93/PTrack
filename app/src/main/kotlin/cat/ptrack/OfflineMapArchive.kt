package cat.ptrack

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Extracts the v1 offline map format into an empty, app-private staging directory. */
internal object OfflineMapArchive {
    const val GRAPH = "valhalla_tiles.tar"
    const val MANIFEST = "manifest.json"
    const val ELEVATION = "elevation"
    private val heightFile = Regex("""elevation/([NS]\d{2})/\1[EW]\d{3}\.hgt(?:\.gz|\.lz4)?""")
    private const val MAX_BYTES = 12L * 1024 * 1024 * 1024

    fun extract(input: InputStream, directory: File) {
        val entries = mutableSetOf<String>()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(entries.add(name) && entries.size <= 2000) { "Duplicate or excess map entries" }
                if (entry.isDirectory) {
                    require(name == "elevation/" || Regex("elevation/[NS]\\d{2}/").matches(name))
                    require(zip.read() == -1) { "Unexpected data in map directory" }
                    continue
                }
                require(name == MANIFEST || name == GRAPH || heightFile.matches(name)) { "Unknown map entry" }
                val output = File(directory, name)
                output.parentFile?.mkdirs()
                output.outputStream().buffered().use { stream ->
                    var bytes = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count == -1) break
                        bytes += count
                        total += count
                        require(total <= MAX_BYTES && (name != MANIFEST || bytes <= 8192)) { "Map archive too large" }
                        stream.write(buffer, 0, count)
                    }
                    require(bytes > 0) { "Empty map entry" }
                }
            }
        }
        require(MANIFEST in entries && GRAPH in entries) { "Incomplete offline map" }
    }
}
