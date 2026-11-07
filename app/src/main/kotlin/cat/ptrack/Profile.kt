package cat.ptrack

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.pow

class Profile(context: Context) {
    data class Signal(val key: String, val request: String, val response: String, val did: String,
        val expression: String, val unit: String, val min: Double, val max: Double, val scale: Double = 1.0, val offset: Double = 0.0) {
        fun decode(raw: String): Double { return try {
            val bytes = ElmProtocol.payload(raw, did)
            if (bytes == null) Double.NaN else {
                val matcher = Pattern.compile("(S?)\\[B(\\d+):B(\\d+)\\]|(S?)B(\\d+)").matcher(expression)
                val replaced = StringBuffer()
                while (matcher.find()) {
                    val first = checkNotNull(matcher.group(5) ?: matcher.group(2)).toInt()
                    val last = if (matcher.group(5) != null) first else checkNotNull(matcher.group(3)).toInt()
                    val length = last - first + 1
                    var value = ElmProtocol.unsigned(bytes, first - 4, length)
                    if (!value.isFinite()) return Double.NaN
                    if ((matcher.group(1) == "S" || matcher.group(4) == "S") && value >= 2.0.pow(length * 8 - 1)) value -= 2.0.pow(length * 8)
                    matcher.appendReplacement(replaced, value.toString())
                }
                matcher.appendTail(replaced)
                val value = Arithmetic(replaced.toString()).parse() * scale + offset
                if (value.isFinite() && value in min..max) value else Double.NaN
            }
        } catch (_: Exception) { Double.NaN } }
    }
    data class RawSignal(val key: String, val request: String, val response: String, val did: String,
        val bytes: Int, val scale: Double, val offset: Double, val min: Double, val max: Double) {
        fun display(raw: String, labels: Labels): String {
            val data = ElmProtocol.payload(raw, did) ?: return "—"
            val text = StringBuilder()
            if (bytes == 0) return data.joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
            var i = 0
            while (i + bytes <= data.size) {
                val value = ElmProtocol.unsigned(data, i, bytes) * scale + offset
                if (!(value < min || value > max)) {
                    if (text.isNotEmpty()) text.append(" · ")
                    text.append(i / bytes + 1).append(": ").append(labels.n(value, if (bytes == 1) 0 else 3))
                }
                i += bytes
            }
            return text.toString().ifEmpty { "—" }
        }
    }
    val signals = linkedMapOf<String, Signal>()
    val rawSignals = mutableListOf<RawSignal>()
    init {
        val root = context.assets.open("stellantis-ecmp.json").use { JSONObject(Streams.bytes(it).toString(Charsets.UTF_8)) }
        val live = root.getJSONObject("live"); val ecus = root.getJSONArray("ecus")
        for (key in live.keys()) {
            val l = live.getJSONObject(key)
            for (i in 0 until ecus.length()) {
                val ecu = ecus.getJSONObject(i)
                if (ecu.getString("id") != l.getString("ecu")) continue
                val dids = ecu.getJSONArray("dids")
                for (j in 0 until dids.length()) {
                    val d = dids.getJSONObject(j)
                    if (!d.getString("did").equals(l.getString("did"), true)) continue
                    val expression = l.optString("expression", d.optString("expression", ""))
                    if (l.has("bytes") || expression.isEmpty()) {
                        rawSignals.add(RawSignal(key, hex(ecu.getString("request")), hex(ecu.getString("response")), hex(d.getString("did")),
                            l.optInt("bytes", 0), l.optDouble("scale", 1.0), l.optDouble("offset", 0.0),
                            l.optDouble("min", -Double.MAX_VALUE), l.optDouble("max", Double.MAX_VALUE)))
                    } else signals[key] = Signal(key, hex(ecu.getString("request")), hex(ecu.getString("response")), hex(d.getString("did")),
                        expression, d.optString("unit", ""), l.optDouble("min", -Double.MAX_VALUE), l.optDouble("max", Double.MAX_VALUE),
                        l.optDouble("scale", 1.0), l.optDouble("offset", 0.0))
                }
            }
        }
        for (key in listOf("current", "voltage", "soc", "speed")) check(signals.containsKey(key)) { "Missing $key" }
    }
    private fun hex(s: String) = s.replace("0x", "").uppercase(Locale.ROOT)

    /** Small arithmetic grammar, never executes profile text as code. */
    private class Arithmetic(text: String) {
        private val s = text.replace(" ", "")
        private var p = 0
        fun parse(): Double { val value = sum(); require(p == s.length); return value }
        private fun sum(): Double {
            var value = product()
            while (p < s.length && (s[p] == '+' || s[p] == '-')) {
                val op = s[p++]; val n = product(); value = if (op == '+') value + n else value - n
            }
            return value
        }
        private fun product(): Double {
            var value = number()
            while (p < s.length && (s[p] == '*' || s[p] == '/')) {
                val op = s[p++]; val n = number(); value = if (op == '*') value * n else value / n
            }
            return value
        }
        private fun number(): Double {
            if (p < s.length && s[p] == '-') { p++; return -number() }
            if (p < s.length && s[p] == '(') { p++; val value = sum(); require(p < s.length && s[p++] == ')'); return value }
            val start = p
            while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
            return s.substring(start, p).toDouble()
        }
    }
}
