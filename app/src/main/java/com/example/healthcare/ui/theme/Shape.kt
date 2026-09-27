package com.example.healthcare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val WellnessShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(17.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

object WellnessSpacing {
    val ScreenHorizontal = 16.dp
    val ScreenVertical = 16.dp
    val Section = 24.dp
    val CardGap = 12.dp
    val CardContent = 16.dp
    val Compact = 7.dp
}
