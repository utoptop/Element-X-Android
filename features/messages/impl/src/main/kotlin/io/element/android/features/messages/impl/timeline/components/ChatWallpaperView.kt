/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme

data class ChatWallpaperState(
    val wallpaper: String = "telegram_classic",
    val customUri: String? = null,
)

val LocalChatWallpaper = compositionLocalOf { ChatWallpaperState() }

@Composable
fun ChatWallpaperView(
    state: ChatWallpaperState,
    modifier: Modifier = Modifier,
) {
    val isLight = ElementTheme.isLightTheme
    when (state.wallpaper) {
        "telegram_classic" -> {
            if (isLight) {
                // Telegram Classic light sage background with subtle pattern
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Color(0xFFE4ECE6))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val patternColor = Color(0x0C000000)
                        val step = 48f
                        var x = 0f
                        while (x < size.width) {
                            var y = 0f
                            while (y < size.height) {
                                drawCircle(
                                    color = patternColor,
                                    radius = 2.5f,
                                    center = Offset(x + (if ((y / step).toInt() % 2 == 0) 0f else step / 2f), y),
                                )
                                y += step
                            }
                            x += step
                        }
                    }
                }
            } else {
                // Telegram Classic dark slate gradient
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0E1621),
                                    Color(0xFF17212B),
                                )
                            )
                        )
                )
            }
        }
        "ocean" -> {
            val colors = if (isLight) {
                listOf(Color(0xFFE6F2FA), Color(0xFFD2E6F5))
            } else {
                listOf(Color(0xFF0B192A), Color(0xFF13253B))
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors))
            )
        }
        "mint" -> {
            val colors = if (isLight) {
                listOf(Color(0xFFEAF5EE), Color(0xFFD3EFE0))
            } else {
                listOf(Color(0xFF091E16), Color(0xFF122C21))
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors))
            )
        }
        "dark_slate" -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF101317), Color(0xFF1A1F26))
                        )
                    )
            )
        }
        "custom" -> {
            if (!state.customUri.isNullOrEmpty()) {
                Box(modifier = modifier.fillMaxSize()) {
                    AsyncImage(
                        model = state.customUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Dimming scrim for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isLight) Color(0x1F000000) else Color(0x4D000000))
                    )
                }
            } else {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(ElementTheme.colors.bgCanvasDefault)
                )
            }
        }
        else -> {
            // "none" or fallback
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgCanvasDefault)
            )
        }
    }
}
