

package com.mediatek.network;


import android.content.Context;
import android.util.Log;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.Network;
import java.net.InetAddress;
import java.io.FileOutputStream;
import java.io.File;
import java.io.*;
import android.os.Handler;
import android.os.Looper;
import java.util.Collection;
import android.net.NetworkInfo;

import com.mediatek.net.MtkNetworkManager;
import com.mediatek.twoworlds.tv.MtkTvHBBTV;
import com.mediatek.twoworlds.tv.MtkTvHBBTVBase;
import vendor.mediatek.hardware.tv.networkproxy.V1_0.INetworkProxy;

import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.net.Proxy;
import android.net.ProxyInfo;
import com.mediatek.twoworlds.tv.MtkTvConfig;

public final class MtkNetworkMonitorService extends Service {

    private static final String TAG = "MtkNetworkMonitorService";
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private native boolean wlan0UpCmd();
    private native boolean wlan0DownCmd();

    private Handler mHandler;
    private LinkProperties mLastedLinkProperties;
    private int isConnected = 0;
    private int mIsCaptivePortal = 0;

    private IntentFilter mProxyIntentFilter;
    private BroadcastReceiver mProxyReceiver;
    private MtkTvConfig mTvConfig;

    private final NetworkCallback mNetworkCallback = new NetworkCallback(){
        @Override
        public void onLost(Network network){
            Log.i(TAG, "onLost");
            isConnected = 0;
            mIsCaptivePortal = 0;
            int data[] = { isConnected };
            MtkTvHBBTV.getInstance().exchangeData(MtkTvHBBTVBase.HbbtvCmdType.MTKTVAPI_HBBTV_SET_NETWORK_STATE.ordinal(),data);
        }

        @Override
        public void onAvailable(Network network){
            Log.i(TAG, "onAvailable");
            isConnected = 1;
            final Network defaultNetwork = mConnectivityManager.getActiveNetwork();
            if (defaultNetwork == null) {
               Log.d(TAG, "network is not connected");
               return;
            }else{
               mLastedLinkProperties = mConnectivityManager.getLinkProperties(defaultNetwork);
            }
            if(mLastedLinkProperties != null){
                if(mLastedLinkProperties.getDhcpServerAddress() != null){
                    String dhcpserver = mLastedLinkProperties.getDhcpServerAddress().getHostAddress();
                    if(dhcpserver != null){
                        try{
                            if(INetworkProxy.getService()!=null){
                                INetworkProxy.getService().setNetworkProperty("dhcpserver",dhcpserver);
                            }
                        }catch (RemoteException ex){
                            Log.d(TAG, "failed setNetworkProperty",ex);
                        }
                    }
                }
                setDefaultDnsSystemProperties(mLastedLinkProperties.getDnsServers());
            }

            int data[] = { isConnected };
            MtkTvHBBTV.getInstance().exchangeData(MtkTvHBBTVBase.HbbtvCmdType.MTKTVAPI_HBBTV_SET_NETWORK_STATE.ordinal(),data);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.i(TAG, "onLinkPropertiesChanged");
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities mNetworkCapabilities) {
            Log.i(TAG, "onCapabilitiesChanged");
            if(mNetworkCapabilities != null && mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)){
              mIsCaptivePortal = 1;
              int data2[] = { mIsCaptivePortal };
              MtkTvHBBTV.getInstance().exchangeData(MtkTvHBBTVBase.HbbtvCmdType.MTKTVAPI_HBBTV_SET_NETWORK_CONDITION_STATE.ordinal(),data2);
            }
        }
    };

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mWifiManager = ((WifiManager) getSystemService(Context.WIFI_SERVICE));
        mConnectivityManager = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE));
        mHandler = new Handler(Looper.getMainLooper());
        mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback,mHandler);
        mTvConfig = MtkTvConfig.getInstance();
        mProxyIntentFilter = new IntentFilter();
        mProxyIntentFilter.addAction(Proxy.PROXY_CHANGE_ACTION);

        mProxyReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context,Intent intent){
                Log.d(TAG, "received proxy setting changed");
                ProxyInfo proxy = intent.getParcelableExtra(Proxy.EXTRA_PROXY_INFO);
                if (proxy != null && 
                    !(proxy.getHost().length()==0 && proxy.getPort()==0)) {
                    Log.d(TAG, "Proxy setting is " + proxy.toString());
                    Log.d(TAG, "Proxy hostlen:" + proxy.getHost().length() 
                        + " port:" + proxy.getPort());
                    mTvConfig.setNetworkProxy(true, proxy.getHost(), proxy.getPort());
                }
                else {
                    Log.d(TAG, "Clear Proxy setting");
                    mTvConfig.setNetworkProxy(false, "", 0);
                }
            }
        };
        registerReceiver(mProxyReceiver,mProxyIntentFilter);
    }

    private void setDefaultDnsSystemProperties(Collection<InetAddress> dnses) {
        int last = 0;
        String value1 = "0";
        String value2 = "0";
        String value3 = "0";
        for (InetAddress dns : dnses) {
            ++last;
            if(last == 1){
                 value1 = dns.getHostAddress();
                 Log.d(TAG, "DNS1 is " + value1);
            }else if(last == 2){
                 value2 = dns.getHostAddress();
                 Log.d(TAG, "DNS2 is " + value2);
            }else if(last == 3){
                 value3 = dns.getHostAddress();
                 Log.d(TAG, "DNS3 is " + value3);
            }else{
                 Log.d(TAG, "DNS counter>3,ignore...");
            }
        }
        try{
            if(INetworkProxy.getService()!=null){
               INetworkProxy.getService().setDnsForLinux(value1,value2,value3);
            }
        }catch (RemoteException ex){
            Log.d(TAG, "failed setDnsForLinux",ex);
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
        boolean enable = MtkNetworkManager.getInstance().isEnanbleWoWL();
        Log.d(TAG, "isEnanbleWoWL: " + enable);
        MtkNetworkManager.getInstance().setEnableWoWL(enable);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        unregisterReceiver(mProxyReceiver);
    }
}



