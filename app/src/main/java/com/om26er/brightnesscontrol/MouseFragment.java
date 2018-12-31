package com.om26er.brightnesscontrol;

import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;

public class MouseFragment extends Fragment {
    private View mBaseView;

    private View touchBoard;
    private long mDownTime;
    private float mDownX;
    private float mDownY;

    private long mUpTime;
    private float mUpX;
    private float mUpY;

    private float mLastMoveX = Float.MAX_VALUE;
    private float mLastMoveY = Float.MAX_VALUE;

    private float mCurMoveX;
    private float mCurMoveY;
    private long mLastMoveTime;

    private Session mWAMPSession;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBaseView = inflater.inflate(R.layout.fragment_mouse, container, false);
        getActivity().setTitle("Mouse");
        mWAMPSession = new Session();
        mWAMPSession.addOnJoinListener((session, details) -> System.out.println("HELLO"));
        Client client = new Client(mWAMPSession, "ws://192.168.100.6:5020/ws", "realm1");
        client.connect().whenComplete((exitInfo, throwable) -> {
        });
        init();
        return mBaseView;
    }

    private void init() {
        touchBoard = mBaseView.findViewById(R.id.touch_board);
        touchBoard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onScreenTouch(event);
                return true;
            }
        });
    }

    private void onScreenTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                System.out.println("down");
                onSingClick(event, true);
                break;
            case MotionEvent.ACTION_UP:
                System.out.println("up");
                onSingClick(event, false);
                break;
            case MotionEvent.ACTION_MOVE:
                System.out.println("Move");
                onMove(event);
                break;
        }
    }

    private void onMove(MotionEvent event) {
        float distanceX = 0;
        float distanceY = 0;

        mCurMoveX = event.getX();
        mCurMoveY = event.getY();

        if (mLastMoveX != Float.MAX_VALUE && mLastMoveY != Float.MAX_VALUE) {
            distanceX = mCurMoveX - mDownX;
            distanceY = mCurMoveY - mDownY;
        }

        float distance = (float) Math.sqrt(distanceX * distanceX + distanceY * distanceY);
        if (distance > 0) {
            Point point = new Point();
            getActivity().getWindowManager().getDefaultDisplay().getSize(point);
            float percentX = (distanceX / point.x) * 100;
            float percentY = (distanceY / point.y) * 100;
            mWAMPSession.call("io.crossbar.move_mouse", percentX * 3, percentY * 3);
            mDownX = event.getX();
            mDownY = event.getY();
        }
        mLastMoveX = mCurMoveX;
        mLastMoveY = mCurMoveY;
    }

    private void onSingClick(MotionEvent event, boolean down) {
        if (down) {
            mDownTime = System.currentTimeMillis();
            mDownX = event.getX();
            mDownY = event.getY();
        } else {
            mUpTime = System.currentTimeMillis();
            mUpX = event.getX();
            mUpY = event.getY();
        }
    }
}
