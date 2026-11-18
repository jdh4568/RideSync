package com.example.ridemate_api_test.feature.ride.presentation.follower

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme

@Composable
fun FollowerRideScreen() {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ride_follower_screen), textAlign = TextAlign.Center)
        }
    }
}

@Preview(name = "Follower ride", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FollowerRidePreview() { HomeTheme { FollowerRideScreen() } }
