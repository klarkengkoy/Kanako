package com.smyddevstudio.kanako.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.account.AccountState
import com.smyddevstudio.kanako.ui.components.KanakoButton
import com.smyddevstudio.kanako.ui.components.KanakoDialog
import com.smyddevstudio.kanako.ui.components.KanakoTextButton
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile
import com.smyddevstudio.kanako.ui.theme.LocalKanakoMotionEnabled

@Composable
internal fun LoginDialog(
    state: AccountState,
    testLoginEnabled: Boolean,
    onSignIn: () -> Unit,
    onTestLogin: suspend (String, String) -> Boolean,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTestLogin by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invalidCredentials by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    val duration = if (LocalKanakoMotionEnabled.current) KanakoUiProfile.expandDuration else 0
    val canSubmit = showTestLogin && !state.busy && !submitting && username.isNotEmpty() && password.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        val enteredPassword = password
        password = ""
        submitting = true
        scope.launch {
            try { invalidCredentials = !onTestLogin(username, enteredPassword) }
            finally { submitting = false }
        }
    }

    KanakoDialog(
        onDismissRequest = {},
        dismissible = false,
        modifier = modifier
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(KanakoUiProfile.dialogContentPadding),
            verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogSectionGap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogHeadingGap)) {
                Text(
                    stringResource(R.string.account_welcome),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            AccountSettingsContent(
                state = state,
                onSignIn = onSignIn,
                onSignOut = {},
                onDelete = {},
                onRetry = {},
                onManageSubscription = {},
                onPurchaseOptions = {},
                showIntroduction = false,
                signInOnly = true
            )
            if (testLoginEnabled) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogParagraphGap)
                ) {
                    KanakoTextButton(
                        onClick = {
                            showTestLogin = !showTestLogin
                            password = ""
                            invalidCredentials = false
                        },
                        enabled = !state.busy && !submitting,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.account_test_login), fontWeight = FontWeight.Bold)
                    }
                    AnimatedVisibility(
                        visible = showTestLogin,
                        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogParagraphGap)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it; invalidCredentials = false },
                                label = { Text(stringResource(R.string.account_test_username)) },
                                singleLine = true,
                                enabled = !state.busy && !submitting && showTestLogin,
                                isError = invalidCredentials,
                                shape = MaterialTheme.shapes.large,
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; invalidCredentials = false },
                                label = { Text(stringResource(R.string.account_test_password)) },
                                singleLine = true,
                                enabled = !state.busy && !submitting && showTestLogin,
                                isError = invalidCredentials,
                                shape = MaterialTheme.shapes.large,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (invalidCredentials) Text(
                                stringResource(R.string.account_test_login_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            KanakoButton(
                                onClick = ::submit,
                                enabled = canSubmit,
                                modifier = Modifier.fillMaxWidth().heightIn(min = KanakoUiProfile.dialogActionHeight)
                            ) {
                                Text(stringResource(R.string.account_test_login), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            KanakoTextButton(onClick = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.account_privacy), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(name = "Login")
@Preview(name = "Login dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Login compact large text", widthDp = 320, heightDp = 480, fontScale = 1.5f)
@Composable
private fun LoginDialogPreview() {
    KanakoTheme {
        LoginDialog(
            state = AccountState(configured = true, accessServiceConfigured = false),
            testLoginEnabled = true,
            onSignIn = {},
            onTestLogin = { _, _ -> false },
            onPrivacy = {}
        )
    }
}
