package com.proxyvpn.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proxyvpn.app.BuildConfig
import com.proxyvpn.app.data.ApiService
import com.proxyvpn.app.data.Plan

/**
 * Оплата тарифа происходит в Telegram (Stars/платёжный провайдер доступны
 * только там) — кнопка открывает бота с диплинком buy_<planId>, бот сразу
 * присылает счёт. После оплаты пользователь возвращается в приложение и
 * тянет /api/me заново (см. CabinetScreen).
 */
@Composable
fun BuyScreen(apiClient: ApiService) {
    val context = LocalContext.current
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            plans = apiClient.plans()
        } catch (e: Exception) {
            error = "Не удалось загрузить тарифы: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Купить подписку", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Оплата происходит в Telegram — там доступны Stars и/или карта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn {
                items(plans) { plan -> PlanRow(plan, context) }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: Plan, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    priceLabel(plan),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    val link = "https://t.me/${BuildConfig.BOT_USERNAME}?start=buy_${plan.id}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                },
            ) {
                Text("Оплатить")
            }
        }
    }
}

private fun priceLabel(plan: Plan): String =
    if (plan.currency == "XTR") "${plan.price} ⭐" else "${plan.price} ₽"
