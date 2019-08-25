package org.deskconn.deskconn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Handler;

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

    private final List<Consumer<Session>> mOnConnectListeners;
    private final List<Runnable> mOnDisconnectListeners;

    private Context mContext;
    private Session mWAMP;
    private JmDNS mJmDNS;
    private ExecutorService mExecutor;
    private String mWiFiIP;
    private Helpers mHelpers;

    public static class Service {
        public final String hostName;
        public final String hostIP;
        public final String realm;
        public final int port;

        public Service(String hostName, String hostIP, String realm, int port) {
            this.hostName = hostName;
            this.hostIP = hostIP;
            this.realm = realm;
            this.port = port;
        }
    }

    public DeskConn(Context context) {
        mContext = context;
        mOnConnectListeners = new ArrayList<>();
        mOnDisconnectListeners = new ArrayList<>();
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

    public void addOnConnectListener(Consumer<Session> method) {
        mOnConnectListeners.add(method);
        if (isConnected()) {
            method.accept(mWAMP);
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

    public CompletableFuture<Map<String, Service>> find(int timeout) {
        if (!isWiFiConnected()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        CompletableFuture<Map<String, Service>> future = new CompletableFuture<>();
        Map<String, Service> services = new HashMap<>();
        mExecutor.submit(() -> {
            try {
                mJmDNS = JmDNS.create(InetAddress.getByName(mWiFiIP));
                mJmDNS.addServiceListener("_deskconn._tcp.local.", new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        ServiceInfo info = event.getInfo();
                        String host = info.getInet4Addresses()[0].getHostAddress();
                        if (services.containsKey(host)) {
                            services.remove(host);
                        }
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        System.out.println("resolved...");
                        ServiceInfo info = event.getInfo();
                        String host = info.getInet4Addresses()[0].getHostAddress();
                        Service service = new Service(event.getName(), host,
                                info.getPropertyString("realm"), info.getPort());
                        services.put(host, service);
                    }
                });
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        new Handler().postDelayed(() -> future.complete(new HashMap<>(services)), timeout);
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
            mHelpers.savePublicKey(pubKey);
            mHelpers.saveSecretKey(privKey);
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
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.hostIP, service.port);
        Helpers helpers = new Helpers(mContext);
        Client client;
        if (helpers.isPaired()) {
            CryptosignAuth auth = new CryptosignAuth(helpers.getPublicKey(), helpers.getPrivateKey(),
                    helpers.getPublicKey());
            client = new Client(mWAMP, crossbarURL, service.realm, auth);
        } else {
            client = new Client(mWAMP, crossbarURL, service.realm);
        }
        client.connect().whenComplete((exitInfo, throwable) -> {
            mOnDisconnectListeners.forEach(Runnable::run);
        });
    }
}
