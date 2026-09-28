package com.example.showreel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.withFrameNanos

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.getInsetsController(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Debug hooks for frame inspection: `--ef seek 7.2 --ez freeze true`.
    val seek = intent.getFloatExtra("seek", 0f)
    val freeze = intent.getBooleanExtra("freeze", false)
    val post = !intent.getBooleanExtra("nopost", false)
    setContent { Showreel(seek, freeze, post) }
  }
}

/** Plays the reel on a loop; tap anywhere to restart from the top. */
@Composable
fun Showreel(seek: Float, freeze: Boolean, post: Boolean = true) {
  val context = LocalContext.current
  val measurer = rememberTextMeasurer(cacheSize = 0)
  val reel = remember { Reel(context, measurer) }
  var time by remember { mutableFloatStateOf(seek) }
  var restarts by remember { mutableIntStateOf(0) }

  LaunchedEffect(restarts) {
    if (freeze) return@LaunchedEffect
    val from = if (restarts == 0) seek else 0f
    val start = withFrameNanos { it }
    while (true) {
      withFrameNanos { now -> time = from + (now - start) / 1_000_000_000f }
    }
  }

  Spacer(
    Modifier
      .fillMaxSize()
      .pointerInput(Unit) { detectTapGestures { restarts++ } }
      .graphicsLayer { if (post) reel.applyPost(this, time % Timeline.LENGTH) }
      .drawBehind { reel.render(this, time % Timeline.LENGTH) },
  )
}
