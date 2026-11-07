package cat.ptrack

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfflineMapArchiveTests {
    @Test fun extractsGraphAndElevationWithTheirExpectedNames() {
        val directory = Files.createTempDirectory("ptrack-map").toFile()
        try {
            OfflineMapArchive.extract(zip("manifest.json", "valhalla_tiles.tar", "elevation/N35/N35W006.hgt.gz"), directory)
            assertEquals("test", directory.resolve("valhalla_tiles.tar").readText())
            assertTrue(directory.resolve("elevation/N35/N35W006.hgt.gz").isFile)
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectsTraversalAndArchivesWithoutGraph() {
        val directory = Files.createTempDirectory("ptrack-map").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                OfflineMapArchive.extract(zip("elevation/../../escape"), directory)
            }
            assertThrows(IllegalArgumentException::class.java) {
                OfflineMapArchive.extract(zip("manifest.json"), directory)
            }
            assertFalse(directory.resolve("valhalla_tiles.tar").exists())
        } finally { directory.deleteRecursively() }
    }

    private fun zip(vararg entries: String) = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { archive ->
            for (name in entries) {
                archive.putNextEntry(ZipEntry(name))
                archive.write("test".toByteArray())
                archive.closeEntry()
            }
        }
    }.toByteArray().inputStream()
}
