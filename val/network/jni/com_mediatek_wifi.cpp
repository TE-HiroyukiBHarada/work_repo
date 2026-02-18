#include <jni.h>
#include <stdlib.h>
#include "utils/Log.h"
#include <string.h>
#include <errno.h>
#include <stdarg.h>
#include "hardware_legacy/wifi.h"
//#include <stdio.h>
#include "cutils/properties.h"
#include "u_common.h"

extern "C" {
#include "app_util/mtktvapi/mtktvapi_config_type_custom.h"
#include "app_util/mtktvapi/a_mtktvapi_config.h"
}

#include <stdlib.h>
#include <inttypes.h>
#include <utils/Log.h>
#include <asm/types.h>
#include <sys/socket.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <netinet/in.h>
#include <poll.h>
#include <net/if_arp.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <ctype.h>

#include <vendor/mediatek/hardware/pppoeproxy/1.0/IPppoeProxy.h>
using namespace vendor::mediatek::hardware::pppoeproxy;
using namespace vendor::mediatek::hardware::pppoeproxy::V1_0;
#include <vendor/mediatek/hardware/tv/networkproxy/1.0/INetworkProxy.h>
using namespace vendor::mediatek::hardware::tv::networkproxy;
using namespace vendor::mediatek::hardware::tv::networkproxy::V1_0;
using ::android::hardware::hidl_vec;


static struct listField {
    jmethodID sizeId;
    jmethodID getId;
    jmethodID addId;
} listFieldIds;
static struct pppoeTagFieldIds{        
     jclass pppoeTagClass;        
     jmethodID constructorId;
     jfieldID type;
     jfieldID length;
     jfieldID value;
}pppoeTagFileld;


#define LOG_TAG "MediatekWifi"

#define JNIREG_CLASS "com/mediatek/net/MtkNetworkNative"

/*
* This function used to enable or disable the WoWL function. 
*WoWL: Wake over WLAN
*@param ifname, interface name wlan0..
*@param : 0, disalbe , 1 ,enable
*@return : 0, operation success, other : fail.
*/

JNIEXPORT jboolean JNICALL native_wifi_set_wowl(JNIEnv *env, jclass clazz, jboolean enable)
{
    android::sp<INetworkProxy> networkproxy = INetworkProxy::getService();
    if(networkproxy == NULL){
        ALOGE("Couldn't get network service");
        return (jboolean)0;
    }

    if(networkproxy->setWOWEnabled(enable) != vendor::mediatek::hardware::tv::networkproxy::V1_0::Result::OK )
    {
        ALOGE("jni call networkproxy->setWOWEnabled failed");
        return (jboolean)0;
    }else{
        return (jboolean)1;
    }
}
JNIEXPORT jboolean JNICALL native_set_wopacket(JNIEnv *env, jclass clazz, jboolean enable)
{
    android::sp<INetworkProxy> networkproxy = INetworkProxy::getService();
    if(networkproxy == NULL){
        ALOGE("Couldn't get network service");
        return (jboolean)0;
    }

    if(networkproxy->setWakeOnPortEnabled(enable) != vendor::mediatek::hardware::tv::networkproxy::V1_0::Result::OK )
    {
        ALOGE("jni call networkproxy->setWakeOnPortEnabled failed");
        return (jboolean)0;
    }else{
        return (jboolean)1;
    }

}

