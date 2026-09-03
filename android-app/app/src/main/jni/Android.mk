# Обёртка над вендоренной библиотекой hev-socks5-tunnel (git submodule,
# см. hev-socks5-tunnel/) + наш тонкий JNI-мост (jni_bridge.c).

LOCAL_PATH := $(call my-dir)
include $(LOCAL_PATH)/hev-socks5-tunnel/Android.mk

include $(CLEAR_VARS)
LOCAL_PATH := $(call my-dir)
LOCAL_MODULE := hev-socks5-tunnel-jni
LOCAL_SRC_FILES := jni_bridge.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/hev-socks5-tunnel/src
LOCAL_SHARED_LIBRARIES := hev-socks5-tunnel
include $(BUILD_SHARED_LIBRARY)
