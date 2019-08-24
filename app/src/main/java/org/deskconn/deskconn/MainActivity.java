package org.deskconn.deskconn;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.navigation.NavigationView;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.MenuItem;
import android.view.WindowManager;

import org.deskconn.deskconn.fragments.BrightnessFragment;
import org.deskconn.deskconn.fragments.MouseFragment;
import org.deskconn.deskconn.network.DeskConnConnector;
import org.libsodium.jni.keys.SigningKey;

import io.crossbar.autobahn.wamp.Session;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DeskConnConnector mConnector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.getMenu().getItem(0).setChecked(true);
        navigationView.setNavigationItemSelectedListener(this);

        mConnector = DeskConnConnector.getInstance(this);
        Helpers helpers = new Helpers(getApplicationContext());
        if (helpers.isFirstRun()) {
            new IntentIntegrator(this).initiateScan();
        } else {
            loadFragment(new BrightnessFragment());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            mConnector.addOnConnectListener(session -> {
                System.out.println(result.getContents());
                sendPairRequest(session, result.getContents().trim());
            });
        } else {
            // Failure...
        }
    }

    private void sendPairRequest(Session session, String otp) {
        SigningKey key = new SigningKey();
        String pubKey = key.getVerifyKey().toString();
        String privKey = key.toString();
        session.call("org.deskconn.pairing.pair", otp, pubKey).whenComplete((callResult, throwable) -> {
            if (throwable == null) {
                Helpers helpers = new Helpers(getBaseContext());
                helpers.savePublicKey(pubKey);
                helpers.saveSecretKey(privKey);
                helpers.setFirstRun(false);
                mConnector.disconnect();
                loadFragment(new BrightnessFragment());
            } else {
                throwable.printStackTrace();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mConnector.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mConnector.disconnect();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_brightness) {
            loadFragment(new BrightnessFragment());
        } else if (id == R.id.nav_mouse) {
            loadFragment(new MouseFragment());
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.container, fragment);
        tx.commit();
    }
}
