package dk.galvo.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

internal fun openAppIntent(ctx: Context, requestCode: Int, questionId: String? = null): PendingIntent {
    val i = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (questionId != null) putExtra(MainActivity.EXTRA_QUESTION, questionId)
    }
    return PendingIntent.getActivity(
        ctx, requestCode, i,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** 4x2: Galvo, streak, today's goal and a start button. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val s = GalvoStore.summary(ctx)
        ids.forEach { id ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_streak)
            v.setImageViewResource(R.id.w_galvo, GalvoStore.moodDrawable(s.mood))
            v.setTextViewText(R.id.w_streak, "${s.streak}")
            v.setTextViewText(R.id.w_streak_label, if (s.streak == 1) "dag i træk" else "dage i træk")
            v.setTextViewText(R.id.w_message, s.message)
            v.setProgressBar(R.id.w_progress, s.goal, s.todayXp.coerceAtMost(s.goal), false)
            v.setTextViewText(R.id.w_goal, "${s.todayXp.coerceAtMost(s.goal)}/${s.goal} XP i dag")
            v.setTextViewText(R.id.w_button, if (s.todayXp >= s.goal) "Én til?" else "Start lektion")
            v.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, 10))
            v.setOnClickPendingIntent(R.id.w_button, openAppIntent(ctx, 11))
            mgr.updateAppWidget(id, v)
        }
    }
}

/** 4x2: today's question – tap to answer it in the app. */
class QuestionWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val q = GalvoStore.questionOfDay(ctx)
        val s = GalvoStore.summary(ctx)
        ids.forEach { id ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_question)
            v.setImageViewResource(R.id.w_galvo, GalvoStore.moodDrawable(if (s.mood == "worried") "worried" else "happy"))
            if (q != null) {
                v.setTextViewText(R.id.w_unit, "DAGENS SPØRGSMÅL · ${q.third.uppercase()}")
                v.setTextViewText(R.id.w_question, q.second)
                v.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, 20, q.first))
            } else {
                v.setTextViewText(R.id.w_unit, "DAGENS SPØRGSMÅL")
                v.setTextViewText(R.id.w_question, "Åbn Galvo én gang, så kommer dagens spørgsmål her.")
                v.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, 21))
            }
            v.setTextViewText(R.id.w_streak, "${s.streak}")
            mgr.updateAppWidget(id, v)
        }
    }
}

/** 2x2: Galvo and the streak. */
class MiniWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val s = GalvoStore.summary(ctx)
        ids.forEach { id ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_mini)
            v.setImageViewResource(R.id.w_galvo, GalvoStore.moodDrawable(s.mood))
            v.setTextViewText(R.id.w_streak, "${s.streak}")
            v.setProgressBar(R.id.w_progress, s.goal, s.todayXp.coerceAtMost(s.goal), false)
            v.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, 30))
            mgr.updateAppWidget(id, v)
        }
    }
}
