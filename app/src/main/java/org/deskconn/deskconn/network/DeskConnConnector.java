package org.deskconn.deskconn.network;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.deskconn.deskconn.Helpers;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.auth.CryptosignAuth;

public class DeskConnConnector {

    private static volatile DeskConnConnector sInstance = null;

    private final List<Consumer<Session>> mOnConnectListeners;
    private final List<Runnable> mOnDisconnectListeners;

    private Context mContext;
    private Session mWAMPSession;
    private JmDNS mJmDNS;

    public synchronized static DeskConnConnector getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DeskConnConnector(context);
        }
        return sInstance;
    }

    private DeskConnConnector(Context context) {
        if (sInstance != null) {
            throw new RuntimeException("Direct initialization not allowed, call getInstance()");
        }
        mContext = context;
        mOnConnectListeners = new ArrayList<>();
        mOnDisconnectListeners = new ArrayList<>();
    }

    public void addOnConnectListener(Consumer<Session> method) {
        mOnConnectListeners.add(method);
        if (isConnected()) {
            method.accept(mWAMPSession);
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

    public synchronized void connect() {
        new Thread(() -> {
            try {
                mJmDNS = JmDNS.create(getWifiIP());
                mJmDNS.addServiceListener("_deskconn._tcp.local.", new ServiceListener());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private InetAddress getWifiIP() throws UnknownHostException {
        WifiManager wifiMgr = mContext.getSystemService(WifiManager.class);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        byte[] ipBytes = BigInteger.valueOf(wifiInfo.getIpAddress()).toByteArray();
        return InetAddress.getByAddress(ipBytes);
    }

    public synchronized void disconnect() {
        if (mJmDNS != null) {
            mJmDNS.unregisterAllServices();
        }
        if (isConnected()) {
            mWAMPSession.leave();
        }
        mWAMPSession = null;
    }

    public boolean isConnected() {
        return mWAMPSession != null && mWAMPSession.isConnected();
    }

    private void connectToServer(DiscoveredService service) {
        mWAMPSession = new Session();
        mWAMPSession.addOnJoinListener((session, details) -> {
            System.out.println(details.authrole);
            mOnConnectListeners.forEach(sessionConsumer -> sessionConsumer.accept(session));
        });
        mWAMPSession.addOnLeaveListener((session, details) -> {
            System.out.println("Left...");
            System.out.println(details.reason);
        });
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.getHost(),
                service.getPort());
        Client wampClient;
        Helpers helpers = new Helpers(mContext);
        if (helpers.isFirstRun()) {
            wampClient = new Client(mWAMPSession, crossbarURL, service.getRealm());
        } else {
            CryptosignAuth auth = new CryptosignAuth(
                    helpers.getPublicKey(), helpers.getPrivateKey(), helpers.getPublicKey());
            wampClient = new Client(mWAMPSession, crossbarURL, service.getRealm(), auth);
        }
        wampClient.connect().whenComplete((exitInfo, throwable) -> {
            mOnDisconnectListeners.forEach(Runnable::run);
        });
    }

    private class ServiceListener implements javax.jmdns.ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("Service added...");
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed: " + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            String host = info.getInet4Addresses()[0].getHostAddress();
            DiscoveredService service = new DiscoveredService(host, info.getPort(), event.getName(),
                    info.getPropertyString("realm"));
            connectToServer(service);
        }
    }
}
