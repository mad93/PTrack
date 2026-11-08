package cat.ptrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class NavigationPlanStore(context: Context) {
    val prefs = context.getSharedPreferences("navigation_plan", Context.MODE_PRIVATE)

    fun read(): NavigationPlan? {
        val stored = prefs.getString(PLAN, null) ?: return null
        return runCatching {
            val json = JSONObject(stored)
            val points = json.getJSONArray("points")
            val chargers = json.optJSONObject("chargerNames")
            NavigationPlan(List(points.length()) { points.getString(it) }, json.getInt("completed"),
                json.optString("origin").ifEmpty { null },
                chargers?.keys()?.asSequence()?.associateWith { chargers.getString(it) }.orEmpty())
        }.getOrNull()
    }

    fun save(plan: NavigationPlan) {
        val json = JSONObject().put("points", JSONArray(plan.points)).put("completed", plan.completed)
            .put("origin", plan.origin).put("chargerNames", JSONObject(plan.chargerNames))
        prefs.edit().putString(PLAN, json.toString()).apply()
    }

    private companion object {
        const val PLAN = "plan"
    }
}
