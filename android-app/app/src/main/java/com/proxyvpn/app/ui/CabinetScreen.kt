package com.proxyvpn.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxyvpn.app.data.ApiService
import com.proxyvpn.app.data.MeResult
import com.proxyvpn.app.vpn.VpnConnectionService
import kotlinx.coroutines.launch

@Composable
fun CabinetScreen(
    apiClient: ApiService,
    token: String?,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var me by remember { mutableStateOf<MeResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var vpnRunning by remember { mutableStateOf(false) }

    fun startVpn(result: MeResult) {
        context.startService(
            Intent(context, VpnConnectionService::class.java).apply {
                putExtra(VpnConnectionService.EXTRA_HOST, result.proxy.host)
                putExtra(VpnConnectionService.EXTRA_SOCKS_PORT, result.proxy.socksPort)
                putExtra(VpnConnectionService.EXTRA_LOGIN, result.proxy.login)
                putExtra(VpnConnectionService.EXTRA_PASSWORD, result.proxy.password)
            },
        )
        vpnRunning = true
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            me?.let { startVpn(it) }
        } else {
            Toast.makeText(context, "Без разрешения VPN не подключить", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVpn(result: MeResult) {
        if (vpnRunning) {
            context.startService(
                Intent(context, VpnConnectionService::class.java).setAction(VpnConnectionService.ACTION_STOP)
            )
            vpnRunning = false
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVpn(result)
            }
        }
    }

    fun refresh() {
        val t = token ?: return
        loading = true
        scope.launch {
            try {
                me = apiClient.me(t)
                error = null
            } catch (e: Exception) {
                error = "Не удалось загрузить данные: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(token) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { refresh() }) { Text("Повторить") }
        } else {
            me?.let { result ->
                val sub = result.subscription

                ConnectionStatusCard(
                    connected = vpnRunning,
                    subscriptionActive = sub.active,
                    host = result.proxy.host,
                    onToggle = { toggleVpn(result) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        IdentityLine(
                            title = if (result.username != null) "@${result.username}" else "ID ${result.telegramId}",
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SubscriptionBadge(active = sub.active, planTitle = sub.planTitle, expiresAt = sub.expiresAt)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Данные для подключения",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MonoLine("Логин", result.proxy.login)
                        MonoLine("Пароль", result.proxy.password)
                        MonoLine("HTTP(S)", "${result.proxy.host}:${result.proxy.httpPort}")
                        MonoLine("SOCKS5", "${result.proxy.host}:${result.proxy.socksPort}")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                copyToClipboard(
                                    context,
                                    "curl -x http://${result.proxy.login}:${result.proxy.password}@" +
                                        "${result.proxy.host}:${result.proxy.httpPort} https://example.com",
                                )
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("Скопировать curl-команду")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    try {
                        token?.let { apiClient.logout(it) }
                    } catch (e: Exception) {
                        // Не удалось отозвать токен на сервере — всё равно выходим локально.
                    }
                    onLogout()
                }
            },
        ) {
            Text("Выйти")
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connected: Boolean,
    subscriptionActive: Boolean,
    host: String,
    onToggle: () -> Unit,
) {
    val accent = if (connected) SuccessColor else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (connected) "🛡️" else "🔒", fontSize = 40.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (connected) "Подключено" else "Отключено",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (connected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(host, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onToggle,
                enabled = subscriptionActive,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connected) MaterialTheme.colorScheme.errorContainer else accent,
                    contentColor = if (connected) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                ),
            ) {
                Text(if (connected) "Отключить VPN" else "Подключить VPN")
            }
            if (!subscriptionActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Нужна активная подписка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionBadge(active: Boolean, planTitle: String?, expiresAt: String?) {
    val (bg, fg, text) = if (active) {
        Triple(SuccessColor.copy(alpha = 0.15f), SuccessColor, "✅ Активна: $planTitle · до $expiresAt")
    } else {
        Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Подписка не активна",
        )
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IdentityLine(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MonoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("proxy", text))
}

private val SuccessColor = Color(0xFF2E7D5B)
