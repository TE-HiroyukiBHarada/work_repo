package com.mediatek.network;

import android.content.BroadcastReceiver;
import android.util.Log;
import android.content.Intent;
import android.content.Context;

public class MtkNetworkMonitorReceiver extends BroadcastReceiver{
    
    private static final String TAG = "MtkNetworkMonitorReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "onReceived " + intent.getAction());
        Intent serviceIntent= new Intent(context, MtkNetworkMonitorService.class);
        context.startService(serviceIntent);
    }
}
