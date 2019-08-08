package org.deskconn.deskconn.network;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Map;

import static android.net.nsd.NsdManager.PROTOCOL_DNS_SD;

class DeskConnFinder {

    private static final String TAG = DeskConnFinder.class.getName();

    private static NsdManager sNsdManager;
    private static NsdManager.DiscoveryListener mDiscoveryListener;

    interface OnServiceFoundListener {
        void onFound(DiscoveredService service);
    }

    interface OnServiceSearchFailureListener {
        void onFailure();
    }

    static void start(@NonNull NsdManager nsdManager, @NonNull OnServiceFoundListener foundListener,
               @NonNull OnServiceSearchFailureListener failureListener) {
        sNsdManager = nsdManager;
        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.d(TAG, "Service discovery start failed");
                failureListener.onFailure();
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
                sNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        failureListener.onFailure();
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo info) {
                        DiscoveredService service = new DiscoveredService(
                                info.getHost().getHostName(),
                                info.getPort(),
                                info.getServiceName(),
                                getAttr(info.getAttributes(), "realm", "deskconn")
                        );
                        foundListener.onFound(service);
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
                failureListener.onFailure();
            }
        };
        sNsdManager.discoverServices("_deskconn._tcp", PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    static void stop() {
        if (sNsdManager != null) {
            sNsdManager.stopServiceDiscovery(mDiscoveryListener);
            sNsdManager = null;
        }
    }

    private static String getAttr(Map<String, byte[]> attrs, String key, String defValue) {
        return new String(attrs.getOrDefault(key, defValue.getBytes()));
    }
}
