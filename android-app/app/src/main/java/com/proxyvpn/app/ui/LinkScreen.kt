package com.proxyvpn.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proxyvpn.app.data.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран входа: связка приложения с аккаунтом Telegram через одноразовый
 * код (см. telegram-bot/README.md, раздел "HTTP API для приложения").
 * Пароль в приложении не нужен — подтверждение происходит в самом Telegram.
 */
@Composable
fun LinkScreen(apiClient: ApiClient, onLinked: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf<String?>(null) }
    var deepLink by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        val currentCode = code ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            try {
                val status = apiClient.linkStatus(currentCode)
                if (!status.valid) {
                    error = "Код истёк, запросите новый"
                    code = null
                    break
                }
                if (status.linked && status.sessionToken != null) {
                    onLinked(status.sessionToken)
                    break
                }
            } catch (e: Exception) {
                // Временная сетевая ошибка — просто пробуем ещё раз на следующем тике.
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Вход через Telegram", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (code == null) {
            Text("Для входа подтвердите привязку через нашего Telegram-бота.")
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val result = apiClient.linkStart()
                            code = result.code
                            deepLink = result.deepLink
                        } catch (e: Exception) {
                            error = "Не удалось связаться с сервером: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Подождите…" else "Войти через Telegram")
            }
        } else {
            Text("Код: $code", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val link = deepLink ?: return@Button
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }) {
                Text("Открыть Telegram")
            }
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ожидаем подтверждения в Telegram…")
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
