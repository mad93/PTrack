package cat.ptrack

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

class Report(private val context: Context) {
    private val l = Labels(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun file(id: Long) = File(File(context.filesDir, "reports"), "ptrack-$id-${l.locale.language}.pdf")
    fun generate(store: Store, id: Long): File = synchronized(REPORT_LOCK) { generateLocked(store, id) }
    private fun generateLocked(store: Store, id: Long): File {
        val samples = store.samples(id); val stats = TripStats(); samples.forEach(stats::add)
        val output = file(id); val parent = checkNotNull(output.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Report directory")
        val temporary = File(output.path + ".tmp")
        val document = PdfDocument()
        try {
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            var c = page.canvas
            title(c, id, store.isDemo(id))
            text(c, l.t("INFORME DE VIATGE", "INFORME DE VIAJE", "TRIP REPORT"), 36, 103, 11, 0xFF56746D.toInt())
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, l.locale)
            text(c, if (stats.count > 0) date.format(Date(stats.start)) + " → " + date.format(Date(stats.end)) else "—", 36, 130, 11, Color.DKGRAY)
            val rows = listOf(
                l.t("Càrrega inicial / final", "Carga inicial / final", "Start / end charge") to (l.n(stats.startSoc, 1) + " % → " + l.n(stats.endSoc, 1) + " %"),
                l.t("Distància registrada", "Distancia registrada", "Recorded distance") to (l.n(stats.km, 2) + " km"),
                l.t("Durada total", "Duración total", "Total duration") to (l.n((stats.end - stats.start) / 60000.0, 1) + " min"),
                l.t("Velocitat mitjana registrada", "Velocidad media registrada", "Recorded average speed") to (l.n(stats.averageSpeed(), 1) + " km/h"),
                l.t("Consum mitjà net", "Consumo medio neto", "Net average consumption") to (l.n(stats.consumption(), 2) + " kWh/100 km"),
                l.t("Energia consumida / recuperada", "Energía consumida / recuperada", "Consumed / recovered energy") to (l.n(stats.consumed, 3) + " / " + l.n(stats.recovered, 3) + " kWh"),
                l.t("Pujada / baixada estimada (GPS)", "Ascenso / descenso estimado (GPS)", "Estimated ascent / descent (GPS)") to (if (stats.hasAltitude) l.n(stats.ascent, 0) + " / " + l.n(stats.descent, 0) + " m" else "—"),
                l.t("Cobertura de lectures", "Cobertura de lecturas", "Reading coverage") to (l.n(stats.coveredSeconds / 60, 1) + " min · " + stats.count + " " + l.t("mostres", "muestras", "samples")))
            var y = 169
            for ((label, value) in rows) { text(c, label, 36, y, 11, Color.DKGRAY); text(c, value, 340, y, 11, Color.BLACK); y += 31 }
            chart(c, samples, { it.soc }, l.signal("soc"), "%", 443, 150, 0.0, 100.0)
            lines(c, listOf(
                l.t("Vehicle", "Vehículo", "Vehicle") + " · " + store.tripVehicle(id),
                l.t("Potència = V × A / 1000. Signe +: consum; signe −: entrada a la bateria.", "Potencia = V × A / 1000. Signo +: consumo; signo −: entrada a batería.", "Power = V × A / 1000. Positive: discharge; negative: energy into battery."),
                l.t("Energia i distància: integració trapezoïdal de lectures OBD vàlides.", "Energía y distancia: integración trapezoidal de lecturas OBD válidas.", "Energy and distance: trapezoidal integration of valid OBD readings."),
                l.t("Els buits de més de 10 s no s’integren. La mitjana inclou les aturades registrades.", "Los huecos de más de 10 s no se integran. La media incluye paradas registradas.", "Gaps over 10 s are excluded. Average speed includes recorded stops."),
                l.t("Altitud GPS: precisió vertical ≤10 m; filtre de desnivell de 5 m. És una estimació.", "Altitud GPS: precisión vertical ≤10 m; filtro de desnivel de 5 m. Es una estimación.", "GPS altitude: vertical accuracy ≤10 m; 5 m elevation filter. This is an estimate."),
                l.t("— indica dada no disponible. Les dades del viatge es conserven al mòbil.", "— indica dato no disponible. Los datos del viaje se guardan en el móvil.", "— means unavailable. Trip data is retained on the phone.")), 36, 657)
            text(c, "Profile: Esa Pelttari · CC BY 4.0 · github.com/EsaHub/EscoEvObdScanner", 36, 780, 8, Color.GRAY)
            footer(c, 1); document.finishPage(page)
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create()); c = page.canvas
            title(c, id, store.isDemo(id))
            chart(c, samples, Sample::power, l.t("Potència de bateria", "Potencia de batería", "Battery power"), "kW", 100, 130, Double.NaN, Double.NaN)
            chart(c, samples, { it.current }, l.signal("current"), "A", 270, 130, Double.NaN, Double.NaN)
            chart(c, samples, { it.speed }, l.signal("speed"), "km/h", 440, 130, 0.0, Double.NaN)
            chart(c, samples, { it.altitude ?: Double.NaN }, l.t("Altitud GPS", "Altitud GPS", "GPS altitude"), "m", 610, 115, Double.NaN, Double.NaN)
            footer(c, 2); document.finishPage(page)
            FileOutputStream(temporary).use { document.writeTo(it); it.fd.sync() }
        } finally { document.close() }
        if (!temporary.renameTo(output)) throw IOException("Cannot finalize PDF")
        return output
    }
    private fun title(c: Canvas, id: Long, demo: Boolean) {
        text(c, "PTrack", 36, 52, 26, 0xFF123C33.toInt())
        text(c, "Peugeot e-2008 · #$id", 36, 76, 11, Color.DKGRAY)
        if (demo) text(c, l.t("SIMULACIÓ", "SIMULACIÓN", "SIMULATION"), 420, 52, 13, 0xFFAE4B20.toInt())
    }
    private fun footer(c: Canvas, page: Int) {
        text(c, "PTrack · " + l.t("Lectura OBD sense escriptures", "Lectura OBD sin escrituras", "Read-only OBD") + " · $page / 2", 36, 813, 9, Color.GRAY)
    }
    private fun lines(c: Canvas, lines: List<String>, x: Int, y: Int) {
        var yy = y
        for (line in lines) { text(c, line, x, yy, 9, Color.DKGRAY); yy += 19 }
    }
    private fun text(c: Canvas, text: String, x: Number, y: Number, size: Number, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size.toFloat()
        c.drawText(text, x.toFloat(), y.toFloat(), paint)
    }
    private fun chart(c: Canvas, samples: List<Sample>, value: (Sample) -> Double, name: String, unit: String,
        top: Int, height: Int, forcedMin: Double, forcedMax: Double) {
        text(c, "$name ($unit)", 36, top, 12, 0xFF123C33.toInt())
        val x = 76f; val y = (top + 18).toFloat(); val w = 475f; val h = (height - 42).toFloat()
        var min = Double.POSITIVE_INFINITY; var max = Double.NEGATIVE_INFINITY
        for (sample in samples) { val v = value(sample); if (v.isFinite()) { min = min(min, v); max = max(max, v) } }
        if (!min.isFinite()) { text(c, "—", x, y + 30, 13, Color.GRAY); return }
        min = if (forcedMin.isFinite()) forcedMin else min(0.0, min)
        max = if (forcedMax.isFinite()) forcedMax else max(0.0, max)
        if (max == min) max = min + 1
        for (i in 0..2) {
            val yy = y + h * i / 2
            paint.color = 0xFFDCE5E1.toInt(); paint.strokeWidth = .5f; c.drawLine(x, yy, x + w, yy, paint)
            text(c, l.n(max - (max - min) * i / 2, 1), 36, yy + 3, 8, Color.GRAY)
        }
        val first = samples.first().time; val duration = max(1L, samples.last().time - first)
        // Preserve extrema with at most two points per PDF pixel.
        val buckets = w.toInt()
        val low = DoubleArray(buckets + 1) { Double.POSITIVE_INFINITY }
        val high = DoubleArray(buckets + 1) { Double.NEGATIVE_INFINITY }
        for (sample in samples) {
            val v = value(sample); if (!v.isFinite()) continue
            val b = ((sample.time - first).toDouble() * buckets / duration).toInt()
            low[b] = min(low[b], v); high[b] = max(high[b], v)
        }
        paint.strokeWidth = 1.1f; paint.color = 0xFF147D69.toInt()
        var previousX: Float? = null; var previousY: Float? = null; var previousBucket = -1
        for (b in 0..buckets) if (low[b].isFinite()) {
            val xx = x + b * w / buckets
            val lo = (y + h - (low[b] - min) / (max - min) * h).toFloat()
            val hi = (y + h - (high[b] - min) / (max - min) * h).toFloat()
            c.drawLine(xx, lo, xx, hi, paint)
            if (previousX != null && (b - previousBucket).toDouble() * duration / buckets <= 10000) c.drawLine(previousX, checkNotNull(previousY), xx, lo, paint)
            previousX = xx; previousY = hi; previousBucket = b
        }
        text(c, "0 min", x, y + h + 15, 8, Color.GRAY)
        text(c, l.n(duration / 120000.0, 1) + " min", x + w / 2 - 12, y + h + 15, 8, Color.GRAY)
        text(c, l.n(duration / 60000.0, 1) + " min", x + w - 38, y + h + 15, 8, Color.GRAY)
    }
    companion object { private val REPORT_LOCK = Any() }
}
