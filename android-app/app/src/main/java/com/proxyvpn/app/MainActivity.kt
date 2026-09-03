package com.proxyvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.proxyvpn.app.data.ApiClient
import com.proxyvpn.app.data.SessionStore
import com.proxyvpn.app.ui.BuyScreen
import com.proxyvpn.app.ui.CabinetScreen
import com.proxyvpn.app.ui.LinkScreen
import com.proxyvpn.app.ui.SubscriptionsScreen
import com.proxyvpn.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionStore = SessionStore(applicationContext)
        val apiClient = ApiClient(baseUrl = BuildConfig.API_BASE_URL)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(apiClient = apiClient, sessionStore = sessionStore)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(apiClient: ApiClient, sessionStore: SessionStore) {
    val navController = rememberNavController()
    var token by remember { mutableStateOf(sessionStore.getToken()) }
    val startDestination = if (token != null) "cabinet" else "link"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("link") {
            LinkScreen(
                apiClient = apiClient,
                onLinked = { newToken ->
                    sessionStore.saveToken(newToken)
                    token = newToken
                    navController.navigate("cabinet") { popUpTo("link") { inclusive = true } }
                },
            )
        }
        composable("cabinet") {
            CabinetScreen(
                apiClient = apiClient,
                token = token,
                onLogout = {
                    sessionStore.clearToken()
                    token = null
                    navController.navigate("link") { popUpTo(0) }
                },
                onNavigateSubscriptions = { navController.navigate("subscriptions") },
                onNavigateBuy = { navController.navigate("buy") },
            )
        }
        composable("subscriptions") {
            SubscriptionsScreen(apiClient = apiClient, token = token, onBack = { navController.popBackStack() })
        }
        composable("buy") {
            BuyScreen(apiClient = apiClient, onBack = { navController.popBackStack() })
        }
    }
}
