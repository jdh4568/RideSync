package com.example.ridemate_api_test.feature.ride.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.RideCard
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary

enum class RideRole { LEADER, FOLLOWER }

@Composable
fun RideHeader(
    role: RideRole,
    groupName: String,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceWhite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bike, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(groupName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.riding_now_format, memberCount),
                fontSize = 13.sp,
                color = TextSecondary,
            )
        }
        RoleBadge(role)
    }
}

@Composable
private fun RoleBadge(role: RideRole) {
    val (container, content, icon) = when (role) {
        RideRole.LEADER -> Triple(PrimaryBlue, SurfaceWhite, AppIcons.Crown)
        RideRole.FOLLOWER -> Triple(AccentBlue.copy(alpha = 0.15f), AccentBlue, AppIcons.Group)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(if (role == RideRole.LEADER) R.string.role_leader else R.string.role_follower),
            color = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun RideStatsCard(
    speed: String,
    distance: String,
    modifier: Modifier = Modifier,
) {
    RideCard(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 20.dp)) {
            StatColumn(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.current_speed),
                value = speed,
                unit = stringResource(R.string.speed_unit),
            )
            Box(
                Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .background(androidx.compose.ui.graphics.Color.LightGray),
            )
            StatColumn(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.moving_distance),
                value = distance,
                unit = stringResource(R.string.distance_unit),
            )
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 13.sp, color = TextSecondary)
        }
    }
}
