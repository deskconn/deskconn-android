package org.deskconn.deskconn.fragments;

import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.deskconn.deskconn.network.DeskConnConnector;
import org.deskconn.deskconn.R;

import io.crossbar.autobahn.wamp.Session;

public class MouseFragment extends Fragment {
        private float mDownX;
    private float mDownY;

    private Session mWAMPSession;
    private Point mDisplaySize;
    private View mTouchBoard;
    private DeskConnConnector mConnector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View baseView = inflater.inflate(R.layout.fragment_mouse, container, false);
        mTouchBoard = baseView.findViewById(R.id.touch_board);
        getActivity().setTitle("Mouse Control");
        mDisplaySize = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(mDisplaySize);
        mConnector = DeskConnConnector.getInstance(getActivity());
        mConnector.addOnConnectListener(this::OnConnect);
        mConnector.addOnDisconnectListener(this::onDisconnect);
        return baseView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mConnector.removeOnConnectListener(this::OnConnect);
        mConnector.removeOnDisconnectListener(this::onDisconnect);
    }

    private void OnConnect(Session session) {
        mWAMPSession = session;
        mTouchBoard.setOnTouchListener((v, event) -> onScreenTouch(event));
    }

    private void onDisconnect() {
        mTouchBoard.setOnTouchListener((v, event) -> false);
    }

    private boolean onScreenTouch(MotionEvent event) {
        int ev = event.getAction();
        if (ev == MotionEvent.ACTION_DOWN) {
            onSingClick(event, true);
        } else if (ev == MotionEvent.ACTION_UP) {
            onSingClick(event, false);
        } else if (ev == MotionEvent.ACTION_MOVE) {
            onMove(event);
        }
        return true;
    }

    private void onMove(MotionEvent event) {
        System.out.println("Moving...");
        float cursorMoveX = event.getX();
        float cursorMoveY = event.getY();

        float percentX = ((cursorMoveX - mDownX) / mDisplaySize.x) * 100;
        float percentY = ((cursorMoveY - mDownY) / mDisplaySize.y) * 100;
        mWAMPSession.call("org.deskconn.mouse.move", percentX * 2, percentY * 4);

        mDownX = cursorMoveX;
        mDownY = cursorMoveY;
    }

    private void onSingClick(MotionEvent event, boolean down) {
        if (down) {
            // Save the coordinates as soon as the finger touches
            // the screen
            mDownX = event.getX();
            mDownY = event.getY();
        }
    }
}
