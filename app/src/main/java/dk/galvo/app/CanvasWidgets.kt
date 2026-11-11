package dk.galvo.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/** Shared drawing helpers for the canvas-drawn widgets. */
object Art {
    val BG1 = Color.parseColor("#22213F"); val BG2 = Color.parseColor("#14152A")
    val TX = Color.parseColor("#F2F0FF"); val SUB = Color.parseColor("#A9A6C8")
    val ORANGE = Color.parseColor("#FF7A1A"); val FLAME = Color.parseColor("#FFB347")
    val CYAN = Color.parseColor("#4CC9F0"); val PINK = Color.parseColor("#FF7DB8")
    val GREEN = Color.parseColor("#3DDC97"); val LINE = Color.parseColor("#2C2E4D")

    fun sizePx(ctx: Context, mgr: AppWidgetManager, id: Int, defW: Int, defH: Int): Pair<Int, Int> {
        val o: Bundle = mgr.getAppWidgetOptions(id)
        val d = ctx.resources.displayMetrics.density
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, defW).takeIf { it > 0 } ?: defW
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defH).takeIf { it > 0 } ?: defH
        // Keep bitmaps modest so RemoteViews stays well under its memory limit.
        val scale = min(d, 2.5f)
        return Pair((wDp * scale).toInt().coerceIn(120, 1100), (hDp * scale).toInt().coerceIn(60, 700))
    }

    fun paint(color: Int, size: Float = 0f, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Dark card with a soft coloured glow. */
    fun background(c: Canvas, w: Int, h: Int, glow: Int, gx: Float = 0.2f, gy: Float = 0.5f) {
        val r = min(w, h) * 0.16f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), BG1, BG2, Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, bg)
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w * gx, h * gy, max(w, h) * 0.6f, Color.argb(110, Color.red(glow), Color.green(glow), Color.blue(glow)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.save(); val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }; c.clipPath(clip)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), g); c.restore()
        c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), r, r, paint(LINE).apply { style = Paint.Style.STROKE; strokeWidth = 2f })
    }

    fun galvo(ctx: Context, c: Canvas, mood: String, cx: Float, cy: Float, size: Float) {
        val bmp = BitmapFactory.decodeResource(ctx.resources, GalvoStore.moodDrawable(mood)) ?: return
        val s = size / max(bmp.width, bmp.height)
        val dst = RectF(cx - bmp.width * s / 2, cy - bmp.height * s / 2, cx + bmp.width * s / 2, cy + bmp.height * s / 2)
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    fun flame(c: Canvas, x: Float, y: Float, s: Float, col: Int) {
        val p = Path().apply {
            moveTo(x, y - s); cubicTo(x + s * 0.2f, y - s * 0.5f, x + s * 0.75f, y - s * 0.35f, x + s * 0.6f, y + s * 0.25f)
            cubicTo(x + s * 0.5f, y + s * 0.75f, x - s * 0.5f, y + s * 0.75f, x - s * 0.6f, y + s * 0.25f)
            cubicTo(x - s * 0.7f, y - s * 0.1f, x - s * 0.3f, y - s * 0.35f, x - s * 0.15f, y - s * 0.55f)
            cubicTo(x - s * 0.05f, y - s * 0.3f, x + s * 0.05f, y - s * 0.6f, x, y - s); close()
        }
        c.drawPath(p, paint(col))
    }

    fun heart(c: Canvas, x: Float, y: Float, s: Float, col: Int) {
        val p = Path().apply {
            moveTo(x, y + s * 0.45f)
            cubicTo(x - s * 0.95f, y - s * 0.15f, x - s * 0.45f, y - s * 0.8f, x, y - s * 0.3f)
            cubicTo(x + s * 0.45f, y - s * 0.8f, x + s * 0.95f, y - s * 0.15f, x, y + s * 0.45f); close()
        }
        c.drawPath(p, paint(col))
    }

    fun gem(c: Canvas, x: Float, y: Float, s: Float, col: Int) {
        val p = Path().apply { moveTo(x - s * 0.5f, y - s * 0.35f); lineTo(x + s * 0.5f, y - s * 0.35f); lineTo(x + s * 0.8f, y); lineTo(x, y + s * 0.6f); lineTo(x - s * 0.8f, y); close() }
        c.drawPath(p, paint(col))
    }

    fun bolt(c: Canvas, x: Float, y: Float, s: Float, col: Int) {
        val p = Path().apply { moveTo(x + s * 0.15f, y - s * 0.7f); lineTo(x - s * 0.45f, y + s * 0.1f); lineTo(x - s * 0.02f, y + s * 0.1f); lineTo(x - s * 0.15f, y + s * 0.7f); lineTo(x + s * 0.45f, y - s * 0.1f); lineTo(x + s * 0.02f, y - s * 0.1f); close() }
        c.drawPath(p, paint(col))
    }

    /** Greedy word wrap for canvas text. */
    fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (wd in text.split(" ")) {
            val t = if (line.isEmpty()) wd else "$line $wd"
            if (p.measureText(t) > maxW && line.isNotEmpty()) { out.add(line); line = wd } else line = t
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    fun push(ctx: Context, mgr: AppWidgetManager, id: Int, bmp: Bitmap, click: android.app.PendingIntent) {
        val v = RemoteViews(ctx.packageName, R.layout.widget_canvas)
        v.setImageViewBitmap(R.id.w_canvas, bmp)
        v.setOnClickPendingIntent(R.id.w_root, click)
        mgr.updateAppWidget(id, v)
    }
}

abstract class CanvasWidget : AppWidgetProvider() {
    abstract val defW: Int; abstract val defH: Int
    abstract fun draw(ctx: Context, c: Canvas, w: Int, h: Int)
    abstract fun click(ctx: Context): android.app.PendingIntent
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { render(ctx, mgr, it) } }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) { render(ctx, mgr, id) }
    private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val (w, h) = Art.sizePx(ctx, mgr, id, defW, defH)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(ctx, Canvas(bmp), w, h)
            Art.push(ctx, mgr, id, bmp, click(ctx))
        } catch (e: Exception) { }
    }
}

