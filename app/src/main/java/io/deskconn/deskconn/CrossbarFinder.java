package io.deskconn.deskconn;

import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

class CrossbarFinder {

    private static final String TAG = CrossbarFinder.class.getName();

    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private Context mContext;

    CrossbarFinder(Context context) {
        mContext = context;
        mNsdManager = context.getSystemService(NsdManager.class);
        initialize();
    }

    void start() {
        mNsdManager.discoverServices("_crossbar._tcp", NsdManager.PROTOCOL_DNS_SD,
                mDiscoveryListener);
    }

    void stop() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }

    private void initialize() {
        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {

            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {

            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {

            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {

                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo info) {
                        Intent intent = new Intent("io.crossbar.found");
                        intent.putExtra("host", info.getHost().getHostName());
                        intent.putExtra("port", info.getPort());
                        intent.putExtra("name", info.getServiceName());
                        mContext.sendBroadcast(intent);
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
                Intent intent = new Intent("io.crossbar.lost");
                mContext.sendBroadcast(intent);
            }
        };
    }
}
