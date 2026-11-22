package com.example.ridemate_api_test.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.BrandBlue
import com.example.ridemate_api_test.core.designsystem.theme.DarkCharcoal
import com.example.ridemate_api_test.core.designsystem.theme.DisabledGrey
import com.example.ridemate_api_test.core.designsystem.theme.InputBorder
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary

/** 전체 배경: 하늘색 기본 위에 Figma 월페이퍼를 상단부터 채운다. */
@Composable
fun RideMateBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ridemate_api_test.core.designsystem.theme.SkyBackground),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.bg_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        content()
    }
}

/** 상단 중앙 브랜드 로고: Ride(검정) + Mate(파랑). */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    fontSize: Int = 40,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Ride",
            color = Color.Black,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-1.4).sp,
        )
        Text(
            text = "Mate",
            color = BrandBlue,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-1.4).sp,
        )
    }
}

/** 아이디/비밀번호 등에 쓰이는 둥근 입력창. */
@Composable
fun RideTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) AccentBlue else InputBorder
    Row(
        modifier = modifier
            .height(59.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(30.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = LocalTextStyle.current.copy(
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextSecondary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                inner()
            },
        )
        trailing?.invoke(this)
    }
}

/** 기본 파란색 채움 버튼. */
@Composable
fun RidePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RideButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = if (enabled) PrimaryBlue else DisabledGrey,
        contentColor = SurfaceWhite,
    )
}

/** 다크 채움 버튼 (로그인 / 가입하기). */
@Composable
fun RideDarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RideButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = if (enabled) DarkCharcoal else DisabledGrey,
        contentColor = SurfaceWhite,
    )
}

/** 흰색 외곽선 버튼 (회원가입 등). */
@Composable
fun RideOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = TextPrimary,
) {
    Box(
        modifier = modifier
            .height(59.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .border(1.dp, InputBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else DisabledGrey,
            fontSize = 25.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    contentColor: Color,
) {
    Box(
        modifier = modifier
            .height(59.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(1.dp, InputBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 25.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/** 그룹 카드 등에 쓰이는 흰색 둥근 카드. */
@Composable
fun RideCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)),
        color = SurfaceWhite,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        content()
    }
}

/** 상단 뒤로 가기 + 중앙 제목 툴바. */
@Composable
fun RideTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Back, contentDescription = null, tint = TextPrimary)
            }
        }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
