package com.om26er.brightnesscontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.types.CallResult;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();

    private SeekBar mSeekBar;
    private TextView mStatusText;
    private Session mWAMPSession;

    private String mUUID;

    private CrossbarFinder mFinder;
    private IntentFilter mFilter;

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
                connectToServer(extras.getString("host"), extras.getInt("port"));
            } else if (action.equals("io.crossbar.lost")) {
                System.out.println("Lost...");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSeekBar = findViewById(R.id.brightness_seekbar);
        mStatusText = findViewById(R.id.status_text);
        mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mSeekBar.setEnabled(false);
        mUUID = UUID.randomUUID().toString();

        mFilter = new IntentFilter();
        mFilter.addAction("io.crossbar.found");
        mFilter.addAction("io.crossbar.lost");
        mFinder = new CrossbarFinder(getApplicationContext());
    }

    private OnSeekBarChangeListener mSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }

            CompletableFuture<CallResult> call = mWAMPSession.call(
                    "io.crossbar.set_brightness", progress, mUUID);
            call.whenComplete((callResult, throwable) -> {
                if (throwable != null) {
                    throwable.printStackTrace();
                }
            });
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mCrossbarDiscovery, mFilter);
        mFinder.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mCrossbarDiscovery);
        mFinder.stop();
        disconnectFromServer();
    }

    private void connectToServer(String ip, int port) {
        mStatusText.setText("Connecting...");
        mWAMPSession = new Session();
        mWAMPSession.addOnJoinListener((session, details) -> {
            mStatusText.setText("Connected");
            CompletableFuture<Integer> currentBrightness = mWAMPSession.call(
                    "io.crossbar.get_brightness", int.class);
            currentBrightness.thenAccept(brightness -> {
                mSeekBar.setProgress(brightness, false);
                mSeekBar.setEnabled(true);
            });
            mWAMPSession.subscribe("io.crossbar.brightness_changed", (args, kwargs, eDetails) -> {
                String publisher = (String) kwargs.get("publisher_id");
                if (publisher == null || !publisher.equals(mUUID)) {
                    mSeekBar.setProgress((Integer) kwargs.get("percentage"));
                }
            });
        });
        mWAMPSession.addOnLeaveListener((session, details) -> {
            System.out.println(details);
        });
        Client wampClient = new Client(
                mWAMPSession, String.format("ws://%s:%d/ws", ip, port), "realm1");
        System.out.println("CONNECTING...");
        wampClient.connect().whenComplete((exitInfo, throwable) -> {
            if (throwable != null) {
                throwable.printStackTrace();
            } else {
                mStatusText.setText("Disconnected");
                mSeekBar.setEnabled(false);
            }
        });
    }

    private void disconnectFromServer() {
        if (mWAMPSession != null && mWAMPSession.isConnected()) {
            mWAMPSession.leave();
        }
    }
}
