package com.mediatek.net;
import android.util.Log;

public class MtkNetworkNative {
    private static final String TAG = "MtkNetworkNative";

    static {
        Log.d(TAG, "MtkNetworkNative begin load libcom_mediatek_net_jni.so!");
        try {
            System.loadLibrary("com_mediatek_net_jni");
        } catch (UnsatisfiedLinkError ule) {
            Log.i(TAG, "WARNING: Could not load library!");
        }
    }

    public MtkNetworkNative() {
        Log.d(TAG, "structure");
    }

    public static native boolean wifiNativeSetWowl(boolean enable);
    public static native boolean setWoPacketNative(boolean enable);
    public static native boolean setEnableWifiPsAwakeNative(boolean enable);
    public static native boolean setEnableWifiCSANative(boolean enable);
    public static native boolean setWifiPsAwakeIntervalNative(int interval);
    public static native boolean setWoPacketListenPortNative();
    public static native boolean clearWoPacketListenPortNative();
    public static native boolean setEnableEthernetWolNative(boolean enable);
    public static native boolean isEthernetWolEnabledNative();
    public static native String startMonitorNetworkWakeUpNative(String iface);
    public static native boolean stopMonitorNetworkWakeUpNative();
    public static native boolean setPacketListenPortNative(int udp_local[], int tcp_local[], int tcp_remote[]);
}
