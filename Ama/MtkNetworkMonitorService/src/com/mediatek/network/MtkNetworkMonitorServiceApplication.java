package com.mediatek.network;
import android.app.Application;
import android.content.Intent;
import android.util.Log;
public class MtkNetworkMonitorServiceApplication extends Application {
    private static final String TAG = "MtkNetworkMonitorServiceApplication";
    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");
        Intent serviceIntent= new Intent(this, MtkNetworkMonitorService.class);
        this.startService(serviceIntent);
        Log.v(TAG, "onCreate end");
    }
}
