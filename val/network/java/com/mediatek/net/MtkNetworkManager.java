package com.mediatek.net;


import android.content.Context;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import com.mediatek.twoworlds.tv.MtkTvConfig;
import com.mediatek.twoworlds.tv.MtkTvNetwork;
import java.util.List;
/**
 * This class provides the specil function provide by mediatek.
 */
public class MtkNetworkManager {

    private static final String TAG = "MtkNetworkManager";
    private MtkTvNetwork mNetwork = null;
    private MtkTvConfig mMtkTvConfig = null;
    private MtkNetworkNative mNative = null;
    private static MtkNetworkManager mInstance;    

    
    public static MtkNetworkManager getInstance() {
        if(mInstance == null)
        {
            mInstance = new MtkNetworkManager();
        }
         
        return mInstance;
    }    

    private MtkNetworkManager(){
        Log.d(TAG, "Create MtkNetworkManager");
        mNetwork = MtkTvNetwork.getInstance(); 
        mMtkTvConfig = new MtkTvConfig();
        mNative = new MtkNetworkNative("wlan0");
    }  

    public boolean enableWolAndWoWL(boolean b){
        boolean result = false;
        result = setEnableWoWL(b) && setEnableWol(b);
        Log.d(TAG, "enableWolAndWoWL api result is " + result);
        return result;
    }

    public boolean isWolAndWowlEnable(){
        boolean result = false;
        result = isEnanbleWoWL() && isEnableWol();
        Log.d(TAG, "isWolAndWowlEnable api result is " + result);
        return result;
    }
    
    public boolean setEnableWoPacket(boolean b){
        boolean result = false;
        result = mNative.setWoPacketNative(b);
        Log.d(TAG, "setEnableWoPacket api driver result is " + result);
        if(result){
            result = mMtkTvConfig.setConfigValue(1, "g_network__wake_on_packet",b == true?1:0,0) == 0;
            Log.d(TAG, "setEnableWoPacket api TvConfig result is " + result);
        }
        return result;
    }

    public boolean isWoPacketEnable(){
        int ret = 0;
        ret = mMtkTvConfig.getConfigValue(1, "g_network__wake_on_packet");
        Log.d(TAG, "isWoPacketEnable api getTvConfig value is " + ret);
        return ret == 1;
    }
  
    public boolean setWifiPsAwakeInterval(int interval){	
        boolean result = false;	
        result = mNative.setWifiPsAwakeIntervalNative(interval);
        Log.d(TAG, "setWifiPsAwakeInterval api result is " + result);
        return result;
    }

    public boolean setEnableWifiPsAwake(boolean b){
        boolean result = false;
        result = mNative.setEnableWifiPsAwakeNative(b);
        Log.d(TAG, "setEnableWifiPsAwake api driver result is " + result);
        if(result){
            result = mMtkTvConfig.setConfigValue(1, "g_misc__ps_awake",b == true?1:0,0) == 0;
            Log.d(TAG, "setEnableWifiPsAwake api TvConfig result is " + result);
        }
        return result;
    }

    public boolean setEnableWifiCSA(boolean b){
        boolean result = false;
        result = mNative.setEnableWifiCSANative(b);
        Log.d(TAG, "setEnableWifiCSA api driver result is " + result);
        if(result){
            result = mMtkTvConfig.setConfigValue(1, "g_misc__wifi_csa",b == true?1:0,0) == 0;
            Log.d(TAG, "setEnableWifiCSA api TvConfig result is " + result);
        }
        return result;
    }

    public boolean isWifiPsAwakeEnable(){
        int ret = 0;
        ret = mMtkTvConfig.getConfigValue(1, "g_misc__ps_awake");
        Log.d(TAG, "isWifiPsAwakeEnable api result is " + ret);
        return ret == 1 ? true : false;
    }

    public boolean isWifiCSAEnable(){
        int ret = 0;
        ret = mMtkTvConfig.getConfigValue(1, "g_misc__wifi_csa");
        Log.d(TAG, "isWifiCSAEnable api result is " + ret);
        return ret == 1 ? true : false;
    }

    /**
    * This API query whether the wowl are enabled or not.   
    * @return true if  enabled, 
    *              false if disabled.
    */
    public boolean isEnanbleWoWL(){
        boolean result = false;
        result = mNetwork.getWifiWolCtl();
        Log.d(TAG, "isEnanbleWoWL api result is " + result);
        return result;
    }

    /**
    * This API will set the wowl(wake on wlan) enable or disable.
    * @param [in] enable
    *                   true is to enable wowl.
    *                   false is to disable.
    * @return true if success, false if fail.
    */
    public boolean setEnableWoWL(boolean enable){       
        boolean result = false;
        result = mNative.wifiNativeSetWowl(enable);
        Log.d(TAG, "setEnableWoWL api driver result is " + result);
        if(result){
           result = mNetwork.setWifiWolCtl(enable);
           Log.d(TAG, "setEnableWoWL api TvConfig result is " + result);
        }
        return result;
    }
    
        /**
        * This API will set the wol(wake on lan) enable or disable.
        * @param [in] enable
        *                   true is to enable.
        *                   false is to disable .
        * @return true if success, false if fail.
        */
    public boolean setEnableWol(boolean enable){
        boolean result = false;
        result = mNetwork.setEthernetWolCtl(enable);
        Log.d(TAG, "setEnableWol api TvConfig result is " + result);
        return result;
    }

        /**
        * This API will set the wol/wowl enable or disable.
        * @return true if enable, false if disable.
        */
    public boolean isEnableWol(){
        boolean result = false;
        result = mNetwork.getEthernetWolCtl();
        Log.d(TAG, "isEnableWol api result is " + result);
        return result;
    }
  
    public boolean sendPppoePADI(int retryTime, List<PppoeTag> tags){
        boolean result = false;
        result = mNative.sendPppoePADI(retryTime, tags);
        Log.d(TAG, "sendPppoePADI api result is " + result);
        return result;
    }

    public boolean waitPppoePADO(int timetout){
        boolean result = false;
        result = mNative.waitPppoePADO(timetout);
        Log.d(TAG, "waitPppoePADO api result is " + result);
        return result;
    }
}