JNIEXPORT jboolean JNICALL native_set_wifi_ps_awake(JNIEnv *env, jclass clazz, jboolean enable)
{
    android::sp<INetworkProxy> networkproxy = INetworkProxy::getService();
    if(networkproxy == NULL){
        ALOGE("Couldn't get network service");
        return (jboolean)0;
    }

    if(networkproxy->setPsAwakeEnabled(enable) != vendor::mediatek::hardware::tv::networkproxy::V1_0::Result::OK )
    {
        ALOGE("jni call networkproxy->setPsAwakeEnabled failed");
        return (jboolean)0;
    }else{
        return (jboolean)1;
    }
}
JNIEXPORT jboolean JNICALL native_set_wifi_csa(JNIEnv *env, jclass clazz, jboolean enable)
{
    android::sp<INetworkProxy> networkproxy = INetworkProxy::getService();
    if(networkproxy == NULL){
        ALOGE("Couldn't get network service");
        return (jboolean)0;
    }

    if(networkproxy->setCSAEnabled(enable) != vendor::mediatek::hardware::tv::networkproxy::V1_0::Result::OK )
    {
        ALOGE("jni call networkproxy->setCSAEnabled failed");
        return (jboolean)0;
    }else{
        return (jboolean)1;
    }
}

JNIEXPORT jboolean JNICALL native_set_wifi_ps_awake_interval(JNIEnv *env, jclass clazz, jint interval)
{

    android::sp<INetworkProxy> networkproxy = INetworkProxy::getService();
    if(networkproxy == NULL){
        ALOGE("Couldn't get network service");
        return (jboolean)0;
    }

    if(networkproxy->setPsAwakeInterval(interval) != vendor::mediatek::hardware::tv::networkproxy::V1_0::Result::OK )
    {
        ALOGE("jni call networkproxy->setPsAwakeInterval failed");
        return (jboolean)0;
    }else{
        return (jboolean)1;
    } 
}
JNIEXPORT jboolean JNICALL native_is_wopacket_enable(JNIEnv *env, jclass clazz)
{
    ALOGD ("native_is_wopacket_enable");
    INT32       i4_r        = -3;
    INT32   i4_value    = 0;
    i4_r = a_mtktvapi_config_get_value(1,CFG_NETWORK_WAKE_ON_PACKET,&i4_value);
    if(MTKTVAPIR_OK == i4_r)
    {
        ALOGD ("succes to call native_is_wopacket_enable");
        return i4_value == 1;
    }else
    {
        ALOGD ("failed to call native_is_wopacket_enable");
        return 0;
    }
}

static jboolean android_net_pppoe_sendPADI(JNIEnv* env, jobject clazz, jint retryTimes, jobject tags)
    {
        unsigned char payload[ETH_DATA_LEN] = {0};        
        unsigned char *cursor = payload;
        const char * s_value = NULL;
        typedef struct PPPoETagStruct {
            unsigned int type:16;   /* tag type */
            unsigned int length:16; /* Length of payload */
            unsigned char payload[ETH_DATA_LEN]; /* A LOT of room to spare */
        } PPPoETag;

        unsigned short payloadLength  = 0; 
        
        int i = 0;
        int tmp = 0;
        int size = env->CallIntMethod(tags, listFieldIds.sizeId);
        ALOGD("android_net_pppoe_sendPADI , retryTimes = %d, the list size = %d", retryTimes, size);

        for(i = 0; i< size; i++)
        {
            jobject tag = env->CallObjectMethod(tags, listFieldIds.getId, i);
            if(tag != NULL)
            {
                int type = env->GetIntField(tag, pppoeTagFileld.type);
                int length =  env->GetIntField(tag, pppoeTagFileld.length);
                jstring value = (jstring) env->GetObjectField(tag, pppoeTagFileld.value);  
                s_value = env->GetStringUTFChars(value,0);
                //ScopedUtfChars s_value(env, value);
                PPPoETag tempTag;
                memset(&tempTag, 0, sizeof(PPPoETag));
                tmp = (type << 8) & 0x0000FF00; 
                tempTag.type   = ((type >> 8) & 0x000000FF) | tmp;
                tmp = (length << 8) & 0x0000FF00;
                tempTag.length = ((length >> 8) & 0x000000FF) | tmp;
                memcpy(tempTag.payload, s_value, length);
                payloadLength += 4+length;
                ALOGD("android_net_pppoe_sendPADI, tag %d, type = %x",i, type);
                ALOGD("android_net_pppoe_sendPADI, tag %d, length = %d", i, length);
                ALOGD("android_net_pppoe_sendPADI, tag %d, VALUE = %s", i, s_value);
                memcpy(cursor, &tempTag, 4+length);
                cursor += 4+length;
                env->ReleaseStringUTFChars(value,s_value);
            }
        }
        
        ALOGD("payloadLength = %d", payloadLength);
        android::sp<IPppoeProxy> pppoeproxy = IPppoeProxy::getService();
        if(pppoeproxy == NULL){
             ALOGE("Couldn't get pppoe service");
             return (jboolean)0;
        }
        std::vector<unsigned char> vecdata;
        for (int i = 0; i < payloadLength; ++i) {
            vecdata.push_back(*(payload + i));
        }
        if(pppoeproxy->pppoeSendPadi(retryTimes,vecdata, payloadLength) != vendor::mediatek::hardware::pppoeproxy::V1_0::Result::OK )
        {
             ALOGE("jni call pppoeproxy SendPadi failed");
             return (jboolean)0;
        }
        
        return (jboolean)1;
    }

