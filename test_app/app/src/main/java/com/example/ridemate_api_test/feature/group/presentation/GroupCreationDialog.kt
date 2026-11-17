package com.example.ridemate_api_test.feature.group.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.feature.group.domain.model.GroupProblem

@Composable
internal fun GroupCreationDialog(
    form: GroupCreationForm, isCreating: Boolean,
    onChange: (GroupCreationForm) -> Unit, onDismiss: () -> Unit, onCreate: () -> Unit,
    errors: GroupFormErrors = GroupFormErrors(), problem: GroupProblem? = null, uncertain: Boolean = false,
) {
    Dialog(onDismissRequest = { if (!isCreating) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !isCreating,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 26.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center) {
            GroupCreationContent(form, isCreating, onChange, onDismiss, onCreate, errors, problem, uncertain)
        }
    }
}

@Composable
internal fun GroupCreationContent(
    form: GroupCreationForm, isCreating: Boolean,
    onChange: (GroupCreationForm) -> Unit, onDismiss: () -> Unit, onCreate: () -> Unit,
    errors: GroupFormErrors = GroupFormErrors(), problem: GroupProblem? = null, uncertain: Boolean = false,
) {
    Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = GroupDesign.Shape,
        color = Color.White, border = BorderStroke(1.dp, Color.Black), shadowElevation = 4.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)
            .padding(top = 20.dp, bottom = 32.dp)) {
            Text(stringResource(R.string.group_create), Modifier.fillMaxWidth().semantics { heading() },
                textAlign = TextAlign.Center, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            CreationField(R.string.group_name, R.string.group_name_hint, form.name,
                { onChange(form.copy(name = it)) }, enabled = !isCreating, error = errors.name)
            Spacer(Modifier.height(24.dp))
            CreationField(R.string.group_description, R.string.group_description_hint, form.description,
                { onChange(form.copy(description = it)) }, enabled = !isCreating, multiline = true, error = errors.description)
            Spacer(Modifier.height(24.dp))
            CreationField(R.string.group_capacity, R.string.group_capacity_hint, form.maxMembers,
                { onChange(form.copy(maxMembers = it)) }, enabled = !isCreating, numeric = true, error = errors.capacity)
            if (uncertain || problem != null) Text(
                stringResource(if (uncertain) R.string.group_create_uncertain else problem!!.messageResource()),
                Modifier.padding(top = 12.dp), fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(24.dp))
            // FlowRow wraps actions when space or system font size requires it.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onDismiss, enabled = !isCreating, shape = GroupDesign.Shape,
                    modifier = Modifier.widthIn(min = 118.dp).heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, GroupDesign.InputBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF1F2F6), contentColor = Color.Black)) {
                    Text(stringResource(R.string.group_cancel), fontSize = 15.sp)
                }
                GroupPrimaryButton(onCreate, enabled = !isCreating && !uncertain,
                    modifier = Modifier.widthIn(min = 118.dp)) {
                    Text(stringResource(if (isCreating) R.string.group_creating else R.string.group_create), fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun CreationField(
    label: Int, hint: Int, value: String, onValueChange: (String) -> Unit,
    enabled: Boolean, multiline: Boolean = false, numeric: Boolean = false,
    error: GroupFieldError? = null,
) {
    val labelText = stringResource(label)
    Text(labelText, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    BasicTextField(value, onValueChange, enabled = enabled, singleLine = !multiline,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (multiline) 128.dp else 48.dp)
            .semantics { contentDescription = labelText },
        textStyle = TextStyle(color = Color.Black, fontSize = 15.sp, lineHeight = 20.sp),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (multiline) ImeAction.Default else if (numeric) ImeAction.Done else ImeAction.Next),
        decorationBox = { inner ->
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White,
                border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else GroupDesign.InputBorder)) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    if (value.isEmpty()) Text(stringResource(hint), color = Color(0xFFBDB2B2),
                        fontSize = 15.sp, lineHeight = 20.sp)
                    inner()
                }
            }
        })
    error?.let {
        Text(stringResource(when (it) {
            GroupFieldError.NAME_REQUIRED -> R.string.group_name_required
            GroupFieldError.NAME_LONG -> R.string.group_name_long
            GroupFieldError.DESCRIPTION_LONG -> R.string.group_description_long
            GroupFieldError.CAPACITY -> R.string.group_capacity_invalid
        }), Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.error)
    }
}

@Preview(name = "Group creation", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GroupCreationPreview() {
    // Inline the dialog content for a static Preview; runtime uses a real modal window.
    HomeTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GroupManagementScreen(GroupManagementUiState(listStatus = GroupListStatus.CONTENT, groups = previewGroups()),
                {}, {}, {}, {}, {}, {}, {})
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxWidth().padding(26.dp)) {
                GroupCreationContent(GroupCreationForm(), false, {}, {}, {})
            }
        }
    }
}
