package com.example.showreel

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt

/** A font plus the metrics needed to optically centre caps. [tracking] is in ems. */
class Face(val family: FontFamily, val typeface: Typeface, val tracking: Float = 0f) {
  /** Cap height as a fraction of the em. */
  val capRatio: Float = run {
    val p = Paint().apply { typeface = this@Face.typeface; textSize = 1000f }
    val r = Rect()
    p.getTextBounds("H", 0, 1, r)
    r.height() / 1000f
  }

  /** Advance of "0" as a fraction of the em — used as the monospace grid. */
  val advanceRatio: Float = Paint().run {
    typeface = this@Face.typeface
    textSize = 1000f
    measureText("0") / 1000f
  }
}

/**
 * Caches text layouts by (text, face, size). Sizes are rounded to whole pixels so that
 * animated text is animated with transforms — never by re-measuring every frame.
 */
class TextKit(private val measurer: TextMeasurer) {
  private data class Key(val text: String, val face: Face, val px: Int)

  private val cache = HashMap<Key, TextLayoutResult>()

  fun layout(density: Density, text: String, face: Face, px: Float): TextLayoutResult {
    val key = Key(text, face, px.roundToInt().coerceAtLeast(1))
    return cache.getOrPut(key) {
      val style = TextStyle(
        fontFamily = face.family,
        fontSize = with(density) { key.px.toFloat().toSp() },
        letterSpacing = face.tracking.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
      )
      measurer.measure(text, style, softWrap = false, maxLines = 1, density = density)
    }
  }

  /** Pixel size at which [text] in [face] spans exactly [width]. */
  fun fitWidth(density: Density, text: String, face: Face, width: Float): Float {
    val ref = layout(density, text, face, 200f)
    return 200f * width / ref.size.width
  }
}

/**
 * Draws [layout] so its cap-height centre sits on [y] and horizontal anchor [ax]
 * (0 = left, 0.5 = centre, 1 = right) sits on [x].
 */
fun DrawScope.drawCaps(
  layout: TextLayoutResult,
  face: Face,
  px: Float,
  x: Float,
  y: Float,
  color: Color,
  ax: Float = 0.5f,
  alpha: Float = 1f,
  style: DrawStyle = Fill,
  blend: BlendMode = BlendMode.SrcOver,
) {
  val cap = px.roundToInt() * face.capRatio
  val topLeft = Offset(x - layout.size.width * ax, y - layout.firstBaseline + cap / 2f)
  drawText(layout, color, topLeft, alpha.coerceIn(0f, 1f), drawStyle = style, blendMode = blend)
}

fun DrawScope.drawCaps(
  layout: TextLayoutResult,
  face: Face,
  px: Float,
  x: Float,
  y: Float,
  brush: Brush,
  ax: Float = 0.5f,
  alpha: Float = 1f,
  blend: BlendMode = BlendMode.SrcOver,
) {
  val cap = px.roundToInt() * face.capRatio
  val topLeft = Offset(x - layout.size.width * ax, y - layout.firstBaseline + cap / 2f)
  drawText(layout, brush, topLeft, alpha.coerceIn(0f, 1f), drawStyle = Fill, blendMode = blend)
}
