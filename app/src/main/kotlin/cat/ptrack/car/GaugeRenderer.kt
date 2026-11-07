package cat.ptrack.car

import android.graphics.*
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import cat.ptrack.*
import java.util.Locale
import kotlin.math.*

/** Image content for the grid, not a freely projected Android surface. */
internal object GaugeRenderer {
    fun number(value: Double, places: Int) = if (value.isFinite()) String.format(Locale.forLanguageTag("ca"), "%.${places}f", value) else "--"
    fun icon(kind: Int, v: VehicleTelemetry.Snapshot, now: Long): CarIcon {
        val s = v.current(now); val t = v.totals?.summary
        val value = if (s == null) Double.NaN else when (kind) {
            0 -> s.power()
            1 -> s.soc
            2 -> s.current
            else -> s.speed
        }
        val negative = if (kind == 0) GaugeScale.limit(100.0, t?.maxIncomingKw ?: 0.0, 25.0) else GaugeScale.limit(200.0, t?.maxIncomingA ?: 0.0, 50.0)
        val positive = if (kind == 0) GaugeScale.limit(150.0, t?.maxDischargeKw ?: 0.0, 25.0) else GaugeScale.limit(300.0, t?.maxDischargeA ?: 0.0, 50.0)
        val bitmap = androidx.core.graphics.createBitmap(256, 256).apply { density = Bitmap.DENSITY_NONE }
        val c = Canvas(bitmap); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Transparent gutter prevents adjacent large semicircles from touching.
        c.scale(.88f, .88f, 128f, 128f)
        val arc = RectF(10f, 10f, 246f, 246f)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 12f
        p.color = 0xFF565B61.toInt(); c.drawArc(arc, 180f, 180f, false, p)
        val color = when {
            kind == 1 -> 0xFF63B8FF.toInt()
            kind == 3 -> 0xFF00E5FF.toInt()
            value < 0 -> 0xFF43DEB0.toInt()
            else -> 0xFFFFAD65.toInt()
        }
        if (kind == 0 || kind == 2) {
            p.strokeWidth = 4f; p.color = 0xFF43DEB0.toInt(); c.drawArc(arc, 180f, 90f, false, p)
            p.color = 0xFFFFAD65.toInt(); c.drawArc(arc, 270f, 90f, false, p)
        }
        val angle = when (kind) {
            1 -> GaugeScale.battery(value)
            3 -> GaugeScale.speed(value, 150.0)
            else -> GaugeScale.angle(value, negative, positive)
        }
        if (angle.isFinite()) {
            p.strokeWidth = 13f; p.color = color
            val startAngle = if (kind == 1 || kind == 3) 180.0 else min(270.0, angle)
            val sweepAngle = if (kind == 1 || kind == 3) angle - 180.0 else abs(angle - 270.0)
            c.drawArc(arc, startAngle.toFloat(), sweepAngle.toFloat(), false, p)
            val radians = Math.toRadians(angle); p.style = Paint.Style.FILL
            c.drawCircle(128f + (cos(radians) * 118).toFloat(), 128f + (sin(radians) * 118).toFloat(), 7f, p)
        }
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        // The AA template stays dark even when map/day mode is enabled.
        p.color = 0xFFF5F5F5.toInt(); p.textSize = 112f
        var text = number(value, if (kind == 1 || kind == 3 || abs(value) >= 10) 0 else 1)
        if (value.isFinite() && value > 0 && (kind == 0 || kind == 2)) text = "+$text"
        while (p.measureText(text) > 232) p.textSize -= 2
        c.drawText(text, 128f, if (kind == 1) 148f else 161f, p)
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL); p.textSize = 40f; p.color = 0xFFBFC3C8.toInt()
        c.drawText(when (kind) { 0 -> "kW"; 1 -> "%"; 2 -> "A"; else -> "km/h" }, 128f, if (kind == 1) 191f else 217f, p)
        if (kind == 1) { p.textSize = 40f; p.color = color; c.drawText(number(v.remaining(now), 1) + " kWh", 128f, 245f, p) }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
