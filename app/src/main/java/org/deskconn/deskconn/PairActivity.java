package org.deskconn.deskconn;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.EditText;

import org.deskconn.deskconn.network.DeskConnConnector;
import org.libsodium.jni.keys.SigningKey;

import io.crossbar.autobahn.wamp.Session;

public class PairActivity extends AppCompatActivity {

    private Session mWAMPSession;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair);
        DeskConnConnector connector = DeskConnConnector.getInstance(this);
        connector.addOnConnectListener(this::onConnect);
        connector.addOnDisconnectListener(this::onDisconnect);
        connector.connect();

        EditText pairCodeBox = findViewById(R.id.edit_text_pair_code);
        Button pairButton = findViewById(R.id.button_pair);
        pairButton.setOnClickListener(view -> {
            String otp = pairCodeBox.getText().toString();
            if (connector.isConnected()) {
                SigningKey key = new SigningKey();
                String pubKey = key.getVerifyKey().toString();
                String privKey = key.toString();
                System.out.println("Calling....");
                mWAMPSession.call("org.deskconn.pairing.pair", otp, pubKey).whenComplete((callResult, throwable) -> {
                    if (throwable != null) {
                        throwable.printStackTrace();
                    } else {
                        Helpers helpers = new Helpers(getBaseContext());
                        helpers.savePublicKey(pubKey);
                        helpers.saveSecretKey(privKey);
                        helpers.setFirstRun(false);
                        connector.disconnect();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    }
                });
            }
        });
    }

    private void onConnect(Session session) {
        mWAMPSession = session;
    }

    private void onDisconnect() {
        mWAMPSession = null;
    }
}