/** 2x2: ring around Galvo showing today's goal, streak below. */
class GoalRingWidget : CanvasWidget() {
    override val defW = 130; override val defH = 130
    override fun click(ctx: Context) = openAppIntent(ctx, 40)
    override fun draw(ctx: Context, c: Canvas, w: Int, h: Int) {
        val s = GalvoStore.summary(ctx)
        Art.background(c, w, h, if (s.todayXp >= s.goal) Art.GREEN else Art.ORANGE, 0.5f, 0.42f)
        val m = min(w, h).toFloat(); val cx = w / 2f; val cy = h * 0.43f; val rad = m * 0.3f
        val st = m * 0.075f
        val track = Art.paint(Art.LINE).apply { style = Paint.Style.STROKE; strokeWidth = st; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(cx, cy, rad, track)
        val frac = (s.todayXp.toFloat() / s.goal).coerceIn(0f, 1f)
        val arc = Art.paint(Art.ORANGE).apply {
            style = Paint.Style.STROKE; strokeWidth = st; strokeCap = Paint.Cap.ROUND
            shader = SweepGradient(cx, cy, intArrayOf(Art.FLAME, Art.ORANGE, Art.FLAME), null)
        }
        if (frac > 0f) c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), -90f, 360f * frac, false, arc)
        Art.galvo(ctx, c, s.mood, cx, cy, rad * 1.45f)
        val tp = Art.paint(Art.TX, m * 0.12f, true).apply { textAlign = Paint.Align.CENTER }
        val label = if (s.todayXp >= s.goal) "Mål nået!" else "${s.todayXp}/${s.goal} XP"
        c.drawText(label, cx, h * 0.86f, tp)
        Art.flame(c, w - m * 0.2f, m * 0.15f, m * 0.065f, if (s.streak > 0) Art.FLAME else Art.LINE)
        c.drawText("${s.streak}", w - m * 0.2f, m * 0.3f, Art.paint(Art.FLAME, m * 0.1f, true).apply { textAlign = Paint.Align.CENTER })
    }
}

