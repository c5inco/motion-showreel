package com.example.showreel

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The whole 15 second reel. [render] is a pure function of time `t` in [0, 15) so it can be
 * looped, scrubbed and frozen on any frame.
 */
class Reel(context: Context, measurer: TextMeasurer) {
  private val kit = TextKit(measurer)
  // Type system: a tight Swiss grotesk for the hits, a high-contrast serif for the grace
  // notes, and a mono for everything technical.
  private val sans = face(context, R.font.inter_tight_extrabold, tracking = -0.04f)
  private val serif = face(context, R.font.instrument_serif, tracking = -0.02f)
  private val italic = face(context, R.font.instrument_serif_italic, tracking = -0.01f)
  private val mono = face(context, R.font.geist_mono)
  private val monoBold = face(context, R.font.geist_mono_semibold)

  private val fluid = RuntimeShader(Shaders.FLUID)
  private val fluidBrush = ShaderBrush(fluid)
  private val post = RuntimeShader(Shaders.POST)
  private val particles = Particles()
  private val path = Path()

  // Frame-wide geometry, refreshed at the top of render().
  private var w = 0f
  private var h = 0f
  private var cx = 0f
  private var cy = 0f
  private var u = 0f
  private var dot = 0f

  private fun face(context: Context, id: Int, tracking: Float = 0f) =
    Face(FontFamily(Font(id)), ResourcesCompat.getFont(context, id)!!, tracking)

  // ---------------------------------------------------------------------------------------
  // Global motion: impacts drive camera shake and lens aberration.

  private fun impulse(t: Float): Float {
    var sum = 0f
    val im = Timeline.impacts
    for (i in im.indices step 2) {
      val dt = t - im[i]
      if (dt >= 0f) sum += im[i + 1] * exp(-dt * 7f)
    }
    return sum
  }

  private fun glitch(t: Float): Float {
    var g = 0f
    val gl = Timeline.glitches
    for (i in gl.indices step 2) g = max(g, sin(prog(t, gl[i], gl[i + 1]) * PI.toFloat()))
    return g
  }

  fun applyPost(layer: GraphicsLayerScope, t: Float) {
    post.setFloatUniform("res", layer.size.width, layer.size.height)
    post.setFloatUniform("time", t)
    post.setFloatUniform("ca", 0.05f + impulse(t) * 0.9f)
    post.setFloatUniform("glitch", glitch(t))
    post.setFloatUniform("grain", 0.07f)
    layer.renderEffect = RenderEffect.createRuntimeShaderEffect(post, "content").asComposeRenderEffect()
  }

  // ---------------------------------------------------------------------------------------

  fun render(scope: DrawScope, t: Float) = with(scope) {
    w = size.width
    h = size.height
    cx = w / 2f
    cy = h / 2f
    u = w / 100f
    dot = u * 1.6f

    drawRect(Palette.Ink)

    val k = impulse(t)
    val sx = k * u * 1.4f * (sin(t * 93.1f) + 0.5f * sin(t * 47.3f)) / 1.5f
    val sy = k * u * 1.4f * (cos(t * 81.7f) + 0.5f * sin(t * 37.9f)) / 1.5f
    translate(sx, sy) {
      if (t < Timeline.KINETIC + 0.05f) intro(t)
      if (t >= Timeline.KINETIC && t < Timeline.SHADER + 0.1f) kinetic(t - Timeline.KINETIC)
      if (t >= Timeline.SHADER - 0.05f && t < Timeline.GEOMETRY + 0.6f) shader(t, t - Timeline.SHADER)
      if (t >= Timeline.GEOMETRY && t < Timeline.PARTICLES) geometry(t, t - Timeline.GEOMETRY)
      if (t >= Timeline.PARTICLES - 0.05f) identity(t)
      // The opening dot returns as the seed of the particle burst.
      if (t in 9.28f..9.56f) {
        val r = dot * Ease.outBack(prog(t, 9.28f, 9.46f), 3f) * (1f - prog(t, 9.5f, 9.56f))
        drawCircle(Palette.Paper, r, Offset(cx, cy))
      }
    }
    hud(t)
  }

  // =======================================================================================
  // 00 OPEN — a dot anticipates, pops, stretches into a line, splits into colour bars which
  // wipe away to reveal the title stack.

