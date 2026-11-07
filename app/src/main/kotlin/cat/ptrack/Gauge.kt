package cat.ptrack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.*

class Gauge @JvmOverloads constructor(context: Context, private val label: String = "", private val unit: String = "",
    private val min: Double = -80.0, private val max: Double = 120.0) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val labels = Labels(context)
    private var reading: Double? = null
    fun value(value: Double?) {
        reading = value
        contentDescription = label + " " + (value?.let { Labels(context).n(it, 1) } ?: "—") + " " + unit
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); val r = min(w * .40f, h * .43f); val cx = w / 2; val cy = h * .51f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .12f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFF25443D.toInt(); canvas.drawArc(oval, 150f, 240f, false, paint)
        if (min < 0) { paint.color = 0xFF319F9F.toInt(); canvas.drawArc(oval, 150f, (240 * -min / (max - min)).toFloat(), false, paint) }
        reading?.let { value ->
            val v = max(min, min(max, value))
            val zero = (150 + 240 * (max(0.0, min) - min) / (max - min)).toFloat()
            val angle = (150 + 240 * (v - min) / (max - min)).toFloat()
            paint.color = if (value < 0) 0xFF65DEDA.toInt() else 0xFFBCF46A.toInt()
            canvas.drawArc(oval, min(zero, angle), max(.5f, abs(angle - zero)), false, paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.color = 0xFFF0F6ED.toInt(); paint.textSize = r * .47f
        canvas.drawText(reading?.let { labels.n(it, 1) } ?: "—", cx, cy + 9, paint)
        paint.textSize = r * .17f; paint.color = 0xFFA8BFB5.toInt(); canvas.drawText(unit, cx, cy + r * .40f, paint)
        paint.textSize = r * .18f; canvas.drawText(label, cx, cy + r * .78f, paint)
        paint.textSize = r * .13f
        canvas.drawText(min.toInt().toString(), cx - r * .83f, cy + r * .64f, paint)
        canvas.drawText(max.toInt().toString(), cx + r * .83f, cy + r * .64f, paint)
    }
}
