package com.proxyvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.proxyvpn.app.data.ApiClient
import com.proxyvpn.app.data.ApiService
import com.proxyvpn.app.data.MockApiService
import com.proxyvpn.app.data.SessionStore
import com.proxyvpn.app.ui.BuyScreen
import com.proxyvpn.app.ui.CabinetScreen
import com.proxyvpn.app.ui.LinkScreen
import com.proxyvpn.app.ui.SubscriptionsScreen
import com.proxyvpn.app.ui.theme.AppTheme

private object Routes {
    const val LINK = "link"
    const val CABINET = "cabinet"
    const val SUBSCRIPTIONS = "subscriptions"
    const val BUY = "buy"
}

private data class Tab(val route: String, val emoji: String, val label: String)

private val TABS = listOf(
    Tab(Routes.CABINET, "👤", "Кабинет"),
    Tab(Routes.SUBSCRIPTIONS, "📋", "Подписки"),
    Tab(Routes.BUY, "💳", "Купить"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionStore = SessionStore(applicationContext)
        // MOCK_MODE (см. app/build.gradle.kts) — ВРЕМЕННО для проверки интерфейса
        // без бэкенда и без входа через Telegram: подменяет ApiClient тестовыми
        // данными и сразу открывает личный кабинет. Верните MOCK_MODE = false,
        // когда бэкенд/бот будут готовы к реальному тестированию.
        val apiClient: ApiService =
            if (BuildConfig.MOCK_MODE) MockApiService() else ApiClient(baseUrl = BuildConfig.API_BASE_URL)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(apiClient = apiClient, sessionStore = sessionStore)
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavHost(apiClient: ApiService, sessionStore: SessionStore) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // В MOCK_MODE логина нет вообще — сразу открываем кабинет с тестовым токеном.
    var token by remember { mutableStateOf(if (BuildConfig.MOCK_MODE) "mock-token" else sessionStore.getToken()) }
    val startDestination = if (token != null) Routes.CABINET else Routes.LINK
    val showChrome = currentRoute != null && currentRoute != Routes.LINK

    Scaffold(
        topBar = {
            if (showChrome) {
                CenterAlignedTopAppBar(
                    title = { Text("🛡️ HarrisVPN", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        },
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Text(tab.emoji) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LINK) {
                LinkScreen(
                    apiClient = apiClient,
                    onLinked = { newToken ->
                        sessionStore.saveToken(newToken)
                        token = newToken
                        navController.navigate(Routes.CABINET) { popUpTo(Routes.LINK) { inclusive = true } }
                    },
                )
            }
            composable(Routes.CABINET) {
                CabinetScreen(
                    apiClient = apiClient,
                    token = token,
                    onLogout = {
                        sessionStore.clearToken()
                        token = null
                        navController.navigate(Routes.LINK) { popUpTo(0) }
                    },
                )
            }
            composable(Routes.SUBSCRIPTIONS) {
                SubscriptionsScreen(apiClient = apiClient, token = token)
            }
            composable(Routes.BUY) {
                BuyScreen(apiClient = apiClient)
            }
        }
    }
}
