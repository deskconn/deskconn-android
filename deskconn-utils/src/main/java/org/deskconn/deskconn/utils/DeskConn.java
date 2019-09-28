package org.deskconn.deskconn.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;

import org.libsodium.jni.keys.SigningKey;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.auth.CryptosignAuth;
import io.crossbar.autobahn.wamp.types.CallResult;

public class DeskConn {

    private final Map<String, Service> mServicesArchive;
    private final List<Consumer<Session>> mOnConnectListeners;
    private final List<Runnable> mOnDisconnectListeners;
    private final List<Consumer<Service>> mOnServiceFoundListeners;
    private final List<Consumer<Service>> mOnServiceLostListeners;

    private Context mContext;
    private Session mWAMP;
    private JmDNS mJmDNS;
    private ExecutorService mExecutor;
    private String mWiFiIP;
    private Helpers mHelpers;

    public static class Service {
        private final String mHostName;
        private final String mHostIP;
        private final String mRealm;
        private final int mPort;

        Service(String hostName, String hostIP, String realm, int port) {
            this.mHostName = hostName;
            this.mHostIP = hostIP;
            this.mRealm = realm;
            this.mPort = port;
        }

        public String getHostName() {
            return mHostName;
        }

        public String getHostIP() {
            return mHostIP;
        }

        public String getRealm() {
            return mRealm;
        }

        public int getPort() {
            return mPort;
        }
    }

    public DeskConn(Context context) {
        mContext = context;
        mServicesArchive = new HashMap<>();
        mOnConnectListeners = new ArrayList<>();
        mOnDisconnectListeners = new ArrayList<>();
        mOnServiceFoundListeners = new ArrayList<>();
        mOnServiceLostListeners = new ArrayList<>();
        mExecutor = Executors.newSingleThreadExecutor();
        mHelpers = new Helpers(mContext);
        trackWiFiState();
    }

    private void trackWiFiState() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        cm.requestNetwork(builder.build(), new NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                mWiFiIP = getNetworkIP(network);
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                mWiFiIP = null;
            }
        });
    }

    public boolean isWiFiConnected() {
        return mWiFiIP != null;
    }

    public void addOnConnectListener(Consumer<Session> callback) {
        mOnConnectListeners.add(callback);
        if (isConnected()) {
            callback.accept(mWAMP);
        }
    }

    public void removeOnConnectListener(Consumer<Session> method) {
        mOnConnectListeners.remove(method);
    }

    public void addOnDisconnectListener(Runnable method) {
        mOnDisconnectListeners.add(method);
    }

    public void removeOnDisconnectListener(Runnable method) {
        mOnDisconnectListeners.remove(method);
    }

    public void addOnServiceFoundListener(Consumer<Service> callback) {
        mOnServiceFoundListeners.add(callback);
    }

    public void removeOnServiceFoundListner(Consumer<Service> callback) {
        mOnServiceFoundListeners.remove(callback);
    }

    public void addOnServiceLostListener(Consumer<Service> callback) {
        mOnServiceLostListeners.add(callback);
    }

    public void removeOnServiceLostListener(Consumer<Service> callback) {
        mOnServiceLostListeners.remove(callback);
    }

    public CompletableFuture<Boolean> startDiscovery() {
        if (!isWiFiConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mExecutor.submit(() -> {
            try {
                List<ServiceEvent> events = new ArrayList<>();
                mJmDNS = JmDNS.create(InetAddress.getByName(mWiFiIP));
                mJmDNS.addServiceListener("_deskconn._tcp.local.", new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        System.out.println(events.contains(event));
                        ServiceInfo info = event.getInfo();
                        String host = info.getInet4Addresses()[0].getHostAddress();
                        System.out.println(mServicesArchive.containsKey(host));
                        if (mServicesArchive.containsKey(host)) {
                            Service service = mServicesArchive.remove(host);
                            for (Consumer<Service> consumer: mOnServiceLostListeners) {
                                consumer.accept(service);
                            }
                        }
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        System.out.println("resolved...");
                        ServiceInfo info = event.getInfo();
                        String host = info.getInet4Addresses()[0].getHostAddress();
                        System.out.println(host);
                        Service service = new Service(event.getName(), host,
                                info.getPropertyString("mRealm"), info.getPort());
                        mServicesArchive.put(host, service);
                        for (Consumer<Service> consumer: mOnServiceFoundListeners) {
                            consumer.accept(service);
                        }
                    }
                });
                future.complete(true);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private String getNetworkIP(Network network) {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        List<LinkAddress> addresses = cm.getLinkProperties(network).getLinkAddresses();
        for (LinkAddress linkAddress: addresses) {
            if (linkAddress.getAddress() instanceof Inet4Address) {
                return linkAddress.getAddress().getHostName();
            }
        }
        return null;
    }

    public boolean isWiFiEnabled() {
        WifiManager wifimanager = mContext.getSystemService(WifiManager.class);
        if (wifimanager == null) {
            return false;
        }
        return wifimanager.isWifiEnabled();
    }

    public boolean isPaired() {
        return mHelpers.isPaired();
    }

    public synchronized void disconnect() {
        if (isConnected()) {
            mWAMP.leave();
        }
    }

    public boolean isConnected() {
        return mWAMP != null && mWAMP.isConnected();
    }

    public CompletableFuture<Void> pair(String otp) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SigningKey key = new SigningKey();
        String pubKey = key.getVerifyKey().toString();
        String privKey = key.toString();
        CompletableFuture<CallResult> call = mWAMP.call("org.deskconn.pairing.pair", otp, pubKey);
        call.thenAccept(callResult -> {
            mHelpers.saveKeys(pubKey, privKey);
            mHelpers.setPaired(true);
            future.complete(null);
        });
        call.exceptionally(throwable -> {
            future.completeExceptionally(throwable);
            return null;
        });
        return future;
    }

    public void connect(Service service) {
        mWAMP = new Session();
        mWAMP.addOnJoinListener((session, details) -> {
            mOnConnectListeners.forEach(sessionConsumer -> sessionConsumer.accept(session));
        });
        mWAMP.addOnLeaveListener((session, details) -> {
            System.out.println("Left...");
            System.out.println(details.reason);
        });
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.mHostIP, service.mPort);
        Helpers helpers = new Helpers(mContext);
        Client client;
        if (helpers.isPaired()) {
            Helpers.KeyPair keyPair = mHelpers.getKeyPair();
            CryptosignAuth auth = new CryptosignAuth(keyPair.getPublicKey(),
                    keyPair.getPrivateKey(), keyPair.getPublicKey());
            client = new Client(mWAMP, crossbarURL, service.mRealm, auth);
        } else {
            client = new Client(mWAMP, crossbarURL, service.mRealm);
        }
        client.connect().whenComplete((exitInfo, throwable) -> {
            mOnDisconnectListeners.forEach(Runnable::run);
        });
    }
}
