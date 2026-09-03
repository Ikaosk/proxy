# Обёртка над вендоренной библиотекой hev-socks5-tunnel (git submodule,
# см. hev-socks5-tunnel/) + наш тонкий JNI-мост (jni_bridge.c).
#
# MY_LOCAL_PATH захватывается ОДИН раз, до любых include: hev-socks5-tunnel/
# Android.mk сам заканчивается на "include $(BUILD_EXECUTABLE)", после чего
# $(call my-dir) резолвится в служебную директорию NDK (build/core), а не
# в нашу jni/ — поэтому переиспользовать $(call my-dir) после такого include
# нельзя, только заранее сохранённое значение.

MY_LOCAL_PATH := $(call my-dir)

include $(MY_LOCAL_PATH)/hev-socks5-tunnel/Android.mk

include $(CLEAR_VARS)
LOCAL_PATH := $(MY_LOCAL_PATH)
LOCAL_MODULE := hev-socks5-tunnel-jni
LOCAL_SRC_FILES := jni_bridge.c
LOCAL_C_INCLUDES := $(MY_LOCAL_PATH)/hev-socks5-tunnel/src
LOCAL_SHARED_LIBRARIES := hev-socks5-tunnel
include $(BUILD_SHARED_LIBRARY)