  private fun DrawScope.intro(t: Float) {
    if (t > 1.05f) titleStack(t)

    if (t < 0.74f) {
      val a = Ease.outCubic(prog(t, 0f, 0.14f))
      val b = Ease.outBack(prog(t, 0.14f, 0.34f), 3f)
      val r = lerp(dot * (1f - 0.35f * a), dot * 1.8f, b)
      val s = Ease.inOutExpo(prog(t, 0.34f, 0.74f))
      val lineH = u * 0.9f
      val ww = lerp(r * 2f, w * 1.3f, s)
      val hh = lerp(r * 2f, lineH, Ease.outCubic(prog(t, 0.34f, 0.6f)))
      // Squash & stretch ghosts.
      if (s > 0f && s < 1f) {
        for (g in 1..3) {
          val sg = Ease.inOutExpo(prog(t - g * 0.018f, 0.34f, 0.74f))
          val wg = lerp(r * 2f, w * 1.3f, sg)
          drawRoundRect(
            Palette.Paper.copy(alpha = 0.18f / g),
            Offset(cx - wg / 2f, cy - hh / 2f), Size(wg, hh), CornerRadius(hh / 2f),
          )
        }
      }
      drawRoundRect(Palette.Paper, Offset(cx - ww / 2f, cy - hh / 2f), Size(ww, hh), CornerRadius(hh / 2f))
      // Shockwave ring on the pop.
      val ring = prog(t, 0.16f, 0.6f)
      if (ring > 0f && ring < 1f) {
        drawCircle(
          Palette.Ember, radius = dot + Ease.outExpo(ring) * w * 0.4f, center = Offset(cx, cy),
          style = Stroke(u * 1.2f * (1f - ring)), alpha = 1f - ring,
        )
      }
    }

    if (t >= 0.7f && t < 1.75f) {
      val colors = listOf(Palette.Cobalt, Palette.Ember, Palette.Volt, Palette.Lilac, Palette.Paper)
      val rowOf = intArrayOf(0, 4, 1, 3, 2) // draw the centre bar last so the line stays continuous
      for (idx in 0 until 5) {
        val i = rowOf[idx]
        val order = abs(i - 2)
        val p = Ease.outExpo(prog(t, 0.7f + order * 0.05f, 1.1f + order * 0.05f))
        val yc = lerp(cy, h * (i + 0.5f) / 5f, p)
        val hh = lerp(u * 0.9f, h / 5f + 2f, p)
        val dir = if (i % 2 == 0) 1f else -1f
        val slide = Ease.inCubic(prog(t, 1.08f + i * 0.04f, 1.36f + i * 0.04f)) * w * 1.1f * dir
        val color = if (i == 2) Palette.Paper else colors[i]
        drawRect(color, Offset(slide, yc - hh / 2f), Size(w, hh))
        // Speed-line tail following each bar out.
        if (slide != 0f) {
          val tailX = if (dir > 0) slide - u * 30f else slide + w
          drawRect(color.copy(alpha = 0.35f), Offset(tailX, yc - hh * 0.04f), Size(u * 30f, hh * 0.08f))
        }
      }
    }
  }

  private fun DrawScope.titleStack(t: Float) {
    class Line(val text: String, val face: Face, val px: Float, val color: Color, val outline: Boolean = false)
    val lines = listOf(
      Line("SHOW", sans, kit.fitWidth(this, "SHOW", sans, w * 0.74f), Palette.Paper),
      Line("reel", italic, kit.fitWidth(this, "reel", italic, w * 0.62f), Palette.Ember),
      Line("2026", sans, kit.fitWidth(this, "2026", sans, w * 0.74f), Palette.Paper, outline = true),
    )
    val caps = lines.map { it.px * it.face.capRatio }
    val gap = h * 0.03f
    val total = caps.sum() + gap * 2
    val push = lerp(1f, 1.06f, Ease.outCubic(prog(t, 1.1f, 2.05f)))
    scale(push, Offset(cx, cy)) {
      var top = cy - total / 2f
      for ((i, line) in lines.withIndex()) {
        val cap = caps[i]
        val lineY = top + cap / 2f
        top += cap + gap
        val inP = Ease.outExpo(prog(t, 1.2f + i * 0.08f, 1.8f + i * 0.08f))
        val outP = Ease.inExpo(prog(t, 1.74f + i * 0.05f, 2.02f + i * 0.05f))
        val y = lineY + (1f - inP) * cap * 1.3f - outP * cap * 1.3f
        val l = kit.layout(this, line.text, line.face, line.px)
        clipRect(0f, lineY - cap * 0.8f, w, lineY + cap * 0.8f) {
          if (line.outline) drawCaps(l, line.face, line.px, cx, y, line.color, style = Stroke(u * 0.35f))
          else drawCaps(l, line.face, line.px, cx, y, line.color)
        }
      }
      // Byline slides in under the stack.
      val byP = Ease.outExpo(prog(t, 1.45f, 1.9f)) * (1f - Ease.inExpo(prog(t, 1.8f, 2.0f)))
      if (byP > 0f) {
        val mpx = u * 2.6f
        val by = cy + total / 2f + u * 7f
        monoLine("NATIVE ANDROID  ·  JETPACK COMPOSE", cx + (1f - byP) * u * 20f, by, mpx, Palette.Paper, 0.5f, byP)
      }
    }
  }

