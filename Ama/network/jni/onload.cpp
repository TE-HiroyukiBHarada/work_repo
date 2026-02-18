#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <jni.h>
#include <assert.h>
#include <log/log.h>

#include "com_mediatek_network.h"

#define LOG_TAG "network_onload"

static int getprocname(pid_t pid, char *buf, size_t len) {
    char filename[32];
    FILE *f;

    snprintf(filename, sizeof(filename), "/proc/%d/cmdline", pid);
    f = fopen(filename, "re");
    if (!f) {
        *buf = '\0';
        return 1;
    }
    if (!fgets(buf, len, f)) {
        *buf = '\0';
        if (fclose(f) != 0) {
            ALOGE("!!!!!!! fclose error !!");
        }
        return 2;
    }
    if (fclose(f) != 0) {
        ALOGE("!!!!!!! fclose error !!");
    }
    return 0;
}

/*
* Register native methods for all classes we know about.
*/
static int registerNatives(JNIEnv* env)
{
    
    register_com_mediatek_networkNative(env);
    
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
    char proc_name[30];
    if(getprocname(getpid(), proc_name, 29) == 0) {
        ALOGD("!!!!!!! JNI_OnLoad OK !! %s\n", proc_name);
        if(strncmp(proc_name, "android.jni.cts", 16) == 0) {
            return JNI_VERSION_1_4;
        }
    }

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

    if (!registerNatives(env)) {//×¢²á
        
        ALOGE("registerNatives fail in !");
        return -1;
    }
    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

    return result;
}



