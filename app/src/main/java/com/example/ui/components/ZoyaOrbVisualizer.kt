package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.gemini.ConnectionStatus
import com.example.ui.theme.ZoyaAccentCyan
import com.example.ui.theme.ZoyaPrimaryMagenta
import com.example.ui.theme.ZoyaSecondaryViolet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ZoyaOrbVisualizer(
    connectionStatus: ConnectionStatus,
    isSpeaking: Boolean,
    isListening: Boolean,
    zoyaAmplitude: Float,
    userAmplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")

    // Idle breathing rotation & pulse
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Dynamic scale based on speaking or mic amplitude
    val activeScale = when {
        isSpeaking -> 1f + (zoyaAmplitude * 0.35f)
        isListening -> 1f + (userAmplitude * 0.3f)
        connectionStatus == ConnectionStatus.CONNECTED -> idlePulse
        else -> 0.95f
    }

    val primaryColor = when (connectionStatus) {
        ConnectionStatus.SPEAKING -> ZoyaPrimaryMagenta
        ConnectionStatus.LISTENING -> ZoyaSecondaryViolet
        ConnectionStatus.CONNECTED -> ZoyaPrimaryMagenta
        ConnectionStatus.CONNECTING -> ZoyaAccentCyan
        ConnectionStatus.ERROR -> Color(0xFFEF4444)
        else -> Color(0xFF6B7280)
    }

    val glowGradient = Brush.radialGradient(
        colors = listOf(
            primaryColor.copy(alpha = if (isSpeaking || isListening) 0.55f else 0.25f),
            ZoyaSecondaryViolet.copy(alpha = 0.15f),
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .size(260.dp)
            .drawBehind {
                drawCircle(brush = glowGradient, radius = size.minDimension / 1.7f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 130.dp),
                onClick = onClick
            )
            .testTag("zoya_orb_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        // Outer soundwave canvas
        Canvas(modifier = Modifier.fillMaxSize().scale(activeScale)) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2.35f

            // Outer glowing ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = baseRadius * 1.08f,
                style = Stroke(width = 2.dp.toPx())
            )

            // Animated wave petals/spikes
            val waveCount = 18
            val currentAmp = if (isSpeaking) zoyaAmplitude else if (isListening) userAmplitude else 0.05f
            for (i in 0 until waveCount) {
                val angleRad = Math.toRadians((i * (360.0 / waveCount) + rotationAngle)).toFloat()
                val waveLength = (15.dp.toPx() + (currentAmp * 35.dp.toPx())) * ((i % 3 + 1) * 0.45f)

                val startX = centerOffset.x + (baseRadius * cos(angleRad))
                val startY = centerOffset.y + (baseRadius * sin(angleRad))
                val endX = centerOffset.x + ((baseRadius + waveLength) * cos(angleRad))
                val endY = centerOffset.y + ((baseRadius + waveLength) * sin(angleRad))

                drawLine(
                    brush = Brush.linearGradient(
                        listOf(primaryColor.copy(alpha = 0.8f), ZoyaSecondaryViolet.copy(alpha = 0.2f))
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Inner Portrait Avatar Card
        Box(
            modifier = Modifier
                .size(175.dp)
                .scale(if (isSpeaking) 1.03f else 1.0f)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            ZoyaPrimaryMagenta,
                            ZoyaSecondaryViolet,
                            ZoyaAccentCyan,
                            ZoyaPrimaryMagenta
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.zoya_portrait),
                contentDescription = "Zoya Sassy AI Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}
