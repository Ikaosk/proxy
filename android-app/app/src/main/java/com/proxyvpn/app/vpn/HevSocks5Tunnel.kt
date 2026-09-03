package com.proxyvpn.app.vpn

/**
 * JNI-биндинг к нативной библиотеке hev-socks5-tunnel (git submodule в
 * app/src/main/jni/hev-socks5-tunnel, MIT). Сама сборка — в
 * app/src/main/jni/Android.mk (classic ndk-build) + jni_bridge.c.
 */
object HevSocks5Tunnel {
    init {
        System.loadLibrary("hev-socks5-tunnel-jni")
    }

    /**
     * Блокирует вызывающий поток до nativeStop() или ошибки — вызывать
     * только из отдельного Thread, не из основного потока сервиса.
     * Возвращает 0 при успехе, иначе -1 (см. hev-main.h).
     */
    external fun nativeStart(config: String, tunFd: Int): Int

    external fun nativeStop()
}
