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

    fun saveCases(ctx: Context, json: String) {
        prefs(ctx).edit().putString("cases", json).apply()
        refreshWidgets(ctx)
    }

    data class Extras(
        val gems: Int, val hearts: Int, val level: Int, val freeze: Int,
        val week: List<Pair<String, Int>>, val weekTotal: Int, val activeDays: Int
    )

    fun extras(ctx: Context): Extras {
        val raw = state(ctx)
        val dayNames = listOf("man", "tir", "ons", "tor", "fre", "lør", "søn")
        val today = LocalDate.now()
        return try {
            val s = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            val xp = s.optInt("xp", 0)
            val level = kotlin.math.floor(kotlin.math.sqrt(xp / 25.0)).toInt() + 1
            var hearts = if (s.has("hearts")) s.optInt("hearts", 5) else 5
            val hTs = s.optLong("hTs", System.currentTimeMillis())
            if (hearts < 5) hearts = (hearts + ((System.currentTimeMillis() - hTs) / (30 * 60 * 1000L)).toInt()).coerceAtMost(5)
            val byDay = s.optJSONObject("xpByDay")
            val week = (6 downTo 0).map { i ->
                val d = today.minusDays(i.toLong())
                dayNames[d.dayOfWeek.value - 1] to (byDay?.optInt(d.toString(), 0) ?: 0)
            }
            Extras(s.optInt("gems", 0), hearts.coerceAtLeast(0), level, s.optInt("freeze", 0),
                week, week.sumOf { it.second }, week.count { it.second > 0 })
        } catch (e: Exception) {
            Extras(0, 5, 1, 0, (6 downTo 0).map { dayNames[today.minusDays(it.toLong()).dayOfWeek.value - 1] to 0 }, 0, 0)
        }
    }

    /** Same daily case as the app: index = floor(local midnight epoch ms / 86400000) % count. */
    data class DailyCase(val index: Int, val title: String, val machine: String, val steps: Int, val stars: Int)

    fun dailyCase(ctx: Context): DailyCase? {
        val raw = prefs(ctx).getString("cases", "") ?: ""
        if (raw.isBlank()) return null
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return null
            val midnight = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val n = Math.floorDiv(midnight, 86400000L)
            val idx = Math.floorMod(n, arr.length().toLong()).toInt()
            val c = arr.getJSONObject(idx)
            val st = state(ctx)
            val stars = try { JSONObject(st).optJSONObject("cases")?.optInt(c.getString("id"), 0) ?: 0 } catch (e: Exception) { 0 }
            DailyCase(idx, c.optString("t"), c.optString("m"), c.optInt("n", 3), stars)
        } catch (e: Exception) { null }
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
        val mood: String,      // happy | worried | party | think | cool | sleepy | wink | sad | love
        val message: String,
        val doneToday: Boolean
    )

    fun summary(ctx: Context): Summary {
        val raw = state(ctx)
        if (raw.isBlank()) {
            return Summary(0, 0, 20, 0, "wink", "Hej, jeg er Galvo! Klar til dit første kundeudkald?", false)
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
            val freeze = s.optInt("freeze", 0).coerceIn(0, 2)
            val liveStreak = if (gap <= 1L + freeze) storedStreak else 0
            val atRisk = gap >= 1L && gap <= 1L + freeze && storedStreak > 0
            val doneToday = gap == 0L
            val hour = java.time.LocalTime.now().hour
            val best = s.optInt("best", 0)
            val left = (goal - todayXp).coerceAtLeast(0)
            val fresh = s.optInt("lessons", 0) == 0 && s.optInt("xp", 0) == 0
            val (mood, msg) = when {
                fresh -> "wink" to "Hej, jeg er Galvo! Klar til dit første kundeudkald?"
                todayXp >= goal && liveStreak >= 7 -> "cool" to pick(listOf(
                    "Mål nået og $liveStreak dage i træk. Fuld effekt!",
                    "$liveStreak dage i træk. Jeg tager solbrillerne på.",
                    "Du kører som en velsmurt printer. $liveStreak dage!"))
                todayXp >= goal -> pick(listOf("party", "love", "party", "cool")) to pick(listOf(
                    "Dagens mål er nået. Du er skarp!",
                    "Fyraften! Du har gjort dit i dag.",
                    "Mål nået – resten af dagen er bonus.",
                    "Flot arbejde i dag. Jeg er stolt!"))
                hour >= 23 || hour < 5 -> "sleepy" to
                    (if (atRisk) "Zzz… din streak på $storedStreak dage venter på dig." else "Zzz… jeg lader op til i morgen.")
                atRisk && hour >= 20 -> "sad" to pick(listOf(
                    "Flammen på $storedStreak dage er ved at gå ud! 3 minutter?",
                    "Sidste chance i dag – red din streak på $storedStreak dage.",
                    "Jeg vil nødig slukke flammen. Kommer du?"))
                atRisk && hour >= 16 -> "worried" to pick(listOf(
                    "Din streak på $storedStreak dage er i fare!",
                    "Psst… $storedStreak dage i træk står på spil.",
                    "Én lektion, så er streaken reddet."))
                atRisk -> "wink" to pick(listOf(
                    "$storedStreak dage i træk – skal vi gøre det til ${storedStreak + 1}?",
                    "${greet(hour)} Én lektion holder flammen i live.",
                    "Kaffe og et hurtigt kundeudkald?"))
                doneToday -> "happy" to pick(listOf(
                    "$left XP mere, så er dagens mål nået.",
                    "Godt i gang! $left XP til målet.",
                    "Næsten der – $left XP tilbage."))
                best >= 3 -> pick(listOf("sad", "think", "sad")) to pick(listOf(
                    "Jeg savner dig. Skal vi starte en ny streak?",
                    "Værktøjskassen samler støv. Én lektion?",
                    "Din rekord er $best dage. Den kan du slå!"))
                else -> "wink" to pick(listOf(
                    "${greet(hour)} Én lektion på 3 min – klar?",
                    "Jeg har en ny sag til dig.",
                    "Skal vi lære noget nyt i dag?"))
            }
            Summary(liveStreak, todayXp, goal, s.optInt("xp", 0), mood, msg, doneToday)
        } catch (e: Exception) {
            Summary(0, 0, 20, 0, "happy", "Klar til dagens lektion?", false)
        }
    }

    private fun greet(hour: Int) = if (hour < 10) "Godmorgen!" else if (hour < 18) "Hej!" else "Godaften!"

    /** Picks from a pool so the choice changes every half hour – alive, but never flickering between redraws. */
    fun <T> pick(pool: List<T>, salt: Int = 0): T {
        val now = java.time.LocalDateTime.now()
        val slot = now.toLocalDate().toEpochDay() * 48 + now.hour * 2 + now.minute / 30 + salt
        return pool[Math.floorMod(slot, pool.size.toLong()).toInt()]
    }

    private val QUIPS = listOf(
        "PVC? Aldrig i min laser!",
        "Husk: dysetjek før du renser.",
        "Testsnit før store jobs. Altid.",
        "Fokus er alt – også for mig.",
        "Laserbriller passer til bølgelængden – ikke til outfittet.",
        "Wiper ren, caps rene – dyserne glade!",
        "Tre rigtige i træk? Så tænder jeg for strålen!")

    /** What Galvo says in his own widget: his current mood line, mixed with one of his sayings once the day's goal is done. */
    fun chatter(ctx: Context): String {
        val s = summary(ctx)
        val now = java.time.LocalDateTime.now()
        val oddSlot = (now.hour * 2 + now.minute / 30) % 2 == 1
        return if (s.todayXp >= s.goal && oddSlot) pick(QUIPS, 3) else s.message
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
        "think" -> R.drawable.galvo_think
        "cool" -> R.drawable.galvo_cool
        "sleepy" -> R.drawable.galvo_sleepy
        "wink" -> R.drawable.galvo_wink
        "sad" -> R.drawable.galvo_sad
        "love" -> R.drawable.galvo_love
        else -> R.drawable.galvo_happy
    }

    fun refreshWidgets(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        listOf(StreakWidget::class.java, QuestionWidget::class.java, MiniWidget::class.java, GoalRingWidget::class.java, WeekWidget::class.java, StatsWidget::class.java, CaseWidget::class.java, BuddyWidget::class.java).forEach { cls ->
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
