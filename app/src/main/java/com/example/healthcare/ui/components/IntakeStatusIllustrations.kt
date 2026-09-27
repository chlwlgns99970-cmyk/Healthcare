package com.example.healthcare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.example.healthcare.ui.theme.Butter
import com.example.healthcare.ui.theme.Coral
import com.example.healthcare.ui.theme.EditorialText
import com.example.healthcare.ui.theme.Lime
import com.example.healthcare.ui.theme.Mint
import com.example.healthcare.ui.theme.Sky

/** Decorative, local Canvas art. Status text beside it carries the accessible meaning. */
@Composable
fun OverCharacterIllustration(modifier: Modifier = Modifier) =
    StatusCharacter(modifier, CharacterKind.ROUND, Coral)

@Composable
fun BalancedCharacterIllustration(modifier: Modifier = Modifier) =
    StatusCharacter(modifier, CharacterKind.BALANCED, Sky)

@Composable
fun SlimCharacterIllustration(modifier: Modifier = Modifier) =
    StatusCharacter(modifier, CharacterKind.LIGHT, Mint)

@Composable
fun WarningCharacterIllustration(modifier: Modifier = Modifier) =
    StatusCharacter(modifier, CharacterKind.WARNING, Butter)

private enum class CharacterKind { ROUND, BALANCED, LIGHT, WARNING }

@Composable
private fun StatusCharacter(modifier: Modifier, kind: CharacterKind, bodyColor: Color) {
    Canvas(modifier.size(108.dp)) {
        val width = size.width
        val height = size.height
        val ink = EditorialText
        if (kind == CharacterKind.ROUND) {
            drawCircle(bodyColor, radius = width * 0.19f, center = Offset(width * 0.26f, height * 0.24f))
            drawCircle(bodyColor, radius = width * 0.19f, center = Offset(width * 0.74f, height * 0.24f))
        } else if (kind == CharacterKind.LIGHT) {
            drawOval(Lime, topLeft = Offset(width * 0.64f, height * 0.06f),
                size = Size(width * 0.18f, height * 0.30f))
        }
        val bodyLeft = if (kind == CharacterKind.LIGHT) width * 0.18f else width * 0.10f
        val bodyTop = if (kind == CharacterKind.LIGHT) height * 0.13f else height * 0.17f
        val bodyWidth = if (kind == CharacterKind.LIGHT) width * 0.64f else width * 0.80f
        val bodyHeight = if (kind == CharacterKind.LIGHT) height * 0.80f else height * 0.73f
        drawOval(bodyColor, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyWidth, bodyHeight))
        drawCircle(ink, radius = width * 0.026f, center = Offset(width * 0.38f, height * 0.49f))
        drawCircle(ink, radius = width * 0.026f, center = Offset(width * 0.62f, height * 0.49f))
        when (kind) {
            CharacterKind.ROUND -> {
                drawOval(Color(0xFFFFC1B7), topLeft = Offset(width * 0.35f, height * 0.57f),
                    size = Size(width * 0.30f, height * 0.18f))
                drawCircle(ink, radius = width * 0.015f, center = Offset(width * 0.45f, height * 0.66f))
                drawCircle(ink, radius = width * 0.015f, center = Offset(width * 0.55f, height * 0.66f))
            }
            CharacterKind.WARNING -> {
                drawLine(ink, Offset(width * 0.39f, height * 0.65f), Offset(width * 0.61f, height * 0.65f),
                    strokeWidth = width * 0.025f)
                drawCircle(Color.White, radius = width * 0.07f,
                    center = Offset(width * 0.79f, height * 0.22f))
                drawLine(ink, Offset(width * 0.79f, height * 0.17f), Offset(width * 0.79f, height * 0.23f),
                    strokeWidth = width * 0.018f)
                drawCircle(ink, radius = width * 0.009f, center = Offset(width * 0.79f, height * 0.26f))
            }
            CharacterKind.BALANCED, CharacterKind.LIGHT -> {
                drawArc(ink, startAngle = 12f, sweepAngle = 156f, useCenter = false,
                    topLeft = Offset(width * 0.39f, height * 0.52f),
                    size = Size(width * 0.22f, height * 0.17f),
                    style = Stroke(width = width * 0.024f))
            }
        }
    }
}
