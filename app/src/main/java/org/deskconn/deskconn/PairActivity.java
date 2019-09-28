package org.deskconn.deskconn;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.deskconn.deskconn.utils.DeskConn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PairActivity extends AppCompatActivity {

    private DeskConn mDeskConn;
    private List<DeskConn.Service> mServices;
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.peers_recyler);
        mRecyclerView = findViewById(R.id.peers_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mDeskConn = ((AppGlobals) getApplication()).getDeskConn();
        mServices = new ArrayList<>();
        discover();
    }

    private void discover() {
        mDeskConn.addOnServiceFoundListener(this::onServiceFound);
        mDeskConn.addOnServiceLostListener(this::onServiceLost);
        CompletableFuture<Boolean> future = mDeskConn.startDiscovery();
        future.thenAccept(started -> {
            mAdapter = new MyAdapter(mServices);
            mRecyclerView.setAdapter(mAdapter);
        });
        future.exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }

    private void onServiceFound(DeskConn.Service service) {
        mServices.add(service);
        mAdapter.notifyDataSetChanged();
    }

    private void onServiceLost(DeskConn.Service service) {
        mServices.remove(service);
        mAdapter.notifyDataSetChanged();
    }
}
