#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <jni.h>
#include <assert.h>
#include "nativehelper/JNIHelp.h"
#include <utils/Log.h>


#include "com_mediatek_networkmonitor.h"

#define LOG_TAG "wifimonitor_onload"

/*
* Register native methods for all classes we know about.
*/
static int registerNatives(JNIEnv* env)
{
    
    register_com_mediatek_wifiMonitorNative(env);
    
    return JNI_TRUE;
}

/*
* Set some test stuff up.
*
* Returns the JNI version on success, -1 on failure.
*/

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;
    ALOGE("JNI_OnLoad in !");

#ifdef __cplusplus
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {        
        ALOGE("get env fail in !");
        return -1;
    }
#else
    if ((*vm)->GetEnv(vm,(void**) &env, JNI_VERSION_1_4) != JNI_OK) {    
        ALOGE("get env fail in !");
        return -1;
    }
#endif   
    assert(env != NULL);

    if (!registerNatives(env)) {//
        
        ALOGE("registerNatives fail in !");
        return -1;
    }
    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

    return result;
}



