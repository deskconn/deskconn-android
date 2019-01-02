package io.deskconn.deskconn;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import io.crossbar.autobahn.wamp.Session;

public class MouseFragment extends Fragment {

    private View mBaseView;

    private float mDownX;
    private float mDownY;

    private Session mWAMPSession;
    private Point mDisplaySize;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBaseView = inflater.inflate(R.layout.fragment_mouse, container, false);
        getActivity().setTitle("Mouse Control");
        mDisplaySize = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(mDisplaySize);
        CrossbarConnector connector = CrossbarConnector.getInstance();
        if (connector.isConnected()) {
            mWAMPSession = connector.getSession();
        }
        connector.addOnConnectListener(session -> mWAMPSession = session);
        init();
        return mBaseView;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        View touchBoard = mBaseView.findViewById(R.id.touch_board);
        touchBoard.setOnTouchListener((v, event) -> onScreenTouch(event));
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
        float cursorMoveX = event.getX();
        float cursorMoveY = event.getY();

        float percentX = ((cursorMoveX - mDownX) / mDisplaySize.x) * 100;
        float percentY = ((cursorMoveY - mDownY) / mDisplaySize.y) * 100;
        mWAMPSession.call("io.crossbar.move_mouse", percentX * 2, percentY * 4);

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
