/*
 * Copyright 2022,2023 Sony Corporation
 */

package com.mediatek.network;

import android.util.Log;
import android.app.Service;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.net.NetworkInfo;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import java.io.InputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.ServerSocket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.BaseEncoding;
import com.mediatek.net.MtkNetworkManager;
import com.mediatek.net.MtkNetworkNative;
import com.mediatek.tv.oneworld.tvapi.network.TvNetworkManager;

public final class MtkNetworkMonitorService extends Service {

    private static final String TAG = "MtkNetworkMonitorService";
    private static final int WOP_PACKET_BUFFER_SIZE = 1518;
    private static final int MAGIC_PACKET_DATA_SIZE = 102;
    private static final int WAKEUP_MAC_ADDRESS_COUNT = 16;
    private static final int MAC_ADDRESS_BYTE_SIZE = 6;
    private static final int FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000;  // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
    private static final int MACOFFSET = 14;
    private static final int PROTOCOLTYPEV4 = 9;
    private static final int PROTOCOLTYPEV6 = 6;
    private static final int IPV6HEADERLEN = 40;
    private static boolean mWopListen = false;
    private static boolean isEnableWol = false;
    private static boolean isEnableWoWL = false;
    private static boolean isEnableWoc = false;
    private static boolean isEnableWop = false;
    private static boolean isEnableLpm = false;
    private static boolean isEnableWakeLockOnly = false;
    private static boolean isScreenOn = false;
    private static boolean isRunnablePosted = false;
    private Context context;