  // =======================================================================================
  // 01 KINETIC TYPE — "TIMING / IS / EVERYTHING", three words, three different animations.

  private fun DrawScope.kinetic(k: Float) {
    if (k < 0.86f) timing(k)
    if (k >= 0.84f && k < 1.5f) isWord(k)
    if (k >= 1.3f) everything(k)
  }

  private fun DrawScope.timing(k: Float) {
    val word = "TIMING"
    val px = kit.fitWidth(this, word, sans, w * 0.84f)
    val advances = FloatArray(word.length) { kit.layout(this, word[it].toString(), sans, px).size.width.toFloat() }
    val total = advances.sum()
    val squeeze = Ease.inExpo(prog(k, 0.6f, 0.86f))

    fun letter(i: Int, time: Float, alpha: Float) {
      val p = prog(time, 0.02f + i * 0.065f, 0.55f + i * 0.065f)
      if (p <= 0f) return
      val drop = Ease.outBack(p, 2.4f)
      var x = cx - total / 2f + advances.take(i).sum() + advances[i] / 2f
      x = lerp(x, cx, squeeze)
      val y = cy - (1f - drop) * h * 0.7f
      val stretch = lerp(1.7f, 1f, Ease.outCubic(prog(p, 0f, 0.5f)))
      val land = sin(prog(p, 0.4f, 0.75f) * PI.toFloat()) * 0.18f
      val sy = stretch - land
      val sxs = (1f / stretch + land) * (1f - squeeze * 0.85f)
      val rot = (hash(i.toFloat()) - 0.5f) * 50f * (1f - Ease.outCubic(p))
      val l = kit.layout(this, word[i].toString(), sans, px)
      withTransform({
        rotate(rot, Offset(x, y))
        scale(sxs, sy, Offset(x, y))
      }) {
        drawCaps(l, sans, px, x, y, if (i == 3) Palette.Ember else Palette.Paper, alpha = alpha)
      }
    }
    for (i in word.indices) {
      for (g in 3 downTo 1) letter(i, k - g * 0.022f, 0.12f * (4 - g) / 3f)
      letter(i, k, 1f)
    }
  }

  private fun DrawScope.isWord(k: Float) {
    drawRect(Palette.Paper)
    val px = minOf(kit.fitWidth(this, "is", italic, w * 0.66f), h * 0.4f / italic.capRatio)
    val l = kit.layout(this, "is", italic, px)
    val p = Ease.outExpo(prog(k, 0.84f, 1.2f))
    val s = lerp(2.6f, 1f, p)
    val rot = lerp(-14f, 0f, p)
    withTransform({
      rotate(rot, Offset(cx, cy))
      scale(s, s, Offset(cx, cy))
    }) {
      drawCaps(l, italic, px, cx, cy, Palette.Ink)
    }
    // Annotation tick marks: a nod to the frame-counting behind every beat.
    val a = prog(k, 1.0f, 1.1f)
    if (a > 0f) {
      val cap = px * italic.capRatio
      val lw = l.size.width.toFloat()
      for (j in 0..8) {
        val tx = cx - lw / 2f - u * 8f
        val ty = cy - cap / 2f + cap * j / 8f
        val len = if (j % 4 == 0) u * 4f else u * 2f
        drawLine(Palette.Ember, Offset(tx - len, ty), Offset(tx, ty), u * 0.35f, alpha = a)
      }
      monoLine("12F", cx - lw / 2f - u * 10f, cy - cap / 2f - u * 3f, u * 2.4f, Palette.Ink, 1f, a)
    }
    // Ink iris closes back over the paper.
    val c = prog(k, 1.18f, 1.48f)
    if (c > 0f) drawCircle(Palette.Ink, Ease.inOutCubic(c) * hypot(w, h) / 2f, Offset(cx, cy))
  }