/** 4x2: XP per day this week as glowing bars. */
class WeekWidget : CanvasWidget() {
    override val defW = 260; override val defH = 130
    override fun click(ctx: Context) = openAppIntent(ctx, 41, "go_practice")
    override fun draw(ctx: Context, c: Canvas, w: Int, h: Int) {
        val s = GalvoStore.summary(ctx); val e = GalvoStore.extras(ctx)
        Art.background(c, w, h, Art.CYAN, 0.85f, 0.2f)
        val pad = h * 0.11f
        c.drawText("DIN UGE", pad, pad + h * 0.08f, Art.paint(Art.SUB, h * 0.085f, true))
        c.drawText("${e.weekTotal} XP · ${e.activeDays} ${if (e.activeDays == 1) "dag" else "dage"}", pad, pad + h * 0.21f, Art.paint(Art.TX, h * 0.12f, true))
        // streak chip
        val chip = Art.paint(Art.FLAME, h * 0.12f, true).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("${s.streak}", w - pad, pad + h * 0.21f, chip)
        Art.flame(c, w - pad - chip.measureText("${s.streak}") - h * 0.08f, pad + h * 0.165f, h * 0.075f, if (s.streak > 0) Art.FLAME else Art.LINE)
        val top = h * 0.42f; val bottom = h * 0.8f
        val maxV = max(s.goal, e.week.maxOf { it.second }).coerceAtLeast(1)
        val n = e.week.size; val slot = (w - pad * 2) / n; val bw = slot * 0.46f
        e.week.forEachIndexed { i, (d, v) ->
            val x = pad + slot * i + slot / 2
            val today = i == n - 1
            c.drawRoundRect(RectF(x - bw / 2, top, x + bw / 2, bottom), bw / 2, bw / 2, Art.paint(Art.LINE))
            if (v > 0) {
                val bh = max(bw, (bottom - top) * v / maxV)
                val p = Art.paint(Art.CYAN).apply { shader = LinearGradient(0f, bottom - bh, 0f, bottom, if (today) Art.FLAME else Art.CYAN, if (today) Art.ORANGE else Color.parseColor("#2B8FB5"), Shader.TileMode.CLAMP) }
                c.drawRoundRect(RectF(x - bw / 2, bottom - bh, x + bw / 2, bottom), bw / 2, bw / 2, p)
            }
            c.drawText(d, x, h * 0.93f, Art.paint(if (today) Art.TX else Art.SUB, h * 0.075f, today).apply { textAlign = Paint.Align.CENTER })
        }
        // goal line
        val gy = bottom - (bottom - top) * s.goal / maxV
        c.drawLine(pad, gy, w - pad, gy, Art.paint(Art.GREEN).apply { strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f); alpha = 150 })
    }
}

/** 4x1: Duolingo-style status bar – streak, gems, hearts, level. */
class StatsWidget : CanvasWidget() {
    override val defW = 260; override val defH = 60
    override fun click(ctx: Context) = openAppIntent(ctx, 42, "go_shop")
    override fun draw(ctx: Context, c: Canvas, w: Int, h: Int) {
        val s = GalvoStore.summary(ctx); val e = GalvoStore.extras(ctx)
        Art.background(c, w, h, Art.ORANGE, 0.1f, 0.5f)
        val items = listOf(
            Triple(Art.FLAME, "${s.streak}", 0), Triple(Art.CYAN, "${e.gems}", 1),
            Triple(Art.PINK, "${e.hearts}", 2), Triple(Art.FLAME, "Niv. ${e.level}", 3))
        val slot = w / 4f; val cy = h / 2f; val ic = h * 0.22f
        val tp = Art.paint(Art.TX, h * 0.3f, true)
        items.forEach { (col, txt, k) ->
            val tw = tp.measureText(txt); val total = ic * 2 + h * 0.08f + tw
            val x0 = slot * k + (slot - total) / 2 + ic
            when (k) { 0 -> Art.flame(c, x0, cy, ic, if (s.streak > 0) col else Art.LINE); 1 -> Art.gem(c, x0, cy, ic, col); 2 -> Art.heart(c, x0, cy, ic * 1.1f, if (e.hearts > 0) col else Art.LINE); else -> Art.bolt(c, x0, cy, ic, col) }
            c.drawText(txt, x0 + ic + h * 0.08f, cy + tp.textSize * 0.36f, tp.apply { color = if (k == 3) Art.TX else col })
        }
    }
}

/** 4x2: today's troubleshooting case – tap to start it. */
class CaseWidget : CanvasWidget() {
    override val defW = 260; override val defH = 130
    override fun click(ctx: Context): android.app.PendingIntent {
        val dc = GalvoStore.dailyCase(ctx)
        return if (dc != null) openAppIntent(ctx, 43, "case_${dc.index}") else openAppIntent(ctx, 44)
    }
    override fun draw(ctx: Context, c: Canvas, w: Int, h: Int) {
        val dc = GalvoStore.dailyCase(ctx)
        Art.background(c, w, h, Art.CYAN, 0.88f, 0.75f)
        val pad = h * 0.12f
        Art.galvo(ctx, c, if (dc == null) "wink" else if (dc.stars == 3) "cool" else if (dc.stars > 0) "wink" else "think", w - h * 0.42f, h * 0.55f, h * 0.7f)
        val maxW = w - h * 0.8f - pad
        c.drawText("DAGENS SAG${if (dc != null) " · ${dc.machine.uppercase()}" else ""}", pad, pad + h * 0.08f, Art.paint(Art.CYAN, h * 0.085f, true))
        val title = dc?.title ?: "Åbn Galvo, så kommer dagens sag her"
        val tp = Art.paint(Art.TX, h * 0.14f, true)
        // simple two-line wrap
        val words = title.split(" "); var line = ""; val lines = mutableListOf<String>()
        for (wd in words) { val t = if (line.isEmpty()) wd else "$line $wd"; if (tp.measureText(t) > maxW && line.isNotEmpty()) { lines.add(line); line = wd } else line = t }
        if (line.isNotEmpty()) lines.add(line)
        lines.take(2).forEachIndexed { i, l -> c.drawText(l, pad, pad + h * 0.27f + i * h * 0.17f, tp) }
        if (dc != null) {
            val sub = "${dc.steps} trin · ${if (dc.stars > 0) "★".repeat(dc.stars) + "☆".repeat(3 - dc.stars) else "op til +25 XP"}"
            c.drawText(sub, pad, h * 0.74f, Art.paint(Art.SUB, h * 0.09f))
            val bp = Art.paint(Art.ORANGE); val bt = Art.paint(Color.parseColor("#1A0D00"), h * 0.09f, true)
            val label = if (dc.stars > 0) "Prøv igen" else "Start sagen"
            val bw = bt.measureText(label) + h * 0.2f; val by = h * 0.8f
            c.drawRoundRect(RectF(pad, by, pad + bw, by + h * 0.14f), h * 0.07f, h * 0.07f, bp)
            c.drawText(label, pad + h * 0.1f, by + h * 0.103f, bt)
        }
    }
}

