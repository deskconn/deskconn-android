package org.deskconn.deskconn;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AppGlobals extends Application implements Application.ActivityLifecycleCallbacks {

    private DeskConn mDeskConn;
    private int mRunningCount;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        mDeskConn = new DeskConn(getApplicationContext());
    }

    public DeskConn getDeskConn() {
        return mDeskConn;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        mRunningCount++;
        if (mRunningCount == 1) {
            System.out.println("Should reconnect...");
//            mDeskConn.connect();
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        mRunningCount--;
        if (mRunningCount == 0) {
            mDeskConn.disconnect();
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }
}