  private fun DrawScope.everything(k: Float) {
    val rowH = h / 8.5f
    val sansPx = rowH * 0.6f / sans.capRatio
    val italicPx = rowH * 0.95f / italic.capRatio
    val outlineRow = kit.layout(this, "EVERYTHING — ", sans, sansPx)
    val heroRow = kit.layout(this, "everything — ", italic, italicPx)
    val rows = 13
    val mid = rows / 2
    val collapse = Ease.inExpo(prog(k, 2.15f, 2.47f))
    val rot = lerp(-9f, -15f, Ease.inOutCubic(prog(k, 1.3f, 2.47f)))
    withTransform({
      rotate(rot, Offset(cx, cy))
    }) {
      for (j in 0 until rows) {
        val d = abs(j - mid)
        val enter = Ease.outExpo(prog(k, 1.34f + d * 0.035f, 1.85f + d * 0.035f))
        if (enter <= 0f) continue
        val hero = j == mid
        val l = if (hero) heroRow else outlineRow
        val uw = l.size.width.toFloat()
        val dir = if (j % 2 == 0) 1f else -1f
        val y = lerp(cy + (j - mid) * rowH, cy, collapse)
        val speed = 0.55f + d * 0.08f
        var off = ((k * speed * w + j * uw * 0.37f) % uw) * dir
        off += (1f - enter) * w * 1.3f * dir
        var x = -uw + off
        if (dir < 0) x -= uw
        val alpha = if (hero) 1f else lerp(0.6f, 0.12f, d / mid.toFloat()) * (1f - collapse)
        val squashY = if (hero) 1f - collapse * 0.9f else 1f
        withTransform({ scale(1f, squashY, Offset(cx, y)) }) {
          while (x < w * 1.4f) {
            if (x + uw > -w * 0.4f) {
              if (hero) drawCaps(l, italic, italicPx, x, y, Palette.Ember, ax = 0f)
              else drawCaps(l, sans, sansPx, x, y, Palette.Paper, ax = 0f, alpha = alpha, style = Stroke(u * 0.25f))
            }
            x += uw
          }
        }
      }
    }
  }

  // =======================================================================================
  // 02 AGSL SHADERS — an iris opens onto a domain-warped fluid; the word FLOW rides it in
  // difference blend, then the whole field pixelates into the geometry grid.

  private fun DrawScope.shader(t: Float, s: Float) {
    val diag = hypot(w, h) / 2f
    val irisP = Ease.outExpo(prog(s, 0f, 0.75f))
    val cell = w / 7f
    val pix = if (s < 2.1f) 1f else lerp(1f, cell, Ease.inExpo(prog(s, 2.1f, 2.55f)))
    fluid.setFloatUniform("res", w, h)
    fluid.setFloatUniform("time", t)
    fluid.setFloatUniform("iris", irisP * diag * 1.05f + 2f)
    fluid.setFloatUniform("cell", pix)
    fluid.setFloatUniform("fade", 1f)
    if (s < 2.56f) drawRect(fluidBrush)

    if (irisP < 1f) {
      drawCircle(
        Palette.Ember, radius = irisP * diag * 1.05f, center = Offset(cx, cy),
        style = Stroke(u * 2.5f * (1f - irisP) + u * 0.3f),
      )
    }

    // flow — per-letter mask reveal, then rides a sine wave.
    val word = "flow"
    val px = kit.fitWidth(this, word, italic, w * 0.7f)
    val cap = px * italic.capRatio
    val adv = FloatArray(word.length) { kit.layout(this, word[it].toString(), italic, px).size.width.toFloat() }
    val total = adv.sum()
    var x = cx - total / 2f
    clipRect(0f, cy - cap * 0.9f, w, cy + cap * 0.9f) {
      for (i in word.indices) {
        val inP = Ease.outExpo(prog(s, 0.35f + i * 0.07f, 1.05f + i * 0.07f))
        val outP = Ease.inExpo(prog(s, 1.85f + i * 0.05f, 2.15f + i * 0.05f))
        val wave = sin(t * 3.4f + i * 0.9f) * u * 2.2f
        val y = cy + (1f - inP) * cap * 1.3f + outP * cap * 1.4f + wave
        val lc = kit.layout(this, word[i].toString(), italic, px)
        val xc = x + adv[i] / 2f
        rotate(sin(t * 2.6f + i) * 4f, Offset(xc, y)) {
          drawCaps(lc, italic, px, xc, y, Palette.Paper, blend = BlendMode.Difference)
        }
        x += adv[i]
      }
    }
    val cap2 = prog(s, 0.8f, 1.5f) * (1f - prog(s, 1.9f, 2.1f))
    if (cap2 > 0f) {
      val msg = "ANDROID RUNTIMESHADER · AGSL · METABALLS"
      val n = (msg.length * prog(s, 0.8f, 1.5f)).toInt()
      monoLine(msg.take(n), cx, cy + cap * 0.9f + u * 5f, u * 2.4f, Palette.Paper, 0.5f, cap2, BlendMode.Difference)
    }
  }