/** 4x2: Galvo himself with a speech bubble – his mood and what he says follow your day. */
class BuddyWidget : CanvasWidget() {
    override val defW = 260; override val defH = 130
    override fun click(ctx: Context) = openAppIntent(ctx, 45)
    override fun draw(ctx: Context, c: Canvas, w: Int, h: Int) {
        val s = GalvoStore.summary(ctx)
        val glow = when (s.mood) {
            "party", "love", "cool" -> Art.GREEN
            "worried", "sad" -> Art.ORANGE
            "sleepy" -> Color.parseColor("#B794F6")
            else -> Art.CYAN
        }
        Art.background(c, w, h, glow, 0.18f, 0.55f)
        val bx = h * 0.98f; val by = h * 0.12f; val bw = w - bx - h * 0.1f; val bh = h * 0.56f
        if (bw < h * 0.7f) {
            // Too narrow for a bubble: just Galvo and his streak.
            Art.galvo(ctx, c, s.mood, w / 2f, h * 0.46f, min(w, h) * 0.82f)
            c.drawText("${s.streak} ${if (s.streak == 1) "dag" else "dage"}", w / 2f, h * 0.93f, Art.paint(Art.FLAME, h * 0.1f, true).apply { textAlign = Paint.Align.CENTER })
            return
        }
        Art.galvo(ctx, c, s.mood, h * 0.5f, h * 0.52f, h * 0.86f)
        val bubble = Art.paint(Art.TX)
        val r = h * 0.1f
        c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), r, r, bubble)
        val tail = Path().apply { moveTo(bx + 1f, by + bh * 0.42f); lineTo(bx - h * 0.09f, by + bh * 0.56f); lineTo(bx + 1f, by + bh * 0.7f); close() }
        c.drawPath(tail, bubble)
        val pad = h * 0.07f
        val ink = Color.parseColor("#14152A")
        val text = GalvoStore.chatter(ctx)
        var size = h * 0.118f
        var lines = Art.wrap(text, Art.paint(ink, size, true), bw - pad * 2)
        if (lines.size > 3) { size = h * 0.098f; lines = Art.wrap(text, Art.paint(ink, size, true), bw - pad * 2) }
        val tp = Art.paint(ink, size, true)
        val lh = size * 1.22f
        val shown = lines.take(4)
        val y0 = by + (bh - lh * shown.size) / 2 + size * 0.92f
        shown.forEachIndexed { i, l -> c.drawText(l, bx + pad, y0 + i * lh, tp) }
        // streak and today's goal under the bubble
        val fy = h * 0.87f
        Art.flame(c, bx + h * 0.06f, fy - h * 0.035f, h * 0.06f, if (s.streak > 0) Art.FLAME else Art.LINE)
        c.drawText("${s.streak}", bx + h * 0.15f, fy, Art.paint(Art.FLAME, h * 0.1f, true))
        val gx = bx + h * 0.4f; val gw = bx + bw - gx; val gh = h * 0.045f; val gy = fy - h * 0.06f
        c.drawRoundRect(RectF(gx, gy, gx + gw, gy + gh), gh, gh, Art.paint(Art.LINE))
        val f = (s.todayXp.toFloat() / s.goal).coerceIn(0f, 1f)
        if (f > 0f) c.drawRoundRect(RectF(gx, gy, gx + max(gh, gw * f), gy + gh), gh, gh, Art.paint(if (f >= 1f) Art.GREEN else Art.ORANGE))
    }
}
