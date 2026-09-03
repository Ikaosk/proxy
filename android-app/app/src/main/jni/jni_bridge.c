/*
 * Тонкая JNI-обвязка над hev-socks5-tunnel (см. hev-socks5-tunnel/src/hev-main.h).
 * Сама библиотека ничего не знает про Android/JNI — весь мост в этом файле.
 */

#include <jni.h>
#include "hev-main.h"

JNIEXPORT jint JNICALL
Java_com_proxyvpn_app_vpn_HevSocks5Tunnel_nativeStart (JNIEnv *env, jobject thiz,
                                                        jstring config, jint tun_fd)
{
    const char *cconfig = (*env)->GetStringUTFChars (env, config, NULL);
    jsize len = (*env)->GetStringUTFLength (env, config);

    int ret = hev_socks5_tunnel_main_from_str ((const unsigned char *) cconfig,
                                                (unsigned int) len, (int) tun_fd);

    (*env)->ReleaseStringUTFChars (env, config, cconfig);
    return (jint) ret;
}

JNIEXPORT void JNICALL
Java_com_proxyvpn_app_vpn_HevSocks5Tunnel_nativeStop (JNIEnv *env, jobject thiz)
{
    hev_socks5_tunnel_quit ();
}
