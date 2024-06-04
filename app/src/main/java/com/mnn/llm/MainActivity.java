package com.mnn.llm;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Chat mChat;
    private Intent mIntent;
    private Button mLoadButton;
    private TextView mModelInfo;
    private RelativeLayout mProcessView;
    private Handler mProcessHandler;
    private ProgressBar mProcessBar;
    private TextView mProcessPercent;
    private Spinner mSpinnerModels;
    private TextView mDownloadStatus;
    private TextView mSignatureStatus;
    private TextView mSignatureValue;
    private TextView mRegistrationStatus;
    private TextView mKeepAliveCountdown;
    private RegistrationManager registrationManager;

    private String mModelName;
    private boolean isModelDownloaded = false; // Flag to track download status

    private static final int REQUEST_PERMISSIONS = 123;

    private Handler downloadHandler;
    private int filesToDownload;
    private int filesDownloaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permissions
        requestPermissions();

        // Initialize UI components
        initializeUIComponents();

        // Set up QR code button click listener
        findViewById(R.id.scan_qr_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanQRCode();
            }
        });

        // Check for stored signature and call API if exists
        checkStoredSignatureAndRegister();
    }

    private void initializeUIComponents() {
        mIntent = new Intent(this, ConversationActivity.class);
        mModelInfo = findViewById(R.id.model_info);
        mLoadButton = findViewById(R.id.load_button);
        mProcessView = findViewById(R.id.process_view);
        mProcessBar = findViewById(R.id.process_bar);
        mProcessPercent = findViewById(R.id.process_percent);
        mSpinnerModels = findViewById(R.id.spinner_models);
        mDownloadStatus = findViewById(R.id.download_status);
        mSignatureStatus = findViewById(R.id.signature_status);
        mSignatureValue = findViewById(R.id.signature_value);
        mRegistrationStatus = findViewById(R.id.registration_status);
        mKeepAliveCountdown = findViewById(R.id.keep_alive_countdown);

        populateFoldersSpinner();

        mSpinnerModels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    mModelName = (String) parent.getItemAtPosition(position);
                    mModelInfo.setText("Selected Model：" + mModelName);
                    mModelInfo.setVisibility(View.VISIBLE);
                    checkIfReadyToMine();
                } else {
                    mModelName = "";
                    mModelInfo.setVisibility(View.GONE);
                    mLoadButton.setEnabled(false);
                    mLoadButton.setClickable(false);
                    mLoadButton.setBackgroundColor(Color.parseColor("#2454e4")); // Or any other color to indicate disabled state
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mProcessHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                int progress = msg.arg1;
                mProcessBar.setProgress(progress);
                mProcessPercent.setText(" " + progress + "%");
                if (progress >= 100) {
                    mLoadButton.setClickable(true);
                    mLoadButton.setBackgroundColor(Color.parseColor("#3e3ddf"));
                    mLoadButton.setText("Loading completed");
                    mIntent.putExtra("chat", mChat);
                    mIntent.putExtra("workerId", registrationManager.getWorkerId()); // Add workerId
                    mIntent.putExtra("signature", registrationManager.getSignature());
                    mIntent.putExtra("grpcUrl", registrationManager.getGrpcServerAddress());
                    startActivity(mIntent);
                }
            }
        };

        downloadHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                filesDownloaded++;
                int progress = (int) ((filesDownloaded / (float) filesToDownload) * 100);
                mProcessBar.setProgress(progress);
                mProcessPercent.setText(" " + progress + "%");

                if (filesDownloaded >= filesToDownload) {
                    setDownloadStatus("All files downloaded successfully");
                    isModelDownloaded = true;
                    runOnUiThread(() -> populateFoldersSpinner());
                    checkIfReadyToMine();
                }
            }
        };

        registrationManager = new RegistrationManager(
                this,
                mDownloadStatus,
                mSignatureStatus,
                mSignatureValue,
                mRegistrationStatus,
                mProcessBar,
                mProcessPercent,
                downloadHandler,
                mKeepAliveCountdown,
                new Dialog(this)
        );
    }

    private void checkIfReadyToMine() {
        if (mModelName != null && !mModelName.isEmpty()) {
            mLoadButton.setEnabled(true);
            mLoadButton.setClickable(true);
            mLoadButton.setBackgroundColor(Color.parseColor("#3e3ddf")); // Or any other color to indicate enabled state
        }
    }

    private void setDownloadStatus(String message) {
        this.runOnUiThread(() -> mDownloadStatus.setText(message));
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                listFoldersInModelDirectory();
            } else {
                Toast.makeText(this, "Permission denied to read/write external storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void onCheckModels() {
        mModelInfo.setVisibility(View.VISIBLE);
        mModelInfo.setText(mModelName + " Model file is ready, model loading");
        mLoadButton.setText("Load model");
        mSpinnerModels.setEnabled(false);
        mSpinnerModels.setVisibility(View.VISIBLE);
    }

    public void listFoldersInModelDirectory() {
        File rootDir = Environment.getExternalStorageDirectory();
        File modelDir = new File(rootDir, "model");

        if (modelDir.exists() && isDirectory(modelDir)) {
            List<String> subfolders = new ArrayList<>();
            File[] files = modelDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        subfolders.add(file.getName());
                    }
                }
            }

            String subfoldersStr = subfolders.isEmpty() ? "No folders found." : String.join(", ", subfolders);
            Toast.makeText(this, "Folders inside the model folder: " + subfoldersStr, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Model directory not found or is not a directory.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isDirectory(File file) {
        return file.exists() && file.isDirectory();
    }

    private void populateFoldersSpinner() {
        File rootDir = Environment.getExternalStorageDirectory();
        File modelDir = new File(rootDir, "model");

        if (isDirectory(modelDir)) {
            List<String> subfolders = new ArrayList<>();
            File[] files = modelDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        subfolders.add(file.getName());
                    }
                }
            }

            subfolders.add(0, getString(R.string.spinner_prompt));
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subfolders);
            mSpinnerModels.setAdapter(adapter);
        }
    }

    public void loadModel(View view) {
        if (mModelName == null || mModelName.isEmpty()) {
            Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
            return;
        }

        onCheckModels();
        mLoadButton.setClickable(false);
        mLoadButton.setBackgroundColor(Color.parseColor("#2454e4"));
        mLoadButton.setText("Loading model...");
        mProcessView.setVisibility(View.VISIBLE);
        mChat = new Chat();
        Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mIntent.putExtra("chat", mChat);
                mIntent.putExtra("workerId", registrationManager.getWorkerId()); // Add workerId
                mIntent.putExtra("signature", registrationManager.getSignature());
                mIntent.putExtra("grpcUrl", registrationManager.getGrpcServerAddress());
                startActivity(mIntent);
            }
        };
        LoadThread loadT = new LoadThread(mChat, handler, Environment.getExternalStorageDirectory().getAbsolutePath() + "/model/" + mModelName);
        loadT.start();
        ProgressThread progressT = new ProgressThread(mChat, mProcessHandler);
        progressT.start();
    }

    public void scanQRCode() {
        registrationManager.scanQRCode();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        registrationManager.handleQRCodeResult(result);
    }

    private void checkStoredSignatureAndRegister() {
        registrationManager.checkStoredSignatureAndRegister();
    }
}

class LoadThread extends Thread {
    private Chat mChat;
    private Handler mHandler;
    private String mModelDir;

    LoadThread(Chat chat, Handler handler, String modelDir) {
        mChat = chat;
        mHandler = handler;
        mModelDir = modelDir;
    }

    public void run() {
        super.run();
        Log.d("LoadThread", "Initializing chat with model directory: " + mModelDir);
        mChat.Init(mModelDir);
        mHandler.sendMessage(new Message());
    }
}

class ProgressThread extends Thread {
    private Handler mHandler;
    private Chat mChat;

    ProgressThread(Chat chat, Handler handler) {
        mChat = chat;
        mHandler = handler;
    }

    public void run() {
        super.run();
        float progress = 0;
        while (progress < 100) {
            try {
                Thread.sleep(50);
            } catch (Exception e) {
            }
            float new_progress = mChat.Progress();
            Log.d("ProgressThread", "Progress: " + new_progress);
            if (Math.abs(new_progress - progress) < 0.01) {
                continue;
            }
            progress = new_progress;
            Message msg = new Message();
            msg.arg1 = (int) progress;
            mHandler.sendMessage(msg);
        }
    }
}
