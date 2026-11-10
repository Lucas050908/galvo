package dk.galvo.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Holds the learning progress the web app sends over, and computes what the widgets show. */
object GalvoStore {
    private const val PREFS = "galvo"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveState(ctx: Context, json: String) {
        prefs(ctx).edit().putString("state", json).apply()
        refreshWidgets(ctx)
    }

    fun state(ctx: Context): String = prefs(ctx).getString("state", "") ?: ""

    fun saveQuestions(ctx: Context, json: String) {
        prefs(ctx).edit().putString("questions", json).apply()
        refreshWidgets(ctx)
    }

    fun reminderTime(ctx: Context): String = prefs(ctx).getString("remindTime", "07:30") ?: "07:30"
    fun reminderOn(ctx: Context): Boolean = prefs(ctx).getBoolean("remindOn", false)
    fun setReminder(ctx: Context, time: String, on: Boolean) {
        prefs(ctx).edit().putString("remindTime", time).putBoolean("remindOn", on).apply()
    }

    data class Summary(
        val streak: Int,
        val todayXp: Int,
        val goal: Int,
        val totalXp: Int,
        val mood: String,      // happy | worried | party
        val message: String,
        val doneToday: Boolean
    )

    fun summary(ctx: Context): Summary {
        val raw = state(ctx)
        if (raw.isBlank()) {
            return Summary(0, 0, 20, 0, "happy", "Klar til dit første kundeudkald?", false)
        }
        return try {
            val s = JSONObject(raw)
            val today = LocalDate.now()
            val todayKey = today.toString()
            val goal = s.optInt("goal", 20).coerceAtLeast(1)
            val xpByDay = s.optJSONObject("xpByDay")
            val todayXp = xpByDay?.optInt(todayKey, 0) ?: 0
            val lastDay = s.optString("lastDay", "")
            val storedStreak = s.optInt("streak", 0)
            val gap = if (lastDay.isNotBlank() && lastDay != "null") {
                try { ChronoUnit.DAYS.between(LocalDate.parse(lastDay), today) } catch (e: Exception) { 99L }
            } else 99L
            val liveStreak = if (gap <= 1) storedStreak else 0
            val atRisk = gap == 1L && storedStreak > 0
            val doneToday = gap == 0L
            val (mood, msg) = when {
                todayXp >= goal -> "party" to "Dagens mål er nået. Du er skarp!"
                atRisk -> "worried" to "Din streak på $storedStreak dage er i fare!"
                doneToday -> "happy" to "${goal - todayXp} XP mere til dagens mål."
                else -> "happy" to "Én lektion på 3 min – klar?"
            }
            Summary(liveStreak, todayXp, goal, s.optInt("xp", 0), mood, msg, doneToday)
        } catch (e: Exception) {
            Summary(0, 0, 20, 0, "happy", "Klar til dagens lektion?", false)
        }
    }

    /** Question of the day: the same question all day, a new one tomorrow. */
    fun questionOfDay(ctx: Context): Triple<String, String, String>? {
        val raw = prefs(ctx).getString("questions", "") ?: ""
        if (raw.isBlank()) return null
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return null
            val idx = (LocalDate.now().toEpochDay() % arr.length()).toInt()
            val q = arr.getJSONObject(idx)
            Triple(q.getString("id"), q.getString("q"), q.optString("u", ""))
        } catch (e: Exception) { null }
    }

    fun moodDrawable(mood: String): Int = when (mood) {
        "party" -> R.drawable.galvo_party
        "worried" -> R.drawable.galvo_worried
        else -> R.drawable.galvo_happy
    }

    fun refreshWidgets(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        listOf(StreakWidget::class.java, QuestionWidget::class.java, MiniWidget::class.java).forEach { cls ->
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) {
                val i = Intent(ctx, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(i)
            }
        }
    }
}
