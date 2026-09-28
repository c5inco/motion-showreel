package com.example.showreel

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A fully analytic particle system: every particle's position is a pure function of time,
 * so the reel can be scrubbed, looped and frozen for inspection without any simulation state.
 *
 * Life of a particle (s = seconds since the scene starts):
 *  burst from centre -> orbit in a three-armed spiral galaxy -> arc into the glyphs of the
 *  title -> anticipate -> explode outwards when the solid title slams in.
 */
class Particles(private val count: Int = 3600) {
  private var w = 0f
  private var h = 0f

  private val arm = FloatArray(count)
  private val radius = FloatArray(count)
  private val delay = FloatArray(count)
  private val tx = FloatArray(count)
  private val ty = FloatArray(count)
  private val ex = FloatArray(count)
  private val ey = FloatArray(count)
  private val wobble = FloatArray(count)
  private val bucket = IntArray(count)

  private val colors = intArrayOf(
    Palette.Paper.toArgb(),
    Palette.Ember.toArgb(),
    0xFF7C90FF.toInt(),
    Palette.Volt.toArgb(),
  )
  private val widths = floatArrayOf(1f, 1.25f, 1f, 0.8f)

  private val lines = Array(4) { FloatArray(count * 4) }
  private val lineCounts = IntArray(4)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.BUTT
    blendMode = BlendMode.PLUS
  }

  private var textCx = 0f
  private var textCy = 0f

  /**
   * Rasterises the title with [title] metrics and scatters particle targets over its ink.
   * [left] and [baseline] are where the title's layout origin and baseline sit on screen.
   */
  fun prepare(
    width: Float,
    height: Float,
    title: String,
    face: Face,
    px: Float,
    left: Float,
    baseline: Float,
  ) {
    if (width == w && height == h) return
    w = width
    h = height
    val rnd = Random(26)

    val scale = 2.5f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = face.typeface
      textSize = px / scale
      letterSpacing = face.tracking
      color = android.graphics.Color.WHITE
    }
    val cap = paint.textSize * face.capRatio
    val bw = (paint.measureText(title) + 4).toInt()
    val bh = (cap * 1.5f + 4).toInt()
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
    val base = cap * 1.25f + 2f
    Canvas(bmp).drawText(title, 0f, base, paint)
    val ink = ArrayList<Long>()
    val row = ByteArray(bw)
    val buf = java.nio.ByteBuffer.allocate(bmp.byteCount)
    bmp.copyPixelsToBuffer(buf)
    val stride = bmp.rowBytes
    for (y in 0 until bh) {
      buf.position(y * stride)
      buf.get(row, 0, bw)
      for (x in 0 until bw) if ((row[x].toInt() and 0xFF) > 140) ink += (x.toLong() shl 32) or y.toLong()
    }
    bmp.recycle()
    ink.shuffle(rnd)

    textCx = left + bw * scale / 2f
    textCy = baseline - cap * scale / 2f

    for (i in 0 until count) {
      val p = ink[i % ink.size]
      val jitter = if (i >= ink.size) scale * 0.5f else 0f
      tx[i] = left + (p ushr 32).toFloat() * scale + rnd.nextFloat() * scale + (rnd.nextFloat() - .5f) * jitter
      ty[i] = baseline - (base - (p and 0xFFFFFFFF).toFloat()) * scale + rnd.nextFloat() * scale

      val r = sqrt(rnd.nextFloat()) * 0.95f + 0.05f
      radius[i] = r
      arm[i] = (i % 3) * (2f * PI.toFloat() / 3f) + r * 4.2f + (rnd.nextFloat() - 0.5f) * (0.9f - r * 0.5f)
      delay[i] = rnd.nextFloat() * 0.35f + (1f - r) * 0.15f
      val a = rnd.nextFloat() * 2f * PI.toFloat()
      val dx = tx[i] - textCx
      val dy = ty[i] - textCy
      val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
      val sp = 0.35f + rnd.nextFloat() * rnd.nextFloat() * 1.4f
      ex[i] = (dx / len * 0.7f + cos(a) * 0.6f) * sp
      ey[i] = (dy / len * 0.7f + sin(a) * 0.6f) * sp
      wobble[i] = rnd.nextFloat() * 100f
      val c = rnd.nextFloat()
      bucket[i] = when {
        c < 0.5f -> 0
        c < 0.74f -> 1
        c < 0.9f -> 2
        else -> 3
      }
    }
  }

  private var px = 0f
  private var py = 0f

  private fun position(i: Int, s: Float) {
    val cx = w / 2f
    val cy = h / 2f
    val r = radius[i]
    val burst = Ease.outExpo(prog(s, 0f, 0.9f))
    val rr = r * w * 0.47f * burst * (1f + 0.04f * sin(s * 3f + wobble[i]))
    val ang = arm[i] + s * (1.1f / (0.22f + r)) + (1f - burst) * 3f
    val gx = cx + rr * cos(ang)
    val gy = cy + rr * sin(ang) * 0.92f

    val k = Ease.inOutCubic(prog(s, 1.15f + delay[i], 2.25f + delay[i]))
    val dx = tx[i] - gx
    val dy = ty[i] - gy
    val arc = sin(k * PI.toFloat()) * 0.35f
    var x = gx + dx * k - dy * arc
    var y = gy + dy * k + dx * arc

    // Anticipation: the formed word inhales before it blows apart.
    val inhale = Ease.inCubic(prog(s, 2.65f, 3.1f)) * 0.07f
    x += (textCx - x) * inhale + sin(s * 40f + wobble[i]) * w * 0.0015f * k
    y += (textCy - y) * inhale

    val e = s - 3.1f
    if (e > 0f) {
      val out = 1f - exp(-e * 3.2f)
      x += ex[i] * out * w * 0.9f
      y += ey[i] * out * w * 0.9f + e * e * w * 0.05f
    }
    px = x
    py = y
  }

  fun draw(scope: DrawScope, s: Float, unit: Float) {
    if (s < 0f || s > 5f) return
    val alpha = prog(s, 0f, 0.25f) * (1f - prog(s, 3.3f, 4.6f))
    if (alpha <= 0f) return
    lineCounts.fill(0)
    // Streak length stretches with speed: longer trails during the burst and the explosion.
    val trail = 0.028f + 0.02f * (1f - prog(s, 0f, 0.6f)) + 0.03f * prog(s, 3.1f, 3.2f)
    for (i in 0 until count) {
      position(i, s - trail)
      val x0 = px
      val y0 = py
      position(i, s)
      val b = bucket[i]
      val arr = lines[b]
      val n = lineCounts[b]
      arr[n] = x0
      arr[n + 1] = y0
      arr[n + 2] = px
      arr[n + 3] = py
      lineCounts[b] = n + 4
    }
    scope.drawIntoCanvas { c ->
      val canvas = c.nativeCanvas
      for (b in 0 until 4) {
        paint.color = colors[b]
        paint.alpha = (alpha * 235).toInt()
        paint.strokeWidth = unit * 0.42f * widths[b]
        canvas.drawLines(lines[b], 0, lineCounts[b], paint)
      }
    }
  }
}
