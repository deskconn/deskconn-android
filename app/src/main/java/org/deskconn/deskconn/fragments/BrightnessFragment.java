package org.deskconn.deskconn.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.deskconn.deskconn.AppGlobals;
import org.deskconn.deskconn.R;
import org.deskconn.deskconn.utils.DeskConn;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.types.CallResult;

public class BrightnessFragment extends Fragment {

    private SeekBar mSeekBar;
    private TextView mStatusText;
    private Session mWAMPSession;
    private String mUUID;

    private DeskConn mConnector;

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

        mStatusText.setText("Connecting...");
        mConnector = ((AppGlobals) getActivity().getApplication()).getDeskConn();
        mConnector.addOnConnectListener(this::onConnect);
        mConnector.addOnDisconnectListener(this::onDisconnect);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mConnector.removeOnConnectListener(this::onConnect);
        mConnector.removeOnDisconnectListener(this::onDisconnect);
    }

    private void onConnect(Session session) {
        mWAMPSession = session;
        mStatusText.setText("Connected");
        CompletableFuture<Integer> currentBrightness = session.call(
                "org.deskconn.brightness.get", int.class);
        currentBrightness.thenAccept(brightness -> {
            mSeekBar.setProgress(brightness, false);
            mSeekBar.setEnabled(true);
        });
        session.subscribe("org.deskconn.brightness.on_changed", (args, kwargs, eDetails) -> {
            String publisher = (String) kwargs.get("publisher_id");
            if (publisher == null || !publisher.equals(mUUID)) {
                mSeekBar.setProgress((Integer) kwargs.get("percentage"));
            }
        });
    }

    private void onDisconnect() {
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
                    "org.deskconn.brightness.set", progress, mUUID);
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
