package com.example.ridemate_api_test.feature.group.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppDialog
import com.example.ridemate_api_test.core.designsystem.component.DialogButton
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.InputBorder
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary

private const val NAME_MAX_LENGTH = 100
private const val DESC_MAX_LENGTH = 500
private const val MAX_MEMBERS_MIN = 2
private const val MAX_MEMBERS_MAX = 50

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, maxMembers: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxMembers by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var descError by remember { mutableStateOf<String?>(null) }
    var maxError by remember { mutableStateOf<String?>(null) }

    // stringResource 는 Composable 컨텍스트에서만 호출할 수 있으므로 콜백 밖에서 미리 가져온다.
    val nameRequiredMsg = stringResource(R.string.group_name_required)
    val nameTooLongMsg = stringResource(R.string.group_name_too_long)
    val descTooLongMsg = stringResource(R.string.group_desc_too_long)
    val maxRequiredMsg = stringResource(R.string.group_max_required)
    val maxInvalidMsg = stringResource(R.string.group_max_invalid)

    AppDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.group_create_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            FormLabel(stringResource(R.string.group_name_label))
            Spacer(Modifier.height(8.dp))
            DialogField(
                value = name,
                onValueChange = {
                    name = it.take(NAME_MAX_LENGTH)
                    nameError = null
                },
                hint = stringResource(R.string.group_name_hint),
                error = nameError,
            )

            Spacer(Modifier.height(16.dp))
            FormLabel(stringResource(R.string.group_desc_label))
            Spacer(Modifier.height(8.dp))
            DialogField(
                value = description,
                onValueChange = {
                    description = it.take(DESC_MAX_LENGTH)
                    descError = null
                },
                hint = stringResource(R.string.group_desc_hint),
                minHeight = 110.dp,
                multiline = true,
                error = descError,
            )

            Spacer(Modifier.height(16.dp))
            FormLabel(stringResource(R.string.group_max_label))
            Spacer(Modifier.height(8.dp))
            DialogField(
                value = maxMembers,
                onValueChange = {
                    maxMembers = it.filter { ch -> ch.isDigit() }.take(2)
                    maxError = null
                },
                hint = stringResource(R.string.group_max_hint),
                keyboardType = KeyboardType.Number,
                error = maxError,
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(
                    text = stringResource(R.string.cancel),
                    container = SoftCard,
                    contentColor = TextPrimary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogButton(
                    text = stringResource(R.string.group_create),
                    container = PrimaryBlue,
                    contentColor = SurfaceWhite,
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedDesc = description.trim()
                        val maxRaw = maxMembers.trim()
                        val maxValue = maxRaw.toIntOrNull()

                        nameError = when {
                            trimmedName.isEmpty() -> nameRequiredMsg
                            trimmedName.length > NAME_MAX_LENGTH -> nameTooLongMsg
                            else -> null
                        }
                        descError = if (trimmedDesc.length > DESC_MAX_LENGTH) descTooLongMsg else null
                        maxError = when {
                            maxRaw.isEmpty() -> maxRequiredMsg
                            maxValue == null || maxValue !in MAX_MEMBERS_MIN..MAX_MEMBERS_MAX -> maxInvalidMsg
                            else -> null
                        }

                        if (nameError == null && descError == null && maxError == null && maxValue != null) {
                            onCreate(trimmedName, trimmedDesc, maxValue)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
}

@Composable
private fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp,
    multiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = !multiline,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.None,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextPrimary,
            fontSize = 15.sp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(SurfaceWhite, RoundedCornerShape(12.dp))
            .border(1.dp, if (error != null) DangerRed else InputBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        decorationBox = { inner ->
            Column {
                if (value.isEmpty()) {
                    Text(hint, color = TextSecondary, fontSize = 15.sp)
                }
                inner()
            }
        },
    )
    if (error != null) {
        Spacer(Modifier.height(6.dp))
        Text(error, color = DangerRed, fontSize = 13.sp)
    }
}