    private ConnectivityManager mConnectivityManager;
    private PowerManager mPowerManager;
    private MtkNetworkNative mMtkNetworkNative = null;
    private MtkNetworkManager mMtkNetworkManager = null;
    private FileDescriptor mWopSocketFd = null;
    private Thread mWopListenThread = null;
    private int[] mNullPort = new int[0];
    private int[] mUdpPort = new int[]{5353,5354};
    private int[] mWocUdpPort = new int[]{5353};
    private int[] mLpmUdpPort = new int[]{5354};
    private int[] mTcpRemotePort = new int[]{5228, 5229, 5230, 443};
    private byte[] mByteArrayMagicPacketEth0 = new byte[MAGIC_PACKET_DATA_SIZE];
    private byte[] mByteArrayMagicPacketWlan0 = new byte[MAGIC_PACKET_DATA_SIZE];
    private static String mWifiSSID = "<unknown ssid>";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Handler mHandler;
    private IntentFilter mScreenOnOffIntentFilter;
    private BroadcastReceiver mScreenOnOffBroadcastReceiver;
    private final NetworkCallback mNetworkCallback = new NetworkCallback(){
        @Override
        public void onLost(Network network){
            Log.i(TAG, "onLost");
            if (isRunnablePosted) {
                isRunnablePosted = false;
                periodicHandler.removeCallbacks(periodicRunnable);
            }
            mMtkNetworkNative.clearWoPacketListenPortNative();
        }

        @Override
        public void onAvailable(Network network){
            Log.i(TAG, "onAvailable");
            handleScreenOffAndActiveNetwork(context);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.i(TAG, "onLinkPropertiesChanged");
            notifySSID();
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities mNetworkCapabilities) {
            Log.i(TAG, "onCapabilitiesChanged");
            notifyMacAddress();
        }

        public void notifySSID(){
            WifiManager mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            WifiInfo currentWifi = mainWifi.getConnectionInfo();
            if(currentWifi!=null){
                if(currentWifi.getSSID().equals(mWifiSSID)){
                    Log.d(TAG, "The same SSID no need to send intent, SSID = " + currentWifi.getSSID());
                }
                else {
                    try {
                        Intent intent_ssid = new Intent();
                        intent_ssid.setAction("Intent.wifiInfo.ssid");
                        intent_ssid.setPackage("com.mediatek.partner.airplay_sys");
                        intent_ssid.putExtra("SSID", currentWifi.getSSID());
                        intent_ssid.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                        sendBroadcast(intent_ssid,"com.mediatek.permission.AirPlay.BroadCast");
                        Log.d(TAG, "SSID broadcast to AirPlay, SSID = "+ currentWifi.getSSID());
                    } catch (Throwable e) {}
                }
            }
        }
    };

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        context = getApplicationContext();
        mConnectivityManager = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE));
        mPowerManager = ((PowerManager) getSystemService(Context.POWER_SERVICE));
        mHandler = new Handler(Looper.getMainLooper());
        mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback,mHandler);
        if (mMtkNetworkNative == null) {
            mMtkNetworkNative = new MtkNetworkNative();
        }
        if (mMtkNetworkManager == null){
            mMtkNetworkManager = MtkNetworkManager.getInstance();
        }

        mScreenOnOffIntentFilter = new IntentFilter();
        mScreenOnOffIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mScreenOnOffIntentFilter.addAction(Intent.ACTION_SCREEN_ON);

        mScreenOnOffBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive(ScreenOnOffBroadcast)");

                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.d(TAG, "SCREEN_OFF");
                    handleScreenOffAndActiveNetwork(context);
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.d(TAG, "SCREEN_ON");
                    if (isRunnablePosted) {
                        isRunnablePosted = false;
                        periodicHandler.removeCallbacks(periodicRunnable);
                    }

                    isEnableWol = TvNetworkManager.isEnableWol(context);
                    isEnableWoWL = TvNetworkManager.isEnanbleWoWL(context);
                    if (isEnableWol || isEnableWoWL){
                        stopWopMonitor();
                    }
                }
            }
        };
        registerReceiver(mScreenOnOffBroadcastReceiver, mScreenOnOffIntentFilter);
    }

    /**
    * Convert packets from byte array to String.
    * @param [in] bytes Packets with byte array type.
    * @return String Packets returned by String type.
    */
    private String encodeUsingBigIntegerStringFormat(byte[] bytes) {
        BigInteger bigInteger = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "x", bigInteger);
    }

    /**
    * Convert packets from byte array to String.
    * @param [in] bytes Packets with byte array type.
    * @return String Packets returned by String type.
    */
    private String encodeUsingBigIntegerToString(byte[] bytes) {
        BigInteger bigInteger = new BigInteger(1, bytes);
        return bigInteger.toString(16);
    }

    /**
    * Initialize a byte array with magic packet format for WOL/WOWL magic packet checking.
    * @return true if success,
    *         false if fail.
    */
    private boolean initMagicPacket() {
        Log.d(TAG, "initMagicPacket()");

        int i = 0;
        int j = 0;
        byte[] byteArrayMacAddress = null;

        int networkType = getActiveNetworkType();
        if (networkType == ConnectivityManager.TYPE_ETHERNET) {
            byteArrayMacAddress = getEthernetMacAddress();
            if (byteArrayMacAddress == null) {
                Log.d(TAG, "getEthernetMacAddress() failed => can not finish initMagicPacket()");
                return false;
            }

            // Create broadcast address
            for (i = 0; i < MAC_ADDRESS_BYTE_SIZE; i ++) {
                mByteArrayMagicPacketEth0[i] = (byte)0xff;
            }

            // Create a prototype of a Magic Packet
            for (i = 0; i < WAKEUP_MAC_ADDRESS_COUNT; i ++) {
                for (j = 0; j < MAC_ADDRESS_BYTE_SIZE; j ++) {
                    mByteArrayMagicPacketEth0[i*MAC_ADDRESS_BYTE_SIZE+j+MAC_ADDRESS_BYTE_SIZE] = byteArrayMacAddress[j];
                }
            }
        } else if (networkType == ConnectivityManager.TYPE_WIFI) {
            byteArrayMacAddress = getWiFiMacAddress();
            if (byteArrayMacAddress == null) {
                Log.d(TAG, "getWiFiMacAddress() failed => can not finish initMagicPacket()");
                return false;
            }

            // Create broadcast address
            for (i = 0; i < MAC_ADDRESS_BYTE_SIZE; i ++) {
                mByteArrayMagicPacketWlan0[i] = (byte)0xff;
            }

            // Create a prototype of a Magic Packet
            for (i = 0; i < WAKEUP_MAC_ADDRESS_COUNT; i ++) {
                for (j = 0; j < MAC_ADDRESS_BYTE_SIZE; j ++) {
                    mByteArrayMagicPacketWlan0[i*MAC_ADDRESS_BYTE_SIZE+j+MAC_ADDRESS_BYTE_SIZE] = byteArrayMacAddress[j];
                }
            }
        } else {
            Log.e(TAG, "The network type is " + networkType + ", but WOL/WOWL is supported by Ethernet/WiFi only.");
            return false;
        }
        return true;
    }

    /**
    * Broad mac address to other application.
    */
    public void notifyMacAddress() {
        byte[] macAddress = null;
        int networkType = getActiveNetworkType();
        if (networkType == ConnectivityManager.TYPE_ETHERNET) {
            macAddress = getEthernetMacAddress();
            if (macAddress == null) {
                Log.e(TAG, "getEthernetMacAddress() failed => can not finish notifyMacAddress()");
                return;
            }
        } else if (networkType == ConnectivityManager.TYPE_WIFI) {
            macAddress = getWiFiMacAddress();
            if (macAddress == null) {
                Log.e(TAG, "getWiFiMacAddress() failed => can not finish notifyMacAddress()");
                return;
            }
        } else {
            Log.e(TAG, "The network type is " + networkType + ", but notifyMacAddress is supported by Ethernet/WiFi only.");
            return;
        }

        try {
            Log.d(TAG, "notifyMacAddress broadcast Intent.wifiInfo.macaddress");
            Intent intent_mac = new Intent();
            intent_mac.setAction("Intent.wifiInfo.macaddress");
            intent_mac.setPackage("com.mediatek.airplaydaemon");
            intent_mac.addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND);
            intent_mac.putExtra("MacAddress", macAddress);
            sendBroadcast(intent_mac,"com.mediatek.permission.AirPlay.BroadCast");
        } catch (Throwable e) {}
    }

    /**
    * Get network type for the active network.
    * @return int e.g. TYPE_ETHERNET/TYPE_WIFI/TYPE_DUMMY
    */
    private int getActiveNetworkType() {
        Log.d(TAG, "getActiveNetworkType()");

        if (mConnectivityManager != null) {
            NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                Log.d(TAG, "Active Network Type = " + networkInfo.getType());
                return networkInfo.getType();
            }
        }
        return ConnectivityManager.TYPE_DUMMY;
    }

    /**
    * Get MAC address for Ethernet.
    * @return byte[] Ethernet MAC addresss
    */
    private byte[] getEthernetMacAddress() {
        Log.d(TAG, "getEthernetMacAddress()");

        if (mConnectivityManager != null) {
            final Network[] networks = mConnectivityManager.getAllNetworks();
            for (final Network network : networks) {
                NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(network);
                if ((networkInfo != null) && (networkInfo.getExtraInfo() != null) && networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    String[] strMacAddress = networkInfo.getExtraInfo().split(":");
                    byte[] byteArrayMacAddress = new byte[MAC_ADDRESS_BYTE_SIZE];
                    for (int i = 0; i < strMacAddress.length; i ++) {
                        Integer hexadecimal = Integer.parseInt(strMacAddress[i], 16);
                        byteArrayMacAddress[i] = hexadecimal.byteValue();
                    }
                    Log.d(TAG, "Ethernet MAC Address = " + networkInfo.getExtraInfo());
                    return byteArrayMacAddress;
                }
            }
        }
        return null;
    }

    /**
    * Get MAC address for WiFi.
    * @return byte[] WiFi MAC addresss
    */
    private byte[] getWiFiMacAddress() {
        Log.d(TAG, "getWiFiMacAddress()");

        NetworkInterface wifiInterface = null;
        wifiInterface = getNetworkInterfaceByName("wlan0");
        if (wifiInterface != null) {
            try {
                byte[] macBytes = wifiInterface.getHardwareAddress();
                if (macBytes != null) {
                    Log.d(TAG, "WiFi MAC Address = " + encodeUsingBigIntegerToString(macBytes));
                    return macBytes;
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static InetAddress getInterfaceAddress(NetworkInterface iface) {
        Enumeration<InetAddress> addrs = iface.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress addr = addrs.nextElement();
            if (addr instanceof Inet4Address) {
                return addr;
            }
        }
        return null;
    }

    private static NetworkInterface getNetworkInterfaceByName(String name) {
        try {
            if (name != null) {
                return NetworkInterface.getByName(name);
            } else {
                Log.e(TAG, "The parameter name can not be null for getNetworkInterfaceByName()");
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    private int BytesToInt32(byte buffer[], int offset, int len)
    {
        int result = 0;
        int i = 0;
        for (i = 0; i < len; i++){
            result <<= 8;
            result |= (buffer[offset + i] & 0xFF);
        }
        return result;
    }

    private boolean fcmPacketCheck(boolean wopEnabled, int srcport, int type) {
        boolean result = false;
        if (wopEnabled && type == 6){
            for (int i = 0; i < mTcpRemotePort.length; i++) {
                if (mTcpRemotePort[i] == srcport){
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private boolean udpPacketCheck(boolean wocEnabled, int dstport, int type) {
        boolean result = false;
        if (wocEnabled && type == 17){
            for (int i = 0; i < mUdpPort.length; i++) {
                if (mUdpPort[i] == dstport){
                    result = true;
                    break;
                }
            }
        }
        return result;
    }
    private boolean LpmPacketCheck(boolean Enabled, int dstport, int type) {
        boolean result = false;
        if (Enabled && type == 17){
            for (int i = 0; i < mLpmUdpPort.length; i++) {
                if (mLpmUdpPort[i] == dstport){
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private void wocWopWakeLock(WakeLock acquirewakeLock, int netType){
        Log.d(TAG, "Acquire Wake Lock 6000ms");
        if (netType == ConnectivityManager.TYPE_ETHERNET) {
                acquirewakeLock.acquire(6000);
            } else if (netType == ConnectivityManager.TYPE_WIFI) {
                acquirewakeLock.acquire(6000);
        }
    }

    private void wowlWakeLock(WakeLock wocwopwake, WakeLock wowlwake, boolean wacklockony){
        if (wacklockony) {
            Log.d(TAG, "wowl wake up, acquire Wake 6000ms");
            wocwopwake.acquire(6000);

            // Send MagicPacket received intent for B2B application
            Log.i(TAG, "Sending MagicPacket received intent");
            try {
                Field field = UserHandle.class.getDeclaredField("CURRENT");
                field.setAccessible(true);
                UserHandle handle = (UserHandle) field.get(UserHandle.class);
                if (handle != null){
                    Intent broadcast_intent = new Intent(
                        "com.sony.dtv.magicpacketnotifyservice.ACTION_RECEIVE_MAGICPACKET");
                    broadcast_intent.putExtra("type", 2); // type 2 represents magicpacket
                    context.sendBroadcastAsUser(broadcast_intent, handle,
                        "com.sony.dtv.magicpacketnotifyservice.RECEIVE_MAGICPACKET");
                }
            } catch (Exception e) {
                Log.e(TAG, "Sending MagicPacket received intent Exception: " + e.toString());
            }
        } else {
            wowlwake.acquire();
            Log.d(TAG,"wowl wake up, turn screen on");
            wowlwake.release();
            mWopListen = false;
        }
    }

    private final class WopMonitoerThread implements Runnable {
        public void run() {
            int networkType = getActiveNetworkType();
            if (networkType != ConnectivityManager.TYPE_ETHERNET && networkType != ConnectivityManager.TYPE_WIFI) {
                Log.e(TAG, "The network type is " + networkType + ", but WOC is supported by Ethernet/WiFi only.");
                return;
            }
            setWopThreadPriority();

            try {
                if (mWopSocketFd != null) {
                    Log.d(TAG, "mWopSocketFd is existed, first, Os.close(mWopSocketFd)");
                    Os.close(mWopSocketFd);
                    mWopSocketFd = null;
                }
                Log.d(TAG, "WOP socket create started");
                mWopSocketFd = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, OsConstants.ETH_P_ALL);
                Log.d(TAG, "WOP socket create finished");
                if (mWopSocketFd != null) {
                    byte[] WopReceivedPackets = new byte[WOP_PACKET_BUFFER_SIZE];
                    int recvsize = 0;
                    int mIPVer = 0;
                    int mProtocoltype = 0;
                    int mIPSourcePort = 0;
                    int mIPDestPort = 0;
                    IPPacketPrase mIPPacketPrase = new IPPacketPrase();
                    WakeLock wopwocwakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                    WakeLock wowwolwakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
                    mWopListen = true;
                    String strMagicPackets = null;
                    strMagicPackets = encodeEthOrWlanMagicPacket(networkType, strMagicPackets);
                    while (mWopListen) {
                        Arrays.fill(WopReceivedPackets, 0, WopReceivedPackets.length-1, (byte)0x00);
                        recvsize = Os.recvfrom(mWopSocketFd, WopReceivedPackets, 0, WopReceivedPackets.length, 0, null);
                        if (recvsize > 0) {
                            if (isEnableWol || isEnableWoWL) {
                                if (mIPPacketPrase.isContainMagic(WopReceivedPackets, networkType, recvsize, strMagicPackets)){
                                    Log.d(TAG, "The network type " + networkType + " received magic packet."
                                    + " Quit WOL/WOWL listen thread.");
                                    wowlWakeLock(wopwocwakeLock, wowwolwakeLock, isEnableWakeLockOnly);
                                    continue;
                                }
                            }
                            if (isEnableWop || isEnableWoc || isEnableLpm) {
                                mIPPacketPrase.initialPraseData();
                                mIPVer = mIPPacketPrase.getIPVersion(WopReceivedPackets);
                                if (mIPVer == 4){
                                    mIPPacketPrase.IPV4PacketPraseData(WopReceivedPackets);
                                } else if (mIPVer == 6){
                                    mIPPacketPrase.IPV6PacketPraseData(WopReceivedPackets);
                                } else {
                                    continue;
                                }
                                mProtocoltype = mIPPacketPrase.Protocoltype;
                                mIPSourcePort = mIPPacketPrase.SourcePort;
                                mIPDestPort = mIPPacketPrase.DestPort;
                                if (LpmPacketCheck(isEnableLpm, mIPDestPort, mProtocoltype) ||
                                    udpPacketCheck(isEnableWoc, mIPDestPort, mProtocoltype) ||
                                    fcmPacketCheck(isEnableWop, mIPSourcePort, mProtocoltype)){
                                    if (!wopwocwakeLock.isHeld()) {
                                        Log.d(TAG, "Received type " + mProtocoltype + " packet, Dest port is :"
                                        + mIPDestPort + " Src port is :" + mIPSourcePort + " networktype is :" + networkType);
                                        wocWopWakeLock(wopwocwakeLock, networkType);
                                    }
                                    if(LpmPacketCheck(isEnableLpm, mIPDestPort, mProtocoltype)){
                                    notifyDarkWakeToAirplay();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ErrnoException | IOException e) {
                e.printStackTrace();
            } finally {
                if (mWopSocketFd != null) {
                    Log.d(TAG, "Os.close(mWopSocketFd)");
                    try {
                        Os.close(mWopSocketFd);
                    } catch (ErrnoException e) {
                        e.printStackTrace();
                    }
                    mWopSocketFd = null;
                }
            }
        }

        private void notifyDarkWakeToAirplay() {
            Intent intent_lpm = new Intent();
            intent_lpm.setAction("Intent.wakereason.darkwake");
            intent_lpm.setPackage("com.mediatek.partner.airplay_sys");
            intent_lpm.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            sendBroadcast(intent_lpm,"com.mediatek.permission.AirPlay.BroadCast");
            Log.d(TAG, "darkwake broadcast send to AirPlay");
        }

        private String encodeEthOrWlanMagicPacket(int networkType, String strMagicPackets) {
            if (networkType == ConnectivityManager.TYPE_ETHERNET) {
                strMagicPackets = BaseEncoding.base16().encode(mByteArrayMagicPacketEth0);
                Log.d(TAG, "ethernet magic: " + strMagicPackets);
            } else if (networkType == ConnectivityManager.TYPE_WIFI) {
                strMagicPackets = BaseEncoding.base16().encode(mByteArrayMagicPacketWlan0);
                Log.d(TAG, "wlan magic: " + strMagicPackets);
            } else {
                Log.e(TAG, "The network type is " + networkType + ", but WOL/WOWL don't supported");
            }
            return strMagicPackets;
        }

        private void setWopThreadPriority() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_MORE_FAVORABLE);
            } catch (IllegalArgumentException illegal) {
                illegal.printStackTrace();
            } catch (SecurityException sec) {
                sec.printStackTrace();
            }
        }
    }

    public class IPPacketPrase
    {
        /*MAC : 6(dst) + 6(src) + 2(type) = 14*/
        public int Version = 0;           //4 ipv4 || 6 ipv6
        public int IpHeaderLen = 0;       //ipv4 header length
        public int Protocoltype = 0;      //6 TCP || 17 UDP
        public int SourcePort = 0;        //tcp/udp source port
        public int DestPort = 0;          //tcp/udp destination port

        public void IPV4PacketPraseData(byte[] buffer){
            Version = ((buffer[MACOFFSET] & 0xF0) >> 4) & 0xFF;
            IpHeaderLen = ((buffer[MACOFFSET] & 0x0F) & 0xFF) * 4;
            Protocoltype = BytesToInt32(buffer, MACOFFSET + PROTOCOLTYPEV4, 1);
            SourcePort = BytesToInt32(buffer, MACOFFSET + IpHeaderLen, 2);
            DestPort = BytesToInt32(buffer, MACOFFSET + IpHeaderLen + 2, 2);
        }

        public void IPV6PacketPraseData(byte[] buffer){
            Version = ((buffer[MACOFFSET] & 0xF0) >> 4) & 0xFF;
            Protocoltype = BytesToInt32(buffer, MACOFFSET + PROTOCOLTYPEV6, 1);
            SourcePort = BytesToInt32(buffer, MACOFFSET + IPV6HEADERLEN, 2);
            DestPort = BytesToInt32(buffer, MACOFFSET + IPV6HEADERLEN + 2, 2);
        }

        public void initialPraseData(){
            Version = 0;
            IpHeaderLen = 0;
            Protocoltype = 0;
            SourcePort = 0;
            DestPort = 0;
        }

        public int getIPVersion(byte[] buffer){
            Version = ((buffer[MACOFFSET] & 0xF0) >> 4) & 0xFF;
            return Version;
        }

        public boolean isContainMagic(byte[] buffer, int netType, int recvsize, String strMagicPackets){
            String strReceivedPackets = null;
            //Log.d(TAG, "isContainMagic netType: " + netType);
            if ((netType != ConnectivityManager.TYPE_ETHERNET) && (netType != ConnectivityManager.TYPE_WIFI)) {
                Log.e(TAG, "The network type is " + netType + ", but WOL/WOWL is supported by Ethernet/WiFi only.");
                return false;
            }
            strReceivedPackets = BaseEncoding.base16().encode(buffer,0,recvsize);
            //Log.d(TAG, "isContainMagic strReceivedPackets: " + strReceivedPackets);
            if (strReceivedPackets.contains(strMagicPackets)){
                return true;
            }else{
                return false;
            }
        }
    }

    private void startWopMonitor()
    {
        Log.d(TAG, "startWopMonitor()");
        mWopListenThread = new Thread(new WopMonitoerThread());
        mWopListen = true;
        mWopListenThread.start();
    }

    private void stopWopMonitor(){
        Log.d(TAG, "stopWopMonitor()");
        if (mWopListenThread != null) {
            mWopListen = false;
            mWopListenThread.interrupt();
            mWopListenThread = null;
        }
    }

    @Override
    public void onStart(Intent intent,int startID) {
        super.onStart(intent, startID);
        Log.i(TAG, "onStart");
    }

    @Override
    public int onStartCommand(Intent intent,int flags,int startID) {
        Log.i(TAG, "onStartCommand");
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        unregisterReceiver(mScreenOnOffBroadcastReceiver);
    }

    private void handleScreenOffAndActiveNetwork(Context context) {
        isScreenOn = mPowerManager.isInteractive();
        int networkType = getActiveNetworkType();
        Log.d(TAG, "The current network type is=" + networkType
                + ", isScreenOn=" + isScreenOn
                + ", mWopListenThread=" + (mWopListenThread != null));

        isEnableWol = TvNetworkManager.isEnableWol(context);
        isEnableWoWL = TvNetworkManager.isEnanbleWoWL(context);
        isEnableWoc = TvNetworkManager.isEnableWoc(context);
        isEnableWop = mMtkNetworkManager.isWoPEnabled(context);
        isEnableLpm = mMtkNetworkManager.isEnableLpm(context);
        isEnableWakeLockOnly = mMtkNetworkManager.isEnableWakeLockTimerforWolandWoWL(context);

        if (((networkType == ConnectivityManager.TYPE_ETHERNET && isEnableWol)
            || (networkType == ConnectivityManager.TYPE_WIFI && isEnableWoWL)) && (!isScreenOn)) {
            initMagicPacket();
            Log.d(TAG, "isEnableWoc= "+ isEnableWoc +" isEnableWop= "+isEnableWop +" isEnableLpm= "+isEnableLpm);
            if (isEnableWoc && !isEnableWop && isEnableLpm){
                mMtkNetworkNative.setPacketListenPortNative(mUdpPort, mNullPort, mNullPort);
            } else if(isEnableWoc && !isEnableWop && !isEnableLpm) {
                mMtkNetworkNative.setPacketListenPortNative(mWocUdpPort, mNullPort, mNullPort);
            } else if (!isEnableWoc && isEnableWop && isEnableLpm){
                mMtkNetworkNative.setPacketListenPortNative(mLpmUdpPort, mNullPort, mTcpRemotePort);
            } else if (!isEnableWoc && isEnableWop && !isEnableLpm){
                mMtkNetworkNative.setPacketListenPortNative(mNullPort, mNullPort, mTcpRemotePort);
            } else if (isEnableWoc && isEnableWop && isEnableLpm){
                mMtkNetworkNative.setPacketListenPortNative(mUdpPort, mNullPort, mTcpRemotePort);
            } else if (isEnableWoc && isEnableWop && !isEnableLpm) {
                mMtkNetworkNative.setPacketListenPortNative(mWocUdpPort, mNullPort, mTcpRemotePort);
            } else if (!isEnableWoc && !isEnableWop && isEnableLpm) {
                mMtkNetworkNative.setPacketListenPortNative(mLpmUdpPort, mNullPort, mNullPort);
            } else if (!isEnableWoc && !isEnableWop && !isEnableLpm) {
                mMtkNetworkNative.clearWoPacketListenPortNative();
            }

            Log.d(TAG, "mWopListenThread is null:  " + (mWopListenThread == null ? "yes": "no"));
            if(mWopListenThread == null)  {
                startWopMonitor();
            }

            if (!isRunnablePosted) {
                isRunnablePosted = true;
                periodicHandler.postDelayed(periodicRunnable, 20000);
            }

        } else {
            if (isRunnablePosted) {
                isRunnablePosted = false;
                periodicHandler.removeCallbacks(periodicRunnable);
            }
            mMtkNetworkNative.clearWoPacketListenPortNative();
        }
    }

    private Handler periodicHandler = new Handler();
    private Runnable periodicRunnable = new Runnable() {
    @Override
    public void run() {
        handleScreenOffAndActiveNetwork(context);
        periodicHandler.postDelayed(this, 60000);
    }
    };
}