  // =======================================================================================
  // 03 GEOMETRY — the pixelated fluid becomes a grid of shapes that morph square→circle on a
  // radial wave while a lissajous trim-path chases across the top.

  private fun DrawScope.geometry(t: Float, g: Float) {
    val cell = w / 7f
    val rows = ceil(h / cell).toInt()
    val gcx = 3.5f
    val gcy = h / cell / 2f
    val maxD = hypot(gcx + 2f, gcy + 2f)
    val cam = Ease.inOutCubic(prog(g, 0.3f, 2.4f))

    fluid.setFloatUniform("cell", cell)
    fluid.setFloatUniform("fade", 1f)
    val flatMix = prog(g, 0.15f, 0.55f)

    withTransform({
      rotate(lerp(0f, -18f, cam), Offset(cx, cy))
      scale(lerp(1f, 1.2f, cam), lerp(1f, 1.2f, cam), Offset(cx, cy))
    }) {
      for (r in -3..rows + 2) for (c in -3..9) {
        val mx = c + 0.5f
        val my = r + 0.5f
        val d = hypot(mx - gcx, my - gcy)
        val onScreen = c in 0..6 && r in 0 until rows
        val appear = if (onScreen) 1f else Ease.outBack(prog(g, 0.3f + d * 0.03f, 0.7f + d * 0.03f))
        val settle = Ease.outCubic(prog(g, 0.02f + d * 0.03f, 0.5f + d * 0.03f))

        val phase = g * 4.2f - d * 0.75f
        val crest = 0.5f + 0.5f * sin(phase)
        val morph = 0.5f + 0.5f * sin(phase - 1.2f)
        val waveOn = prog(g, 0.2f, 0.7f)
        val baseScale = lerp(0.52f, 0.9f, crest * crest) * waveOn + 0.6f * (1f - waveOn)
        var sc = lerp(1f, baseScale, settle) * appear
        val exit = prog(g, 1.9f + (maxD - d) * 0.015f, 2.22f + (maxD - d) * 0.015f)
        sc *= clamp01(1f - Ease.inBack(exit, 2.2f))
        if (sc <= 0.001f) continue

        val size = cell * sc
        val corner = size / 2f * lerp(0f, morph, settle)
        val rot = 90f * smoothstep(0.25f, 0.85f, crest) * waveOn + exit * 180f
        val px = mx * cell
        val py = my * cell
        val tl = Offset(px - size / 2f, py - size / 2f)
        val color = lerp(Palette.Slate, Palette.Ember, crest.pow(6f))
        val accent = hash(r * 13f + c * 7f) > 0.93f

        if (flatMix < 1f && onScreen) {
          drawRoundRect(fluidBrush, tl, Size(size, size), CornerRadius(corner))
        }
        rotate(rot, Offset(px, py)) {
          val fill = if (accent) Palette.Volt else color
          drawRoundRect(fill, tl, Size(size, size), CornerRadius(corner), alpha = flatMix)
          drawRoundRect(
            Palette.Paper, tl, Size(size, size), CornerRadius(corner),
            style = Stroke(u * 0.2f), alpha = 0.22f * flatMix,
          )
        }
      }
      lissajous(g)
    }
    caption("JETPACK COMPOSE CANVAS · ALL PROCEDURAL", g, 0.5f, 2.0f, h * 0.84f)
  }

