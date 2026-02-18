/* ---------------------------------------------------------------------------- *
 * Copyright Statement:                                                         *
 *                                                                              *
 * This software/firmware and related documentation ("MediaTek Software") are   *
 * protected under relevant copyright laws. The information contained herein is *
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without  *
 * the prior written permission of MediaTek inc. and/or its licensors, any      *
 * reproduction, modification, use or disclosure of MediaTek Software, and      *
 * information contained herein, in whole or in part, shall be strictly         *
 * prohibited.                                                                  *
 *                                                                              *
 * Copyright  (C) [2022]  MediaTek Inc. All rights reserved.                    *
 *                                                                              *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES  *
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")      *
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER   *
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL           *
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED     *
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR           *
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH  *
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,             *
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES *
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.    *
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO *
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK        *
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE   *
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR      *
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S  *
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE        *
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE   *
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE   *
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.     *
 *                                                                              *
 * The following software/firmware and/or related documentation ("MediaTek      *
 * Software") have been modified by MediaTek Inc. All revisions are subject to  *
 * any receiver's applicable license agreements with MediaTek Inc.              *
 * ---------------------------------------------------------------------------- */

package com.mediatek.net;
import android.provider.Settings;

import android.content.Context;
import android.util.Log;

/**
 * This class provides the specil function provide by mediatek.
 */
public class MtkNetworkManager {

    private static final String TAG = "MtkNetworkManager";
    private MtkNetworkNative mNative = null;
    private static MtkNetworkManager mInstance;


    public static MtkNetworkManager getInstance() {
        if (mInstance == null) {
            mInstance = new MtkNetworkManager();
        }

        return mInstance;
    }

    private MtkNetworkManager(){
        Log.d(TAG, "Create MtkNetworkManager");
        mNative = new MtkNetworkNative();
    }

    public boolean enableWolAndWoWL(boolean enable) {
        boolean result = false;
        result = setEnableWoWL(enable) && setEnableWol(enable);
        Log.d(TAG, "enableWolAndWoWL api result is " + result);
        return result;
    }

    public boolean setEnableWoPacket(boolean enable) {
        Log.d(TAG, "setEnableWoPacket is " + enable);
        return enable;
    }

    public boolean setWifiPsAwakeInterval(int interval) {
        boolean result = false;
        result = mNative.setWifiPsAwakeIntervalNative(interval);
        Log.d(TAG, "setWifiPsAwakeInterval api result is " + result);
        return result;
    }

    public boolean setEnableWifiPsAwake(boolean enable) {
        boolean result = false;
        result = mNative.setEnableWifiPsAwakeNative(enable);
        Log.d(TAG, "setEnableWifiPsAwake api driver result is " + result);
        return result;
    }

    public boolean setEnableWifiCSA(boolean enable) {
        boolean result = false;
        result = mNative.setEnableWifiCSANative(enable);
        Log.d(TAG, "setEnableWifiCSA api driver result is " + result);
        return result;
    }

    public boolean setEnableWoP(boolean enable, Context context) {
        boolean result = false;
        if (context == null){
            Log.e(TAG, "setEnableWoP context is null");
        }else{
            Settings.Global.putInt(context.getContentResolver(), "M_WopEnable", enable ? 1 : 0);
            result = (Settings.Global.getInt(context.getContentResolver(), "M_WopEnable", 0) == 1) ? true : false;
        }
        Log.d(TAG, "setEnableWoP is " + result);
        return result;
    }

    public boolean isWoPEnabled(Context context) {
        boolean result = false;
        if (context == null){
            Log.e(TAG, "isWoPEnabled context is null");
        }else{
            result = (Settings.Global.getInt(context.getContentResolver(), "M_WopEnable", 0) == 1) ? true : false;
        }
        Log.d(TAG, "isWoPEnabled : " + result);
        return result;
    }

    public boolean setEnableWakeLockTimerforWolandWoWL(boolean enable, Context context) {
        boolean result = false;
        if (context == null){
            Log.e(TAG, "setEnableWakeLockTimerforWolandWoWL context is null");
        }else{
            Settings.Global.putInt(context.getContentResolver(), "M_WakeOnyEnable", enable ? 1 : 0);
            result = (Settings.Global.getInt(context.getContentResolver(), "M_WakeOnyEnable", 0) == 1) ? true : false;
        }
        Log.d(TAG, "setEnableWakeLockTimerforWolandWoWL is " + result);
        return result;
    }

    public boolean isEnableWakeLockTimerforWolandWoWL(Context context) {
        boolean result = false;
        if (context == null){
            Log.e(TAG, "isEnableWakeLockTimerforWolandWoWL context is null");
        }else{
            result = (Settings.Global.getInt(context.getContentResolver(), "M_WakeOnyEnable", 0) == 1) ? true : false;
        }
        Log.d(TAG, "isEnableWakeLockTimerforWolandWoWL : " + result);
        return result;
    }

   /**
    * This API will set the wowl(wake on wlan) enable or disable.
    * @param [in] enable
    *                   true is to enable wowl.
    *                   false is to disable.
    * @return true if success, false if fail.
    */
    public boolean setEnableWoWL(boolean enable) {
        boolean result = false;
        result = mNative.wifiNativeSetWowl(enable);
        Log.d(TAG, "setEnableWoWL api driver result is " + result);
        return result;
    }

    /**
    * This API will set WOL(Wake on Lan) enable or disable for Ethernet.
    * @param [in] enable
    *                   true is to enable.
    *                   false is to disable .
    * @return true if success, false if fail.
    */
    public boolean setEnableWol(boolean enable) {
        boolean result = false;
        result = mNative.setEnableEthernetWolNative(enable);
        Log.d(TAG, "setEnableWol api driver result is " + result);
        return result;
    }

    public boolean setEnableLpm(boolean enable, Context context) {
        boolean result = false;
        if (context == null){
            Log.e(TAG, "setEnableLpm context is null");
        }else{
            Settings.Global.putInt(context.getContentResolver(), "M_isLowPowerModeEnable", enable ? 1 : 0);
            result = (Settings.Global.getInt(context.getContentResolver(), "M_isLowPowerModeEnable", 0) == 1) ? true : false;
        }
        Log.d(TAG, "setEnableLpm is " + result);
        return result;
    }

    public boolean isEnableLpm(Context context) {
        boolean result = false;
        if (context == null){
            Log.e(TAG, "isEnableLpm context is null");
        }else{
            result = (Settings.Global.getInt(context.getContentResolver(), "M_isLowPowerModeEnable", 0) == 1) ? true : false;
        }
        Log.d(TAG, "isEnableLpm : " + result);
        return result;
    }
}
