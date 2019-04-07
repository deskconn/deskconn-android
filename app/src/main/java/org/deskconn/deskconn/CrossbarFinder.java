package org.deskconn.deskconn;

import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.Map;

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
        mNsdManager.discoverServices("_deskconn._tcp", NsdManager.PROTOCOL_DNS_SD,
                mDiscoveryListener);
    }

    void stop() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }

    private void initialize() {
        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.d(TAG, "Service discovery start failed");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.d(TAG, "Service discovery stop failed");
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Service discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        System.out.println("Resolve failed...");
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo info) {
                        Intent intent = new Intent("io.crossbar.found");
                        String serverIP = info.getHost().getHostName();
                        intent.putExtra("host", serverIP);
                        intent.putExtra("port", info.getPort());
                        intent.putExtra("name", info.getServiceName());

                        Map<String, byte[]> attrs = info.getAttributes();
                        intent.putExtra("realm", getAttr(attrs, "realm", "deskconn"));
                        intent.putExtra("hostname", getAttr(attrs, "hostname", serverIP));
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

    private String getAttr(Map<String, byte[]> attrs, String key, String defValue) {
        return new String(attrs.getOrDefault(key, defValue.getBytes()));
    }
}
