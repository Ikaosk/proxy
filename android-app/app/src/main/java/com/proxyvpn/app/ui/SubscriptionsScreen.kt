package com.proxyvpn.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.proxyvpn.app.data.ApiService
import com.proxyvpn.app.data.Subscription

@Composable
fun SubscriptionsScreen(apiClient: ApiService, token: String?, onBack: () -> Unit) {
    var subscriptions by remember { mutableStateOf<List<Subscription>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        val t = token
        if (t == null) {
            loading = false
            return@LaunchedEffect
        }
        try {
            subscriptions = apiClient.subscriptions(t)
        } catch (e: Exception) {
            error = "Не удалось загрузить подписки: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("📋 Управление подписками", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            subscriptions.isEmpty() -> Text("У вас пока нет подписок.")
            else -> LazyColumn {
                items(subscriptions) { sub ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(sub.planTitle, style = MaterialTheme.typography.titleMedium)
                            Text("До: ${sub.expiresAt}")
                            Text(if (sub.active) "✅ активна" else "⏹ истекла")
                        }
                    }
                }
            }
        }
    }
}
