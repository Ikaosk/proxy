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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.proxyvpn.app.data.ApiClient
import com.proxyvpn.app.data.MeResult
import com.proxyvpn.app.vpn.VpnConnectionService
import kotlinx.coroutines.launch

@Composable
fun CabinetScreen(
    apiClient: ApiClient,
    token: String?,
    onLogout: () -> Unit,
    onNavigateSubscriptions: () -> Unit,
    onNavigateBuy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var me by remember { mutableStateOf<MeResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            context.startService(Intent(context, VpnConnectionService::class.java))
            Toast.makeText(
                context,
                "Разрешение получено. Туннелирование трафика ещё не реализовано — это заготовка для следующего шага.",
                Toast.LENGTH_LONG,
            ).show()
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("👤 Личный кабинет", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { refresh() }) { Text("Повторить") }
        } else {
            me?.let { result ->
                val sub = result.subscription

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ID: ${result.telegramId}")
                        result.username?.let { Text("@$it") }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (sub.active) {
                            Text("Подписка активна: ${sub.planTitle}")
                            Text("До: ${sub.expiresAt}")
                        } else {
                            Text("Подписка не активна")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Данные для подключения", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Логин: ${result.proxy.login}")
                        Text("Пароль: ${result.proxy.password}")
                        Text("HTTP(S): ${result.proxy.host}:${result.proxy.httpPort}")
                        Text("SOCKS5: ${result.proxy.host}:${result.proxy.socksPort}")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            copyToClipboard(
                                context,
                                "curl -x http://${result.proxy.login}:${result.proxy.password}@" +
                                    "${result.proxy.host}:${result.proxy.httpPort} https://example.com",
                            )
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Скопировать curl-команду")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("VPN-подключение")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ядро туннелирования (tun2socks) — следующий шаг. Кнопка ниже уже " +
                                "запрашивает реальное системное разрешение на VPN, но пока не " +
                                "передаёт трафик. Сейчас логин/пароль выше можно использовать " +
                                "как обычный SOCKS5/HTTP-прокси.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            enabled = sub.active,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val prepareIntent = VpnService.prepare(context)
                                if (prepareIntent != null) {
                                    vpnPermissionLauncher.launch(prepareIntent)
                                } else {
                                    context.startService(Intent(context, VpnConnectionService::class.java))
                                    Toast.makeText(
                                        context,
                                        "Разрешение уже есть. Туннелирование пока не реализовано.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                        ) {
                            Text("Подключить VPN (заготовка)")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onNavigateSubscriptions, modifier = Modifier.fillMaxWidth()) {
            Text("📋 Управление подписками")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onNavigateBuy, modifier = Modifier.fillMaxWidth()) {
            Text("💳 Купить подписку")
        }
        Spacer(modifier = Modifier.height(8.dp))
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("proxy", text))
}
