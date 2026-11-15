package com.example.moisessongs.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.moisessongs.R
import com.example.moisessongs.ui.theme.AppSizes
import com.example.moisessongs.ui.theme.SplashGradient

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(SplashGradient), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.musical_note), null, Modifier.size(AppSizes.splashArtwork))
    }
}
