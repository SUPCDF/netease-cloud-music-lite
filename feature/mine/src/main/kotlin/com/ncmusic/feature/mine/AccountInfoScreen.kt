package com.ncmusic.feature.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar

/** 账号信息页：改头像 / 昵称 / 简介 */
@Composable
fun AccountInfoScreen(
    onBack: () -> Unit,
    viewModel: AccountInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val avatarPicker = rememberLauncherForActivityResult(GridImagePickerContract()) { uri ->
        uri?.let { viewModel.changeAvatar(it) }
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = { NcTopBar(title = Str.res(R.string.account_info), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> NcLoading(Modifier.padding(padding))

            state.profile == null -> NcError(
                message = Str.res(R.string.failed_to_load),
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clickable(enabled = !state.uploadingAvatar) {
                            avatarPicker.launch(Unit)
                        },
                ) {
                    CoverImage(
                        url = state.profile?.avatarUrl,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                    )
                    if (state.uploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { avatarPicker.launch(Unit) },
                    enabled = !state.uploadingAvatar,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(Str.res(R.string.change_avatar))
                }

                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = viewModel::updateNickname,
                    label = { Text(Str.res(R.string.nickname)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.signature,
                    onValueChange = viewModel::updateSignature,
                    label = { Text(Str.res(R.string.signature)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(Str.res(R.string.save))
                    }
                }
            }
        }
    }
}
