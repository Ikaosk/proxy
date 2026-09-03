package com.proxyvpn.app.data

/**
 * Заглушка ApiService с тестовыми данными — для проверки интерфейса без
 * бэкенда и без входа через Telegram. Включается флагом
 * BuildConfig.MOCK_MODE (см. app/build.gradle.kts).
 *
 * ВРЕМЕННО: перед реальной работой с бэкендом верните MOCK_MODE = false.
 */
class MockApiService : ApiService {
    override suspend fun linkStart(): LinkStartResult =
        LinkStartResult(code = "MOCK123", deepLink = null, expiresIn = 600)

    override suspend fun linkStatus(code: String): LinkStatusResult =
        LinkStatusResult(valid = true, linked = true, sessionToken = "mock-token")

    override suspend fun plans(): List<Plan> = listOf(
        Plan(id = "1m", title = "1 месяц", days = 30, price = 150, currency = "XTR"),
        Plan(id = "3m", title = "3 месяца", days = 90, price = 400, currency = "XTR"),
        Plan(id = "6m", title = "6 месяцев", days = 180, price = 750, currency = "XTR"),
        Plan(id = "12m", title = "12 месяцев", days = 365, price = 1400, currency = "XTR"),
    )

    override suspend fun me(token: String): MeResult = MeResult(
        telegramId = 123456789L,
        username = "test_user",
        firstName = "Тест",
        createdAt = "2026-01-01T00:00:00.000Z",
        subscription = SubscriptionStatus(
            active = true,
            planId = "3m",
            planTitle = "3 месяца",
            expiresAt = "2026-12-31T00:00:00.000Z",
        ),
        proxy = ProxyInfo(
            login = "tg123456789",
            password = "mockPassword123",
            host = "203.0.113.10",
            httpPort = 8080,
            socksPort = 1080,
        ),
    )

    override suspend fun subscriptions(token: String): List<Subscription> = listOf(
        Subscription(
            planId = "3m",
            planTitle = "3 месяца",
            startedAt = "2026-10-01T00:00:00.000Z",
            expiresAt = "2026-12-31T00:00:00.000Z",
            active = true,
        ),
        Subscription(
            planId = "1m",
            planTitle = "1 месяц",
            startedAt = "2026-09-01T00:00:00.000Z",
            expiresAt = "2026-10-01T00:00:00.000Z",
            active = false,
        ),
    )

    override suspend fun logout(token: String) {
        // мок — ничего отзывать не нужно
    }
}
