package cat.ptrack

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.roundToInt

internal class TripCalendar(context: Context, private val labels: Labels) : LinearLayout(context) {
    init { orientation = VERTICAL }

    fun render(
        month: YearMonth,
        annual: Boolean,
        counts: Map<LocalDate, Int>,
        navigate: (YearMonth, Boolean) -> Unit,
        select: (LocalDate) -> Unit
    ) {
        val title = if (annual) month.year.toString() else month.format(DateTimeFormatter.ofPattern("LLLL yyyy", labels.locale))
        val navigation = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        navigation.addView(button("‹") { navigate(if (annual) month.minusYears(1) else month.minusMonths(1), annual) }.apply {
            contentDescription = labels.t("Període anterior", "Periodo anterior", "Previous period")
        }, LayoutParams(dp(48), dp(48)))
        navigation.addView(button(title) {
            DatePickerDialog(context, { _, year, selectedMonth, _ ->
                navigate(YearMonth.of(year, selectedMonth + 1), annual)
            }, month.year, month.monthValue - 1, 1).show()
        }, LayoutParams(0, dp(48), 1f))
        navigation.addView(button("›") { navigate(if (annual) month.plusYears(1) else month.plusMonths(1), annual) }.apply {
            contentDescription = labels.t("Període següent", "Periodo siguiente", "Next period")
        }, LayoutParams(dp(48), dp(48)))
        addView(navigation)
        val mode = if (annual) labels.t("Vista mensual", "Vista mensual", "Month view")
            else labels.t("Vista anual", "Vista anual", "Year view")
        addView(button(mode) { navigate(month, !annual) })
        if (annual) renderYear(month, counts, navigate) else renderMonth(month, counts, select)
    }

    private fun renderYear(month: YearMonth, counts: Map<LocalDate, Int>, navigate: (YearMonth, Boolean) -> Unit) {
        val totals = IntArray(12)
        for ((day, count) in counts) totals[day.monthValue - 1] += count
        for (row in 0 until 4) {
            val line = LinearLayout(context)
            for (column in 0 until 3) {
                val index = row * 3 + column
                val target = month.withMonth(index + 1)
                val name = target.month.getDisplayName(TextStyle.SHORT_STANDALONE, labels.locale)
                line.addView(button("$name\n${totals[index]}") { navigate(target, false) }.apply {
                    contentDescription = "${target.month.getDisplayName(TextStyle.FULL_STANDALONE, labels.locale)}, ${tripCount(totals[index])}"
                }, LayoutParams(0, dp(64), 1f))
            }
            addView(line)
        }
    }

    private fun renderMonth(month: YearMonth, counts: Map<LocalDate, Int>, select: (LocalDate) -> Unit) {
        val first = WeekFields.of(labels.locale).firstDayOfWeek.value
        val highestCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(2)
        val weekdays = LinearLayout(context)
        for (column in 0 until 7) {
            val weekday = DayOfWeek.of((first - 1 + column) % 7 + 1)
            weekdays.addView(centered(weekday.getDisplayName(TextStyle.SHORT_STANDALONE, labels.locale)), LayoutParams(0, dp(32), 1f))
        }
        addView(weekdays)
        val offset = (month.atDay(1).dayOfWeek.value - first + 7) % 7
        val weeks = (offset + month.lengthOfMonth() + 6) / 7
        for (row in 0 until weeks) {
            val line = LinearLayout(context)
            for (column in 0 until 7) {
                val index = row * 7 + column - offset + 1
                val cell = FrameLayout(context)
                if (index in 1..month.lengthOfMonth()) {
                    val date = month.atDay(index)
                    val count = counts[date] ?: 0
                    val number = centered(index.toString()).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
                    val diameter = if (count == 0) 32 else 24 + (15.0 * (count - 1) / (highestCount - 1)).roundToInt()
                    if (count > 0) {
                        number.setTextColor(0xFF0A211C.toInt())
                        number.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(0xFFBCF46A.toInt())
                        }
                    }
                    cell.addView(number, FrameLayout.LayoutParams(dp(diameter), dp(diameter), Gravity.CENTER))
                    cell.contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(labels.locale)) +
                        ", " + tripCount(count)
                    cell.isFocusable = true
                    cell.setOnClickListener { select(date) }
                }
                line.addView(cell, LayoutParams(0, dp(46), 1f))
            }
            addView(line)
        }
    }

    private fun tripCount(count: Int) = if (count == 1) labels.t("1 viatge", "1 viaje", "1 trip")
        else labels.t("$count viatges", "$count viajes", "$count trips")

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun centered(value: String) = TextView(context).apply {
        text = value; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 13f
    }
    private fun button(value: String, click: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; minimumWidth = 0; minWidth = 0
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { click() }
    }
}
