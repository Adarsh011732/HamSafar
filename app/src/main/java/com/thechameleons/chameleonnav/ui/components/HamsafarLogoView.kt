package com.thechameleons.chameleonnav.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thechameleons.chameleonnav.R
import com.thechameleons.chameleonnav.ui.theme.CardBorder

/**
 * Animated Hamsafar Logo Component.
 * Plays the new Hamsafar video animation smoothly in a loop using hardware-accelerated TextureView,
 * with an instant static-frame fallback.
 */
@Composable
fun HamsafarLogoView(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showBorder: Boolean = true
) {
    var isVideoReady by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0D0E12))
            .then(
                if (showBorder) Modifier.border(1.dp, CardBorder, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // High-fidelity looping video player for animated logo
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        var mediaPlayer: MediaPlayer? = null

                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val surface = Surface(surfaceTexture)
                                mediaPlayer = MediaPlayer.create(ctx, R.raw.hamsafar_logo).apply {
                                    setSurface(surface)
                                    isLooping = true
                                    setVolume(0f, 0f) // silent
                                    setOnPreparedListener {
                                        isVideoReady = true
                                        start()
                                    }
                                }
                            } catch (_: Exception) {
                                isVideoReady = false
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            try {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                            } catch (_: Exception) {}
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Instant crisp static logo displayed until video starts or as fallback
        if (!isVideoReady) {
            Image(
                painter = painterResource(id = R.drawable.hamsafar_logo),
                contentDescription = "Hamsafar Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
