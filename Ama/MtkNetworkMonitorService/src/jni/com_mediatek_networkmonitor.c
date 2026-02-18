
#include <jni.h>
#include <stdlib.h>
#include <cutils/log.h>
#include <errno.h>
#include <stdarg.h>
#include <netutils/ifc.h>

#define LOG_TAG "com_mediatek_networkmonitor"

#define JNIREG_CLASS "com/mediatek/wifi/MtkNetworkMonitorService"


static jboolean native_wlan0_up(JNIEnv* env, jclass clazz)
{
    if(ifc_init() < 0)
    {
        ALOGE("native_wlan0_up ifc_init failed!");
        return JNI_FALSE;
    }
    if(ifc_up("wlan0") < 0)
    {
        ALOGE("native_wlan0_up ifc_up failed!");
        return JNI_FALSE;
    }
    ifc_close();
    ALOGD("native_wlan0_up succeed!");
    return JNI_TRUE;
}

static jboolean native_wlan0_down(JNIEnv* env, jclass clazz)
    {
        if(ifc_init() < 0)
        {
            ALOGE("native_wlan0_down ifc_init failed!");
            return JNI_FALSE;
        }
        if(ifc_down("wlan0") < 0)
        {
            ALOGE("native_wlan0_down ifc_down failed!");
            return JNI_FALSE;
        }
        ifc_close();
        ALOGD("native_wlan0_down succeed!");
        return JNI_TRUE;
    }


static JNINativeMethod gMethods[] = {
    { "wlan0UpCmd", "()Z", (void*)native_wlan0_up},
    { "wlan0DownCmd", "()Z", (void*)native_wlan0_down},
};

int register_com_mediatek_wifiMonitorNative(JNIEnv* env)
{
    if (!jniRegisterNativeMethods(env, JNIREG_CLASS, gMethods, 
                                     sizeof(gMethods) / sizeof(gMethods[0])))
    {
        return JNI_FALSE;
    }    
    return JNI_TRUE;
}

