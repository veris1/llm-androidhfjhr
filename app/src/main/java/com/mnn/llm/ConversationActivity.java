package com.mnn.llm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mnn.llm.recylcerchat.ChatData;
import com.mnn.llm.recylcerchat.ConversationRecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConversationActivity extends BaseActivity {

    private RecyclerView mRecyclerView;
    private ConversationRecyclerView mAdapter;
    private DateFormat mDateFormat;
    private Chat mChat;
    private GrpcClient grpcClient;
    private String signature;
    private String workerId;
    private String grpcServerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        mChat = (Chat) getIntent().getSerializableExtra("chat");
        signature = getIntent().getStringExtra("signature");
        workerId = getIntent().getStringExtra("workerId");
        grpcServerAddress = getIntent().getStringExtra("grpcUrl");

        Log.d("ConversationActivity", "Signature: " + signature);
        Log.d("ConversationActivity", "Worker ID: " + workerId);
        Log.d("ConversationActivity", "gRPC Server Address: " + grpcServerAddress);

        if (signature == null || workerId == null) {
            throw new IllegalArgumentException("Signature or Worker ID is missing!");
        }

        mDateFormat = new SimpleDateFormat("hh:mm aa");
        setupToolbarWithUpNav(R.id.toolbar, "mnn-llm", R.drawable.ic_action_back);

        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ConversationRecyclerView(this, initData());
        mRecyclerView.setAdapter(mAdapter);

        try {
            grpcClient = new GrpcClient(this, grpcServerAddress); // Replace with your gRPC server address and port
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        grpcClient.streamTaskRequests(signature, workerId, mChat, new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                logMessage(msg.obj.toString());
            }
        });
    }

    public List<ChatData> initData() {
        List<ChatData> data = new ArrayList<>();
        // set head time: year-month-day
        ChatData head = new ChatData();
        DateFormat headFormat = new SimpleDateFormat("yyyy-MM-dd");
        String headDate = headFormat.format(new Date());
        head.setTime("");
        head.setText(headDate);
        head.setType("0");
        data.add(head);
        // set initial log entry
        ChatData item = new ChatData();
        String itemDate = mDateFormat.format(new Date());
        item.setType("1");
        item.setTime(itemDate);
        item.setText("Log started.");
        data.add(item);

        return data;
    }

    private void logMessage(String message) {
        ChatData logEntry = new ChatData();
        logEntry.setTime(mDateFormat.format(new Date()));
        logEntry.setType("1");
        logEntry.setText(message);
        mAdapter.addItem(logEntry);
        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount() - 1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_userphoto, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Toast.makeText(getBaseContext(), "Clear Memory", Toast.LENGTH_SHORT).show();
        mChat.Reset();
        return true;
    }
}
