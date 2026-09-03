# Android-клиент

Android-приложение (Kotlin + Jetpack Compose) для сервиса подписок на
`../web-proxy/`, связанное с `../telegram-bot/`: вход через Telegram (без
пароля в приложении), личный кабинет, управление подписками, покупка.

## Экраны

- **Вход через Telegram** — приложение получает одноразовый код от backend
  API (`POST /api/link/start`), открывает бота диплинком
  `t.me/<bot>?start=link_<код>`, дальше поллит `GET /api/link/status`, пока
  пользователь не нажмёт Start в Telegram. Пароль нигде не нужен.
- **👤 Личный кабинет** — статус подписки, логин/пароль/адреса прокси
  (из `GET /api/me`), кнопка копирования готовой curl-команды.
- **📋 Управление подписками** — история подписок (`GET /api/subscriptions`).
- **💳 Купить подписку** — список тарифов (`GET /api/plans`), кнопка
  «Оплатить в Telegram» открывает бота диплинком `start=buy_<planId>` —
  сама оплата (Stars/карта) происходит в Telegram, это единственное место,
  где доступен Telegram Payments API.

## Как это устроено технически

```
Android-приложение ──HTTP──▶ telegram-bot/lib/apiServer.js ──┐
        │                                                     │
        │ deep link (t.me/...)                     общий store.json
        ▼                                                     │
   Telegram-бот ◀──────────────────────────────────────────────┘
        │
        ▼
    web-proxy (проверяет подписку из того же store.json)
```

Подробности API — в `../telegram-bot/README.md`, раздел «HTTP API для
приложения».

## Что дальше: VPN-ядро

Это приложение — **клиент** (личный кабинет, подписки, вход через
Telegram), а не готовый VPN-туннель. Кнопка «Подключить VPN» на экране
кабинета уже делает реальную часть Android-интеграции — запрашивает
системное разрешение через `VpnService.prepare()` и поднимает
foreground-сервис (`vpn/VpnConnectionService.kt`), — но **не передаёт
трафик**: TUN-интерфейс не создаётся (`Builder().establish()` не
вызывается), поэтому интернет пользователя это никак не затрагивает.

Причина разделения на два шага: полноценный VPN поверх `VpnService`
требует разбора IP-пакетов и их проброса в SOCKS5 (`tun2socks`) — это
обычно нативный компонент на C/Go (пример: open-source
`outline-go-tun2socks` или `hev-socks5-tunnel`), который встраивается
через JNI/AAR. Такой сборки нет в этой сессии (нет доступа к Android
SDK/Google Maven из песочницы), поэтому её нужно добавить и
протестировать отдельно, уже в Android Studio на реальном
устройстве/эмуляторе.

Когда будете добавлять tun2socks — точка входа обозначена
`TODO(vpn-core)` в `vpn/VpnConnectionService.kt`: там нужно вызвать
`Builder().addAddress(...).addRoute(...).establish()`, получить
`ParcelFileDescriptor`, и запустить поверх него tun2socks с адресом
`web-proxy` (из `GET /api/me` → `proxy.host`/`proxy.socksPort`) как
upstream SOCKS5.

## Сборка

Проект не собирался и не запускался в этой сессии — здесь нет доступа к
Android SDK и Google Maven (заблокированы политикой песочницы). Откройте
папку в Android Studio (Iguana/Koala или новее) — она сама подтянет
Gradle wrapper и зависимости. Либо вручную:

```bash
cd android-app
gradle wrapper --gradle-version 8.7   # один раз, если нет gradlew
./gradlew assembleDebug
```

Перед сборкой поправьте `app/build.gradle.kts`:
- `API_BASE_URL` — адрес вашего `telegram-bot` (по умолчанию
  `http://10.0.2.2:3000`, алиас хоста для эмулятора).
- `BOT_USERNAME` — юзернейм вашего бота без `@`.

В проде backend должен быть на HTTPS — уберите
`android:usesCleartextTraffic="true"` из `AndroidManifest.xml` после
перехода на HTTPS.

## Зависимости

Jetpack Compose (BOM), Navigation Compose, OkHttp (HTTP-клиент),
`androidx.security:security-crypto` (шифрованное хранилище токена),
kotlinx-coroutines. Версии — в `app/build.gradle.kts`; при открытии в
свежей Android Studio она может предложить обновления через Version
Catalog — это нормально, проект не завязан на конкретные патч-версии.