  private fun DrawScope.lissajous(g: Float) {
    val head = Ease.inOutCubic(prog(g, 0.4f, 1.6f))
    val tail = Ease.inOutCubic(prog(g, 0.75f, 2.05f))
    if (head <= tail) return
    val n = 420
    val i0 = (tail * n).toInt()
    val i1 = (head * n).toInt()
    fun pt(i: Int): Offset {
      val v = i / n.toFloat() * 2f * PI.toFloat()
      return Offset(cx + w * 0.36f * sin(3f * v + PI.toFloat() / 2f), cy + h * 0.3f * sin(2f * v))
    }
    path.reset()
    val p0 = pt(i0)
    path.moveTo(p0.x, p0.y)
    for (i in i0 + 1..i1) pt(i).let { path.lineTo(it.x, it.y) }
    drawPath(path, Palette.Volt.copy(alpha = 0.25f), style = Stroke(u * 4f, cap = StrokeCap.Round))
    drawPath(path, Palette.Volt, style = Stroke(u * 1.3f, cap = StrokeCap.Round))
    val hp = pt(i1)
    drawCircle(Palette.Volt.copy(alpha = 0.25f), u * 4.5f, hp)
    drawCircle(Palette.Paper, u * 1.6f, hp)
  }

  // =======================================================================================
  // 04 PARTICLES + 05 IDENTITY — a galaxy of analytic particles forms the name, inhales, and
  // detonates as the solid title slams in. Then the lockup, and the iris closes to the dot
  // that opens the reel — a seamless loop.

