package io.deskconn.deskconn;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.types.CallResult;

public class BrightnessFragment extends Fragment {

    private SeekBar mSeekBar;
    private TextView mStatusText;
    private Session mWAMPSession;
    private String mUUID;

    private CrossbarConnector mConnector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_brightness, container, false);
        getActivity().setTitle("Brightness Control");
        mSeekBar = view.findViewById(R.id.brightness_seekbar);
        mStatusText = view.findViewById(R.id.status_text);
        mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
        mSeekBar.setEnabled(false);
        mUUID = UUID.randomUUID().toString();

        mConnector = CrossbarConnector.getInstance();
        if (mConnector.isConnected()) {
            onServerConnectedListener(mConnector.getSession());
        } else {
            mConnector.addOnConnectListener(this::onServerConnectedListener);
        }
        mConnector.addOnConnectingListener(this::onServerConnectingListener);
        mConnector.addOnDisconnectedListener(this::onServerDisconnectedListener);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mConnector.removeOnConnectListener(this::onServerConnectedListener);
        mConnector.removeOnConnectingListener(this::onServerConnectingListener);
        mConnector.removeOnDisconnectedListener(this::onServerDisconnectedListener);
    }

    private void onServerConnectingListener() {
        mStatusText.setText("Connecting...");
    }

    private void onServerConnectedListener(Session session) {
        mWAMPSession = session;
        mStatusText.setText("Connected");
        CompletableFuture<Integer> currentBrightness = session.call(
                "io.crossbar.get_brightness", int.class);
        currentBrightness.thenAccept(brightness -> {
            mSeekBar.setProgress(brightness, false);
            mSeekBar.setEnabled(true);
        });
        session.subscribe("io.crossbar.brightness_changed", (args, kwargs, eDetails) -> {
            String publisher = (String) kwargs.get("publisher_id");
            if (publisher == null || !publisher.equals(mUUID)) {
                mSeekBar.setProgress((Integer) kwargs.get("percentage"));
            }
        });
    }

    private void onServerDisconnectedListener() {
        mWAMPSession = null;
        mStatusText.setText("Disconnected");
        mSeekBar.setEnabled(false);
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {
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
}
