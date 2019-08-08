package org.deskconn.deskconn;


import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class Helpers extends ContextWrapper {

    private static final String KEY_FIRST_RUN = "key_first_run";
    private static final String KEY_PUBLIC = "key_public";
    private static final String KEY_SECRET = "key_secret";

    private SharedPreferences mPrefs;

    public Helpers(Context base) {
        super(base);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public boolean isFirstRun() {
        return mPrefs.getBoolean(KEY_FIRST_RUN, true);
    }

    public void setFirstRun(boolean isFirstRun) {
        mPrefs.edit().putBoolean(KEY_FIRST_RUN, isFirstRun).apply();
    }

    public String getPublicKey() {
        return mPrefs.getString(KEY_PUBLIC, null);
    }

    public void savePublicKey(String key) {
        mPrefs.edit().putString(KEY_PUBLIC, key).apply();
    }

    public String getPrivateKey() {
        return mPrefs.getString(KEY_SECRET, null);
    }

    public void saveSecretKey(String key) {
        mPrefs.edit().putString(KEY_SECRET, key).apply();
    }
}
