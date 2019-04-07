package org.deskconn.deskconn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.function.Consumer;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;

class CrossbarConnector {

    @SuppressLint("StaticFieldLeak")
    private static CrossbarConnector sInstance;

    private Activity mActivity;

    private Session mWAMPSession;
    private CrossbarFinder mFinder;
    private IntentFilter mFilter;

    private ArrayList<Consumer<Session>> mOnConnectListeners = new ArrayList<>();
    private ArrayList<Runnable> mOnConnectingListeners = new ArrayList<>();
    private ArrayList<Runnable> mOnDisconnectedListeners = new ArrayList<>();

    static CrossbarConnector getInstance(Activity activity) {
        if (sInstance == null) {
            sInstance = new CrossbarConnector(activity);
        }
        return sInstance;
    }

    static CrossbarConnector getInstance() {
        return sInstance;
    }

    Session getSession() {
        return mWAMPSession;
    }

    private CrossbarConnector(Activity activity) {
        mActivity = activity;
        mFilter = new IntentFilter();
        mFilter.addAction("io.crossbar.found");
        mFilter.addAction("io.crossbar.lost");
        mFinder = new CrossbarFinder(activity.getApplicationContext());
    }

    void addOnConnectingListener(Runnable method) {
        mOnConnectingListeners.add(method);
    }

    void removeOnConnectingListener(Runnable method) {
        mOnConnectingListeners.remove(method);
    }

    void addOnConnectListener(Consumer<Session> method) {
        mOnConnectListeners.add(method);
    }

    void removeOnConnectListener(Consumer<Session> method) {
        mOnConnectListeners.remove(method);
    }

    void addOnDisconnectedListener(Runnable method) {
        mOnDisconnectedListeners.add(method);
    }

    void removeOnDisconnectedListener(Runnable method) {
        mOnDisconnectedListeners.remove(method);
    }

    void connect() {
        mActivity.registerReceiver(mCrossbarDiscovery, mFilter);
        mFinder.start();
    }

    void disconnect() {
        mActivity.unregisterReceiver(mCrossbarDiscovery);
        mFinder.stop();
        disconnectFromServer();
    }

    private BroadcastReceiver mCrossbarDiscovery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            Bundle extras = intent.getExtras();
            if (extras != null && action.equals("io.crossbar.found")) {
                System.out.println("Found: " + extras.getString("host"));
                System.out.println("Deskconn host is: " + extras.getString("hostname"));
                connectToServer(extras.getString("host"), extras.getInt("port"),
                        extras.getString("realm"));
            } else if (action.equals("io.crossbar.lost")) {
                System.out.println("Lost...");
            }
        }
    };

    boolean isConnected() {
        return mWAMPSession != null && mWAMPSession.isConnected();
    }

    private void disconnectFromServer() {
        if (isConnected()) {
            mWAMPSession.leave();
        }
        mWAMPSession = null;
    }

    private void connectToServer(String ip, int port, String realm) {
        mOnConnectingListeners.forEach(Runnable::run);
        mWAMPSession = new Session();
        mWAMPSession.addOnJoinListener((session, details) -> {
            mOnConnectListeners.forEach(sessionConsumer -> sessionConsumer.accept(session));
        });
        String crossbarURL = String.format("ws://%s:%d/ws", ip, port);
        Client wampClient = new Client(mWAMPSession, crossbarURL, realm);
        wampClient.connect().whenComplete((exitInfo, throwable) -> {
            mOnDisconnectedListeners.forEach(Runnable::run);
        });
    }
}
