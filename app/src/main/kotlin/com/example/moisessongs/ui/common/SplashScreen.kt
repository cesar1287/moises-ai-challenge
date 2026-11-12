package com.example.moisessongs.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.moisessongs.R

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
        colors = listOf(Color.Black, Color(0xFF00323B)), start = Offset.Zero,
        end = Offset(1800f, -900f))), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.musical_note), null, Modifier.size(100.dp))
    }
}
