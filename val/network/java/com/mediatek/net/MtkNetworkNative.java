package com.mediatek.net;
import android.util.Log;
import com.mediatek.net.PppoeTag;
import java.util.List;

public class MtkNetworkNative {
    private static final String TAG = "MtkNetworkNative";
    private String mInterface;
    static {
        Log.d(TAG, "MtkNetworkNative begin load  libcom_mediatek_net_jni.mediatek.so !");
        try {
            System.loadLibrary("com_mediatek_net_jni.mediatek");
        } catch (UnsatisfiedLinkError ule) {
            Log.i(TAG, "WARNING: Could not load library!");
        }
    }
    
    public MtkNetworkNative(String iface){
        Log.d(TAG, "structure");

        mInterface = iface;
    }


    public static native boolean sendPppoePADI(int retryTime, List<PppoeTag> tags);
    public static native boolean waitPppoePADO(int timeout);

    public static native boolean wifiNativeSetWowl(boolean enable);
    public static native boolean setWoPacketNative(boolean enable);
    public static native boolean setEnableWifiPsAwakeNative(boolean enable);
    public static native boolean setEnableWifiCSANative(boolean enable);
    public static native boolean setWifiPsAwakeIntervalNative(int interval);
    public static native boolean isWoPacketEnableNative();

    
}
