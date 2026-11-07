package cat.ptrack

import java.io.InputStream
import java.io.OutputStream

object Streams {
    fun bytes(input: InputStream): ByteArray = input.readBytes()
    fun copy(input: InputStream, output: OutputStream) { input.copyTo(output, 16_384) }
}
