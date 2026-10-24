package com.ncmusic.feature.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 登录页：
 * - 扫码登录（推荐，安全）
 * - 手机号登录（密码 / 短信验证码）
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 登录成功后返回上一页
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            kotlinx.coroutines.delay(600)
            onLoginSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NcTopBar(title = Str.res(R.string.log_in), onBack = onBack)

        TabRow(selectedTabIndex = if (state.mode == LoginMode.QR) 0 else 1) {
            Tab(
                selected = state.mode == LoginMode.QR,
                onClick = { viewModel.switchMode(LoginMode.QR) },
                text = { Text(Str.res(R.string.scan_to_log_in)) },
            )
            Tab(
                selected = state.mode == LoginMode.PHONE,
                onClick = { viewModel.switchMode(LoginMode.PHONE) },
                text = { Text(Str.res(R.string.phone_login)) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.mode) {
                LoginMode.QR -> QrSection(
                    qrKey = state.qrKey,
                    statusText = state.qrStatusText,
                    expired = state.qrExpired,
                    scanned = state.scanned,
                    onRefresh = { viewModel.refreshQr() },
                )

                LoginMode.PHONE -> PhoneSection(
                    state = state,
                    onPhoneChange = { viewModel.updatePhone(it) },
                    onPasswordChange = { viewModel.updatePassword(it) },
                    onCaptchaChange = { viewModel.updateCaptcha(it) },
                    onUseCaptchaChange = { viewModel.setUseCaptcha(it) },
                    onSendCaptcha = { viewModel.sendCaptcha() },
                    onLogin = { viewModel.login() },
                )
            }

            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = Str.res(R.string.your_login_cookie_is_stored_only_on_this_device),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun QrSection(
    qrKey: String?,
    statusText: String,
    expired: Boolean,
    scanned: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(232.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = remember(qrKey) {
                qrKey?.let { generateQrBitmap("https://music.163.com/login?codekey=$it") }
            }
            when {
                bitmap != null && !expired -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = Str.res(R.string.login_qr_code),
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )

                expired -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = Str.res(R.string.refresh_qr_code))
                    }
                    Text(Str.res(R.string.qr_code_expired), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRefresh) { Text(Str.res(R.string.tap_to_refresh)) }
                }

                else -> CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (scanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = Str.res(R.string.open_netease_cloud_music_menu_top_left_scan),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhoneSection(
    state: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onUseCaptchaChange: (Boolean) -> Unit,
    onSendCaptcha: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text(Str.res(R.string.phone_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (state.useCaptcha) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.captcha,
                    onValueChange = onCaptchaChange,
                    label = { Text(Str.res(R.string.sms_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(12.dp))
                TextButton(
                    onClick = onSendCaptcha,
                    enabled = state.captchaCooldown == 0 && !state.loading,
                ) {
                    Text(
                        text = if (state.captchaCooldown > 0) "${state.captchaCooldown}s" else Str.res(R.string.get_code),
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(Str.res(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Str.res(R.string.log_in_with_sms_code),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.useCaptcha, onCheckedChange = onUseCaptchaChange)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onLogin,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(Str.res(R.string.log_in))
            }
        }
    }
}

/** 本地生成二维码位图（zxing），不依赖服务端出图 */
private fun generateQrBitmap(content: String, size: Int = 600): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
