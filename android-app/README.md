# HarrisVPN — Android-клиент

Android-приложение (Kotlin + Jetpack Compose) для сервиса подписок на
`../web-proxy/`, связанное с `../telegram-bot/`: вход через Telegram (без
пароля в приложении), личный кабинет, управление подписками, покупка.

## Тестовый режим без бэкенда (MOCK_MODE)

⚠️ Сейчас в `app/build.gradle.kts` включён `MOCK_MODE = true` — по
просьбе для проверки интерфейса. В этом режиме:

- Экран входа через Telegram пропускается, приложение сразу открывает
  личный кабинет.
- Все данные (подписка, логин/пароль прокси, тарифы, история) —
  тестовые заглушки из `data/MockApiService.kt`, реальных сетевых
  запросов нет.

**Не забудьте вернуть `MOCK_MODE` в `false`** в `app/build.gradle.kts`,
когда будете тестировать реальную связку с ботом/бэкендом — иначе
приложение так и будет показывать фейковые данные.

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

## VPN-туннель

Кнопка «Подключить VPN» поднимает настоящий системный VPN
(`android.net.VpnService`) и прокачивает через него весь трафик
устройства в SOCKS5 `web-proxy` — не заглушка.

Разбор IP-пакетов и их проброс в SOCKS5 делает
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (MIT) —
подключена как git submodule в `app/src/main/jni/hev-socks5-tunnel/` и
собирается classic ndk-build'ом (`app/src/main/jni/Android.mk`) в
`libhev-socks5-tunnel.so`; `jni_bridge.c` — тонкий JNI-мост к её C API
(`hev_socks5_tunnel_main_from_str`/`hev_socks5_tunnel_quit`), а
`vpn/VpnConnectionService.kt` создаёт TUN-интерфейс через
`VpnService.Builder`, исключает само приложение из туннеля
(`addDisallowedApplication`, иначе получилась бы петля маршрутизации
для соединения с самим SOCKS5-сервером) и запускает библиотеку в
отдельном потоке.

**Известное ограничение:** наш `web-proxy` поддерживает только SOCKS5
`CONNECT` (TCP), не `UDP ASSOCIATE` — поэтому UDP-режим релея в конфиге
выставлен в `'tcp'`. DNS и другой UDP-трафик через туннель может
работать хуже, чем обычный HTTP(S)/TCP — это стоит в первую очередь
проверить на реальном устройстве. Полноценная поддержка UDP потребует
доработки SOCKS5-сервера в `../web-proxy/`.

## Сборка

Проект не собирался и не запускался в этой сессии — здесь нет доступа к
Android SDK, Google Maven и NDK (заблокированы политикой песочницы), а
собрать и проверить нативную часть (ndk-build + JNI) можно только там,
где это всё есть.

**Обязательно** клонируйте с сабмодулями (иначе `hev-socks5-tunnel/`
будет пустой папкой и сборка упадёт на этапе ndk-build):

```bash
git clone --recursive https://github.com/Ikaosk/proxy.git
# если уже склонировали без --recursive:
git submodule update --init --recursive
```

### Вариант 1: CI (без своего компьютера)

`.github/workflows/android-apk.yml` собирает debug-APK на GitHub Actions
(у раннера есть обычный интернет) при каждом пуше в `android-app/**`, либо
вручную — вкладка **Actions → Android APK → Run workflow**. Готовый
`app-debug.apk` появляется в **Artifacts** внизу страницы запуска (нужно
быть залогиненным в GitHub, artifacts приватны). ⚠️ Собирается со
значениями `API_BASE_URL`/`BOT_USERNAME` по умолчанию из
`app/build.gradle.kts` (`http://10.0.2.2:3000`, `MyVpnBot`) — поправьте их
перед пушем под свой сервер и бота, иначе экраны кабинета/покупки не
подключатся к вашему backend.

### Вариант 2: Android Studio

Откройте папку `android-app/` в Android Studio (Iguana/Koala или новее) —
она сама подтянет Gradle wrapper, NDK (версия закреплена в
`app/build.gradle.kts` через `ndkVersion`) и зависимости; при первой
синхронизации может предложить установить NDK — соглашайтесь. Либо вручную:

```bash
cd android-app
gradle wrapper --gradle-version 8.7   # один раз, если нет gradlew
sdkmanager --install "ndk;27.0.12077973"   # если NDK ещё не установлен
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
