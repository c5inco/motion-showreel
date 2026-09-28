package com.example.showreel

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Palette — ink, paper and three loud accents. */
object Palette {
  val Ink = Color(0xFF09090B)
  val Paper = Color(0xFFF3EFE6)
  val Ember = Color(0xFFFF4A1C)
  val Cobalt = Color(0xFF3D5BFF)
  val Volt = Color(0xFFD7FF3A)
  val Lilac = Color(0xFFB69CFF)
  val Slate = Color(0xFF17171D)
}

/** Master timeline, in seconds. The reel loops every [LENGTH] seconds. */
object Timeline {
  const val LENGTH = 15f
  const val KINETIC = 2.0f
  const val SHADER = 4.45f
  const val GEOMETRY = 7.0f
  const val PARTICLES = 9.5f
  const val IDENTITY = 12.6f

  val sections = listOf(
    0f to "00  OPEN",
    KINETIC to "01  KINETIC TYPE",
    SHADER to "02  AGSL SHADERS",
    GEOMETRY to "03  GEOMETRY",
    PARTICLES to "04  PARTICLES",
    IDENTITY to "05  IDENTITY",
  )

  /** (time, strength) — beats that kick the camera and the lens. */
  val impacts = floatArrayOf(
    0.30f, 0.45f,
    1.12f, 0.35f,
    2.28f, 0.25f,
    2.40f, 0.25f,
    2.52f, 0.30f,
    2.85f, 1.00f,
    3.42f, 0.55f,
    4.47f, 0.70f,
    7.00f, 0.35f,
    9.52f, 0.60f,
    12.60f, 1.25f,
  )

  /** Short windows of RGB-slice glitch at hard transitions. */
  val glitches = floatArrayOf(
    1.90f, 2.04f,
    4.38f, 4.50f,
    9.40f, 9.56f,
    12.56f, 12.68f,
  )
}

fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** Normalised progress of [t] through the window [a, b]. */
fun prog(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun lerp(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, clamp01(t))

fun smoothstep(a: Float, b: Float, x: Float): Float {
  val t = clamp01((x - a) / (b - a))
  return t * t * (3f - 2f * t)
}

object Ease {
  fun inCubic(x: Float) = x * x * x
  fun outCubic(x: Float) = 1f - (1f - x).pow(3)
  fun inOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
  fun outQuint(x: Float) = 1f - (1f - x).pow(5)
  fun inOutQuint(x: Float) = if (x < 0.5f) 16f * x.pow(5) else 1f - (-2f * x + 2f).pow(5) / 2f
  fun inExpo(x: Float) = if (x <= 0f) 0f else 2f.pow(10f * x - 10f)
  fun outExpo(x: Float) = if (x >= 1f) 1f else 1f - 2f.pow(-10f * x)
  fun inOutExpo(x: Float) = when {
    x <= 0f -> 0f
    x >= 1f -> 1f
    x < 0.5f -> 2f.pow(20f * x - 10f) / 2f
    else -> (2f - 2f.pow(-20f * x + 10f)) / 2f
  }

  fun outBack(x: Float, s: Float = 1.70158f): Float {
    val c3 = s + 1f
    return 1f + c3 * (x - 1f).pow(3) + s * (x - 1f).pow(2)
  }

  fun inBack(x: Float, s: Float = 1.70158f) = (s + 1f) * x * x * x - s * x * x

  fun outElastic(x: Float): Float = when {
    x <= 0f -> 0f
    x >= 1f -> 1f
    else -> 2f.pow(-10f * x) * sin((x * 10f - 0.75f) * (2f * PI.toFloat() / 3f)) + 1f
  }

  /** Critically-under-damped spring, settles to 1. */
  fun spring(x: Float, bounce: Float = 7f, damping: Float = 5.5f): Float =
    if (x <= 0f) 0f else 1f - exp(-damping * x) * cos(bounce * x)
}

/** Cheap deterministic hash in [0, 1). */
fun hash(n: Float): Float {
  val s = sin(n * 127.1f + 311.7f) * 43758.547f
  return s - kotlin.math.floor(s)
}
