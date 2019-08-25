package org.deskconn.deskconn;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PairActivity extends AppCompatActivity {

    private DeskConn mDeskConn;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.peers_recyler);
        recyclerView = findViewById(R.id.peers_recycler_view);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mDeskConn = ((AppGlobals) getApplication()).getDeskConn();
        discover();
    }

    private void discover() {
        CompletableFuture<Map<String, DeskConn.Service>> future = mDeskConn.find(1000);
        future.thenAccept(servicesMap -> {
            List<DeskConn.Service> services = new ArrayList<>();
            for (String key : servicesMap.keySet()) {
                services.add(servicesMap.get(key));
            }
            mAdapter = new MyAdapter(services);
            recyclerView.setAdapter(mAdapter);
        });
        future.exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }
}
