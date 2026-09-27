package com.example.healthcare.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.example.healthcare.ui.RecommendationImageResolver

/** 등록 추천은 stable ID 전용 사진을 사용하고, 카탈로그 밖 음식만 안전 fallback을 표시합니다. */
@Composable
fun RecommendationPhoto(
    templateId: String?,
    foodName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val imageRes = RecommendationImageResolver.resolve(templateId, foodName)
    if (imageRes != null) {
        Image(
            bitmap = ImageBitmap.imageResource(imageRes),
            contentDescription = contentDescription,
            contentScale = contentScale,
            filterQuality = FilterQuality.High,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.RestaurantMenu,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(.34f)
            )
        }
    }
}
