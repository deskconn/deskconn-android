package org.deskconn.deskconn.network;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.nsd.NsdManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;

public class DeskConnConnector {

    @SuppressLint("StaticFieldLeak")
    private static volatile DeskConnConnector sInstance;

    private Context mContext;
    private Session mWAMPSession;

    private final List<Consumer<Session>> mOnConnectListeners;
    private final List<Runnable> mOnDisconnectListeners;

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

    public void connect() {
        DeskConnFinder.start(mContext.getSystemService(NsdManager.class), this::connectToServer,
                () -> {});
    }

    public void disconnect() {
        DeskConnFinder.stop();
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
            mOnConnectListeners.forEach(sessionConsumer -> sessionConsumer.accept(session));
        });
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.getHost(),
                service.getPort());
        Client wampClient = new Client(mWAMPSession, crossbarURL, service.getRealm());
        wampClient.connect().whenComplete((exitInfo, throwable) -> {
            mOnDisconnectListeners.forEach(Runnable::run);
        });
    }
}