  private fun DrawScope.identity(t: Float) {
    val title = "Claude"
    val px = kit.fitWidth(this, title, serif, w * 0.86f)
    val cap = px * serif.capRatio
    val ty = h * 0.44f
    val l = kit.layout(this, title, serif, px)
    val tw = l.size.width.toFloat()
    particles.prepare(w, h, title, serif, px, cx - tw / 2f, ty + cap / 2f)

    val f = t - Timeline.IDENTITY
    val close = Ease.inOutExpo(prog(t, 14.42f, 14.88f))
    val closeR = lerp(hypot(w, h) / 2f, dot, close)
    val zoom = lerp(1f, 0.35f, close)

    scale(zoom, Offset(cx, cy)) {
      // Galaxy core glow.
      val s = t - Timeline.PARTICLES
      val glow = prog(s, 0f, 0.5f) * (1f - prog(s, 1.3f, 2.3f))
      if (glow > 0f) {
        drawCircle(
          Brush.radialGradient(
            listOf(Palette.Ember.copy(alpha = 0.45f * glow), Color.Transparent),
            Offset(cx, cy), w * 0.35f,
          ),
          w * 0.35f, Offset(cx, cy),
        )
      }
      particles.draw(this, s, u)
      caption("3,600 PARTICLES · PURE KOTLIN MATH", s, 0.4f, 2.5f, h * 0.84f)

      if (f >= 0f) {
        val bloom = Ease.outExpo(prog(f, 0f, 0.9f))
        drawCircle(
          Brush.radialGradient(
            listOf(Palette.Ember.copy(alpha = 0.35f), Color.Transparent),
            Offset(cx, ty), w * 0.8f * bloom + 1f,
          ),
          w * 0.8f * bloom + 1f, Offset(cx, ty),
        )

        val slam = Ease.outExpo(prog(f, 0f, 0.6f))
        val sc = lerp(1.14f, 1f, slam)
        scale(sc, Offset(cx, ty)) {
          drawCaps(l, serif, px, cx, ty, Palette.Paper)
          // Light sweep.
          val sweep = prog(f, 0.4f, 1.1f)
          if (sweep > 0f && sweep < 1f) {
            val sxp = lerp(-0.3f * w, 1.3f * w, Ease.inOutCubic(sweep))
            drawCaps(
              l, serif, px, cx, ty,
              Brush.linearGradient(
                0f to Color.Transparent, 0.5f to Palette.Ember, 1f to Color.Transparent,
                start = Offset(sxp - u * 12f, ty - cap), end = Offset(sxp + u * 12f, ty + cap),
              ),
            )
          }
        }

        // Subtitle tracks in from wide letter-spacing.
        val sub = "MOTION DESIGNER"
        val spx = u * 4.2f
        val track = lerp(u * 7f, u * 1.6f, Ease.outExpo(prog(f, 0.1f, 0.9f)))
        val subY = ty + cap / 2f + u * 9f
        trackedLine(sub, cx, subY, spx, track, f - 0.1f)

        val bar = Ease.inOutExpo(prog(f, 0.4f, 0.9f))
        if (bar > 0f) {
          drawRect(Palette.Ember, Offset(cx - tw * bar / 2f, subY + u * 5f), Size(tw * bar, u * 0.6f))
        }

        val tpx = u * 2.8f
        val lines = listOf("JETPACK COMPOSE · NATIVE ANDROID", "AVAILABLE FOR HIRE")
        var chars = ((f - 0.7f) * 60f).toInt()
        var ly = subY + u * 12f
        val adv = tpx * mono.advanceRatio
        var cursor = Offset(cx, ly)
        for ((li, line) in lines.withIndex()) {
          if (chars <= 0) break
          val n = minOf(chars, line.length)
          monoLine(line.take(n), cx, ly, tpx, if (li == 1) Palette.Volt else Palette.Paper, 0.5f, 1f, full = line)
          cursor = Offset(cx - line.length * adv / 2f + n * adv + u * 0.6f, ly)
          chars -= line.length + 4
          ly += u * 5f
        }
        if (f > 0.7f && sin(t * 18f) > 0f) {
          drawRect(Palette.Volt, Offset(cursor.x, cursor.y - tpx * 0.45f), Size(adv * 0.9f, tpx * 0.9f))
        }

        val flash = 1f - prog(f, 0f, 0.16f)
        if (flash > 0f) drawRect(Palette.Paper, alpha = flash * 0.9f)
      }
    }

    if (close > 0f) {
      path.reset()
      path.fillType = PathFillType.EvenOdd
      path.addRect(Rect(-w, -h, w * 2f, h * 2f))
      path.addOval(Rect(Offset(cx, cy), closeR))
      drawPath(path, Palette.Ink)
      path.fillType = PathFillType.NonZero
      drawCircle(Palette.Ember, closeR + u * 1.5f, Offset(cx, cy), style = Stroke(u * 0.8f), alpha = 1f - close * close)
      val white = prog(t, 14.66f, 14.88f)
      if (white > 0f) drawCircle(Palette.Paper, closeR, Offset(cx, cy), alpha = white)
    }
  }

  // =======================================================================================
  // HUD — viewfinder, timecode, section labels, progress. Drawn in difference blend so it
  // reads on paper and ink alike.

