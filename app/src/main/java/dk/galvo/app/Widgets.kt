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

/** 2x2: big glowing streak flame with today's goal bar. */
class MiniWidget : CanvasWidget() {
    override val defW = 130; override val defH = 130
    override fun click(ctx: Context) = openAppIntent(ctx, 30)
    override fun draw(ctx: Context, c: android.graphics.Canvas, w: Int, h: Int) {
        val s = GalvoStore.summary(ctx)
        val alive = s.streak > 0
        Art.background(c, w, h, if (alive) Art.FLAME else Art.LINE, 0.5f, 0.4f)
        val m = minOf(w, h).toFloat(); val cx = w / 2f; val cy = h * 0.42f
        Art.flame(c, cx, cy, m * 0.3f, if (alive) Art.ORANGE else Art.LINE)
        Art.flame(c, cx, cy + m * 0.06f, m * 0.19f, if (alive) Art.FLAME else android.graphics.Color.parseColor("#3A3C60"))
        c.drawText("${s.streak}", cx, cy + m * 0.13f, Art.paint(android.graphics.Color.parseColor("#1A0D00"), m * 0.17f, true).apply { textAlign = android.graphics.Paint.Align.CENTER })
        c.drawText(if (s.streak == 1) "dag i træk" else "dage i træk", cx, h * 0.76f, Art.paint(Art.SUB, m * 0.085f, true).apply { textAlign = android.graphics.Paint.Align.CENTER })
        val bx = w * 0.18f; val bw = w * 0.64f; val by = h * 0.84f; val bh = m * 0.05f
        c.drawRoundRect(android.graphics.RectF(bx, by, bx + bw, by + bh), bh, bh, Art.paint(Art.LINE))
        val f = (s.todayXp.toFloat() / s.goal).coerceIn(0f, 1f)
        if (f > 0f) c.drawRoundRect(android.graphics.RectF(bx, by, bx + maxOf(bh, bw * f), by + bh), bh, bh, Art.paint(if (f >= 1f) Art.GREEN else Art.ORANGE))
    }
}