static jboolean android_net_pppoe_waitPADO(JNIEnv* env, jobject clazz, jint timeout){
        ALOGD("android_net_pppoe_waitPADO");
        android::sp<IPppoeProxy> pppoeproxy = IPppoeProxy::getService();
        if(pppoeproxy == NULL){
             ALOGE("Couldn't get pppoe service");
             return (jboolean)0;
        }
        return (jboolean)(pppoeproxy->pppoeWaitPado(timeout) == vendor::mediatek::hardware::pppoeproxy::V1_0::Result::OK);
    }


static JNINativeMethod gMethods[] = {
    { "wifiNativeSetWowl", "(Z)Z", (void*)native_wifi_set_wowl },
    { "setWoPacketNative","(Z)Z", (void*)native_set_wopacket},
    { "setEnableWifiPsAwakeNative","(Z)Z", (void*)native_set_wifi_ps_awake},
    { "setEnableWifiCSANative","(Z)Z", (void*)native_set_wifi_csa},
    { "setWifiPsAwakeIntervalNative","(I)Z", (void*)native_set_wifi_ps_awake_interval},
    { "isWoPacketEnableNative","()Z", (void*)native_is_wopacket_enable},
    { "sendPppoePADI", "(ILjava/util/List;)Z",(void*) android_net_pppoe_sendPADI},            
    { "waitPppoePADO", "(I)Z",(void*) android_net_pppoe_waitPADO},

};

int register_com_mediatek_wifiNative(JNIEnv* env)
{
    jclass listClass = env->FindClass("java/util/List");
    LOG_FATAL_IF(listClass == NULL, "Unable to find class java/util/List");
    listFieldIds.sizeId = env->GetMethodID(listClass, "size", "()I");
    listFieldIds.getId  = env->GetMethodID(listClass, "get" , "(I)Ljava/lang/Object;" );
    listFieldIds.addId  = env->GetMethodID(listClass, "add" , "(ILjava/lang/Object;)V" );

    pppoeTagFileld.pppoeTagClass = env->FindClass("com/mediatek/net/PppoeTag");
    if (pppoeTagFileld.pppoeTagClass != NULL) {
        pppoeTagFileld.constructorId = env->GetMethodID(pppoeTagFileld.pppoeTagClass,
                                                    "<init>", "()V");
        pppoeTagFileld.type = env->GetFieldID(pppoeTagFileld.pppoeTagClass,
                                "mType", "I");
        pppoeTagFileld.length = env->GetFieldID(pppoeTagFileld.pppoeTagClass,
                                "mLength", "I");
        pppoeTagFileld.value = env->GetFieldID(pppoeTagFileld.pppoeTagClass,
                                "mValue", "Ljava/lang/String;");
        }
    jclass clazz;
    clazz = env->FindClass(JNIREG_CLASS);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", JNIREG_CLASS);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) < 0)
    {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}