  private fun DrawScope.hud(t: Float) {
    val a = prog(t, 0.25f, 0.6f) * (1f - prog(t, 14.3f, 14.5f))
    if (a <= 0f) return
    val m = u * 5f
    val top = u * 11f
    val bottom = h - u * 10f
    val c = Palette.Paper.copy(alpha = 0.85f * a)
    val bl = BlendMode.Difference
    val sw = u * 0.3f
    val arm = u * 5f

    // Corner brackets.
    for ((x, y, dx, dy) in listOf(
      listOf(m, top - u * 3f, 1f, 1f), listOf(w - m, top - u * 3f, -1f, 1f),
      listOf(m, bottom + u * 3f, 1f, -1f), listOf(w - m, bottom + u * 3f, -1f, -1f),
    )) {
      drawLine(c, Offset(x, y), Offset(x + arm * dx, y), sw, blendMode = bl)
      drawLine(c, Offset(x, y), Offset(x, y + arm * dy), sw, blendMode = bl)
    }

    val mpx = u * 2.3f
    val ty = top + u * 3f
    monoLine("CLAUDE / MOTION REEL", m + u * 2f, ty, mpx, Palette.Paper, 0f, a * 0.85f, bl)

    val frames = (t * 30f).toInt()
    val tc = "00:00:%02d:%02d".format(frames / 30, frames % 30)
    monoLine(tc, w - m - u * 2f, ty, mpx, Palette.Paper, 1f, a * 0.85f, bl)
    val tcw = tc.length * mpx * mono.advanceRatio
    if ((t * 2f).toInt() % 2 == 0) drawCircle(Palette.Ember, u * 0.8f, Offset(w - m - u * 2f - tcw - u * 2.5f, ty), alpha = a)

    // Progress bar with section ticks.
    val barY = bottom - u * 1.5f
    val x0 = m + u * 2f
    val x1 = w - m - u * 2f
    drawLine(c.copy(alpha = 0.3f * a), Offset(x0, barY), Offset(x1, barY), sw, blendMode = bl)
    drawLine(c, Offset(x0, barY), Offset(lerp(x0, x1, t / Timeline.LENGTH), barY), sw * 2f, blendMode = bl)
    for ((st, _) in Timeline.sections) {
      val x = lerp(x0, x1, st / Timeline.LENGTH)
      drawLine(c, Offset(x, barY - u * 0.8f), Offset(x, barY + u * 0.8f), sw, blendMode = bl)
    }

    // Rolling section label.
    val idx = Timeline.sections.indexOfLast { t >= it.first }
    val (start, label) = Timeline.sections[idx]
    val prev = Timeline.sections.getOrNull(idx - 1)?.second
    val roll = Ease.outExpo(prog(t, start, start + 0.45f))
    val ly = barY - u * 4f
    val lh = u * 4f
    clipRect(0f, ly - lh / 2f, w, ly + lh / 2f) {
      if (prev != null && roll < 1f) monoLine(prev, x0, ly - roll * lh, mpx, Palette.Paper, 0f, a * (1f - roll), bl, bold = true)
      monoLine(label, x0, ly + (1f - roll) * lh, mpx, Palette.Paper, 0f, a * roll.coerceAtLeast(if (prev == null) 1f else 0f), bl, bold = true)
    }
    monoLine("COMPOSE · AGSL · 60P", x1, ly, mpx, Palette.Paper, 1f, a * 0.6f, bl)
  }

  /** Typed mono caption with an ember bullet; types on at 50 cps, then fades before [end]. */
  private fun DrawScope.caption(text: String, time: Float, start: Float, end: Float, y: Float) {
    val n = ((time - start) * 50f).toInt().coerceIn(0, text.length)
    val a = 1f - prog(time, end - 0.2f, end)
    if (n == 0 || a <= 0f) return
    val px = u * 2.4f
    val x0 = cx - text.length * px * mono.advanceRatio / 2f
    drawRect(Palette.Ember, Offset(x0 - u * 3f, y - u * 0.6f), Size(u * 1.2f, u * 1.2f), alpha = a)
    monoLine(text.take(n), cx, y, px, Palette.Paper, 0.5f, a, BlendMode.Difference, full = text)
  }

  // ---------------------------------------------------------------------------------------
  // Monospace helpers: glyphs are laid out on a fixed grid from cached single-char layouts,
  // so changing strings (timecode, typewriters) never re-measure.

  private fun DrawScope.monoLine(
    text: String,
    x: Float,
    y: Float,
    px: Float,
    color: Color,
    ax: Float,
    alpha: Float,
    blend: BlendMode = BlendMode.SrcOver,
    bold: Boolean = false,
    full: String = text,
  ) {
    if (alpha <= 0f) return
    val f = if (bold) monoBold else mono
    val adv = px * f.advanceRatio
    var cx0 = x - full.length * adv * ax
    for (ch in text) {
      if (ch != ' ') drawCaps(kit.layout(this, ch.toString(), f, px), f, px, cx0, y, color, ax = 0f, alpha = alpha, blend = blend)
      cx0 += adv
    }
  }

  private fun DrawScope.trackedLine(text: String, x: Float, y: Float, px: Float, track: Float, time: Float) {
    val adv = px * monoBold.advanceRatio + track
    var x0 = x - (text.length * adv - track) / 2f
    for ((i, ch) in text.withIndex()) {
      val flick = prog(time, hash(i * 3.7f) * 0.35f, hash(i * 3.7f) * 0.35f + 0.12f)
      val on = if (flick < 1f && sin(time * 90f + i) > 0f) flick * 0.4f else flick
      if (ch != ' ' && on > 0f) {
        drawCaps(kit.layout(this, ch.toString(), monoBold, px), monoBold, px, x0, y, Palette.Paper, ax = 0f, alpha = on)
      }
      x0 += adv
    }
  }
}
