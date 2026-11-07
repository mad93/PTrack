package cat.ptrack

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/** ELM automatic CAN formatting (ATH0 / CAF1); no CAN broadcast or write services. */
class ElmProtocol(private val input: InputStream, private val output: OutputStream) {
    private var target = ""
    fun exchange(command: String): String {
        if (!command.matches(Regex("AT[A-Z0-9 ]+|22[0-9A-F]{4}"))) throw IOException("Read-only command rejected")
        output.write((command + "\r").toByteArray(Charsets.US_ASCII)); output.flush()
        val deadline = System.nanoTime() + 4_000_000_000L
        val text = StringBuilder()
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted) throw IOException("Cancelled")
            if (input.available() == 0) {
                try { Thread.sleep(10) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException(e) }
                continue
            }
            val b = input.read()
            if (b < 0) throw IOException("Disconnected")
            if (b == '>'.code) return text.toString()
            text.append(b.toChar())
            if (text.length > 32768) throw IOException("ELM reply too large")
        }
        throw IOException("ELM prompt timeout")
    }
    private fun expect(command: String) {
        if (!exchange(command).trim().matches(Regex("(?s).*\\bOK\\b.*"))) throw IOException("ELM rejected $command")
    }
    fun initialize() {
        exchange("ATZ"); expect("ATE0"); expect("ATL0"); expect("ATS0"); expect("ATH0")
        expect("ATCAF1"); expect("ATSP6"); expect("ATST64")
    }
    fun read(request: String, response: String, did: String): String {
        if (target != request) {
            expect("ATSH$request"); expect("ATCRA$response")
            exchange("ATFCSH$request"); exchange("ATFCSD300000"); exchange("ATFCSM1")
            target = request
        }
        val raw = exchange("22$did")
        if (raw.uppercase(Locale.ROOT).contains("ELM327") || raw.uppercase(Locale.ROOT).contains("STN")) throw IOException("ELM reset")
        for (line in raw.split(Regex("[\\r\\n]+"))) if (line.trim().equals("22$did", true)) throw IOException("ELM echo returned: reset suspected")
        return raw
    }
    companion object {
        fun payload(raw: String, did: String): ByteArray? {
            val data = StringBuilder()
            var numbered = false; var sequence = 0; var declared = -1
            for (original in raw.uppercase(Locale.ROOT).split(Regex("[\\r\\n]+"))) {
                val line = original.replace(" ", "").trim()
                if (line.matches(Regex("[0-9A-F]+:[0-9A-F]+"))) {
                    val colon = line.indexOf(':'); val n = line.substring(0, colon).toInt(16)
                    if (n != sequence++ % 16 || line.substring(colon + 1).length % 2 != 0) return null
                    numbered = true; data.append(line.substring(colon + 1))
                } else if (!numbered && line.matches(Regex("[0-9A-F]{1,3}"))) declared = line.toInt(16)
                else if (!numbered && line.startsWith("62$did")) data.append(line)
            }
            var hex = data.toString()
            if (numbered) {
                if (declared < 3 || hex.length < declared * 2) return null
                hex = hex.substring(0, declared * 2)
            }
            if (!hex.startsWith("62$did") || hex.length % 2 != 0) return null
            return try { (6 until hex.length step 2).map { hex.substring(it, it + 2).toInt(16).toByte() }.toByteArray() }
            catch (_: NumberFormatException) { null }
        }
        fun unsigned(data: ByteArray?, offset: Int, length: Int): Double {
            if (data == null || offset < 0 || length !in 1..4 || offset + length > data.size) return Double.NaN
            var value = 0L
            for (i in offset until offset + length) value = (value shl 8) or (data[i].toLong() and 255)
            return value.toDouble()
        }
    }
}
