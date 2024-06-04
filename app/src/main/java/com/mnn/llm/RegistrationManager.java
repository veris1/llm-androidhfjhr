package com.mnn.llm;

import android.app.Activity;
import android.app.Dialog;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegistrationManager {

    private static final String PREFS_NAME = "MyAppPreferences";
    private static final String KEY_SIGNATURE = "signature";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final long KEEP_ALIVE_INTERVAL = 5000; // 5 seconds in milliseconds

    private final Activity activity;
    private final TextView mDownloadStatus;
    private final TextView mSignatureStatus;
    private final TextView mSignatureValue;
    private final TextView mRegistrationStatus;
    private final ProgressBar mProcessBar;
    private final TextView mProcessPercent;
    private final Handler downloadHandler;
    private final Handler keepAliveHandler;
    private final TextView mKeepAliveCountdown;
    private final Dialog progressDialog;

    public String getSignature() {
        return signature;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getGrpcServerAddress() {
        return grpcServerAddress;
    }

    private String signature;
    private String workerId;
    private String grpcServerAddress;
    private int keepAliveCount = 0;
    private String serviceType = "LLM";
    private int countdownTime = 5; // Countdown timer in seconds

    private ExecutorService executorService;
    private int filesToDownload;
    private int filesDownloaded;

    public RegistrationManager(Activity activity, TextView mDownloadStatus, TextView mSignatureStatus, TextView mSignatureValue, TextView mRegistrationStatus, ProgressBar mProcessBar, TextView mProcessPercent, Handler downloadHandler, TextView mKeepAliveCountdown, Dialog progressDialog) {
        this.activity = activity;
        this.mDownloadStatus = mDownloadStatus;
        this.mSignatureStatus = mSignatureStatus;
        this.mSignatureValue = mSignatureValue;
        this.mRegistrationStatus = mRegistrationStatus;
        this.mProcessBar = mProcessBar;
        this.mProcessPercent = mProcessPercent;
        this.downloadHandler = downloadHandler;
        this.executorService = Executors.newFixedThreadPool(4);
        this.keepAliveHandler = new Handler();
        this.mKeepAliveCountdown = mKeepAliveCountdown;
        this.progressDialog = progressDialog;
    }

    public void scanQRCode() {
        IntentIntegrator integrator = new IntentIntegrator(activity);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan a QR code");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.initiateScan();
    }

    public void handleQRCodeResult(IntentResult result) {
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(activity, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                signature = result.getContents();
                Toast.makeText(activity, signature, Toast.LENGTH_LONG).show();
                setDownloadStatus("Scanned signature: " + signature);
                storeSignature(signature);
                updateSignatureStatus(signature);
                callRegisterAPI(signature);
            }
        }
    }

    private void callRegisterAPI(String signature) {
        showProgressDialog();
        OkHttpClient client = new OkHttpClient();

        Map<String, Object> data = new HashMap<>();
        data.put("signature", signature);
        data.put("gpu_info", null);
        data.put("service_type", "LLM");
        data.put("app_version", "0.4.1");
        data.put("os", "Android");
        data.put("models", null);
        data.put("cpu_architecture", "arm");
        data.put("cpu_ram", getDeviceRamInfo());

        JSONObject jsonData = new JSONObject(data);

        Log.i("API_CALL", "Request body:" + jsonData);

        RequestBody body = RequestBody.create(jsonData.toString(), JSON);
        Request request = new Request.Builder()
                .url("https://p2p.neurochain.io:8443/register")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("API_CALL", "Error: " + e.getMessage());
                setDownloadStatus("API call failed: " + e.getMessage());
                updateRegistrationStatus(false);
                dismissProgressDialog();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body().string();
                dismissProgressDialog();
                if (!response.isSuccessful()) {
                    final String errorMessage = "Unexpected code " + response.code() + ": " + responseData;
                    Log.e("API_CALL", errorMessage);
                    setDownloadStatus("API call failed: " + errorMessage);
                    updateRegistrationStatus(false);
                } else {
                    Log.d("API_CALL", "Response: " + responseData);
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        workerId = jsonResponse.getString("worker_id");
                        grpcServerAddress = jsonResponse.getString("rpc_url");

                        activity.runOnUiThread(() -> {
                            setDownloadStatus("API call successful: " + responseData);
                            updateRegistrationStatus(true);
                            handleQRResponse(responseData);
                        });
                        scheduleKeepAlive();
                    } catch (Exception e) {
                        e.printStackTrace();
                        setDownloadStatus("Failed to parse registration response");
                    }
                }
            }
        });
    }

    private void handleQRResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            String modelRepo = jsonResponse.getJSONArray("modelsConfigs").getJSONObject(0).getString("model_repo");
            String modelName = jsonResponse.getJSONArray("modelsConfigs").getJSONObject(0).getString("model_name");
            JSONArray supportingFiles = jsonResponse.getJSONArray("modelsConfigs").getJSONObject(0).getJSONArray("supporting_files");

            List<String> files = new ArrayList<>();
            for (int i = 0; i < supportingFiles.length(); i++) {
                files.add(supportingFiles.getString(i));
            }

            filesToDownload = files.size();
            filesDownloaded = 0;

            downloadModelFiles(modelRepo, files, modelName);
        } catch (Exception e) {
            e.printStackTrace();
            setDownloadStatus("Failed to parse QR response");
        }
    }

    private void downloadModelFiles(String modelRepo, List<String> files, String modelName) {
        File rootDir = Environment.getExternalStorageDirectory();
        File modelDir = new File(rootDir, "model");
        File modelFolder = new File(modelDir, modelName);

        if (!modelFolder.exists()) {
            boolean created = modelFolder.mkdirs();
            if (created) {
                setDownloadStatus("Created model folder: " + modelFolder.getAbsolutePath());
                Log.d("Download", "Created model folder: " + modelFolder.getAbsolutePath());
            } else {
                setDownloadStatus("Failed to create model folder: " + modelFolder.getAbsolutePath());
                Log.d("Download", "Failed to create model folder: " + modelFolder.getAbsolutePath());
                return;
            }
        } else {
            setDownloadStatus("Model folder already exists: " + modelFolder.getAbsolutePath());
            Log.d("Download", "Model folder already exists: " + modelFolder.getAbsolutePath());
        }

        for (String fileName : files) {
            File file = new File(modelFolder, fileName);
            File parentDir = file.getParentFile();

            if (!parentDir.exists()) {
                boolean parentCreated = parentDir.mkdirs();
                if (parentCreated) {
                    setDownloadStatus("Created directory: " + parentDir.getAbsolutePath());
                    Log.d("Download", "Created directory: " + parentDir.getAbsolutePath());
                } else {
                    setDownloadStatus("Failed to create directory: " + parentDir.getAbsolutePath());
                    Log.d("Download", "Failed to create directory: " + parentDir.getAbsolutePath());
                    continue;
                }
            }

            if (!file.exists()) {
                executorService.execute(() -> {
                    setDownloadStatus("Downloading: " + fileName + " to " + file.getAbsolutePath());
                    Log.d("Download", "Downloading: " + fileName + " to " + file.getAbsolutePath());
                    downloadFile(modelRepo, fileName, file);
                });
            } else {
                filesDownloaded++;
                int progress = (int) ((filesDownloaded / (float) filesToDownload) * 100);
                updateProgress(progress);
            }
        }
    }

    private void downloadFile(String modelRepo, String fileName, File file) {
        try {
            try (BufferedInputStream in = new BufferedInputStream(new URL(modelRepo + "/" + fileName).openStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                byte dataBuffer[] = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
                setDownloadStatus("Downloaded: " + fileName);
                Log.d("Download", "Downloaded: " + fileName);
                downloadHandler.sendEmptyMessage(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
            setDownloadStatus("Failed to download: " + fileName);
            Log.d("Download", "Failed to download: " + fileName);
        }
    }

    private void setDownloadStatus(String message) {
        activity.runOnUiThread(() -> mDownloadStatus.setText(message));
    }

    private void storeSignature(String signature) {
        SharedPreferences preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_SIGNATURE, signature);
        editor.apply();
    }

    public void checkStoredSignatureAndRegister() {
        SharedPreferences preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedSignature = preferences.getString(KEY_SIGNATURE, null);
        if (storedSignature != null) {
            signature = storedSignature;
            updateSignatureStatus(storedSignature);
            callRegisterAPI(storedSignature);
        } else {
            updateSignatureStatus(null);
        }
    }

    private void updateSignatureStatus(String signature) {
        if (signature != null) {
            mSignatureStatus.setText("Signature scanned");
            mSignatureValue.setText("Signature: " + signature);
        } else {
            mSignatureStatus.setText("No signature scanned");
            mSignatureValue.setText("Signature: N/A");
        }
    }

    private void updateRegistrationStatus(boolean success) {
        activity.runOnUiThread(() -> {
            if (success) {
                mRegistrationStatus.setText("Registered successfully");
                mRegistrationStatus.setTextColor(activity.getResources().getColor(android.R.color.holo_green_dark));
                mRegistrationStatus.setVisibility(View.VISIBLE);
            } else {
                mRegistrationStatus.setText("Registration failed");
                mRegistrationStatus.setTextColor(activity.getResources().getColor(android.R.color.holo_red_dark));
                mRegistrationStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private long getDeviceRamInfo() {
        ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalMemory = memoryInfo.totalMem;
        return totalMemory / (1024 * 1024 * 1024);
    }

    private void updateProgress(int progress) {
        activity.runOnUiThread(() -> {
            if (filesDownloaded >= filesToDownload) {
                setDownloadStatus("All files downloaded successfully");
            }
            mProcessBar.setProgress(progress);
            mProcessPercent.setText(" " + progress + "%");
        });
    }

    private void scheduleKeepAlive() {
        keepAliveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                callKeepAlive();
                countdownTime = 5; // Reset countdown timer
                keepAliveHandler.postDelayed(this, KEEP_ALIVE_INTERVAL);
                updateKeepAliveCountdown();
            }
        }, KEEP_ALIVE_INTERVAL);
        updateKeepAliveCountdown();
    }

    private void callKeepAlive() {
        OkHttpClient client = new OkHttpClient();

        Map<String, Object> data = new HashMap<>();
        data.put("signature", signature);
        data.put("worker_id", workerId);
        data.put("keep_alive_count", keepAliveCount);
        data.put("service_type", serviceType);

        JSONObject jsonData = new JSONObject(data);

        Log.i("KEEP_ALIVE", "Keep alive body:" + jsonData);

        RequestBody body = RequestBody.create(jsonData.toString(), JSON);
        Request request = new Request.Builder()
                .url("https://p2p.neurochain.io:8443/keep_alive")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("KEEP_ALIVE", "Error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("KEEP_ALIVE", "Keep alive failed. Response status code: " + response.code());
                } else {
                    Log.d("KEEP_ALIVE", "Keep alive successful");
                    keepAliveCount++;
                }
            }
        });
    }

    private void updateKeepAliveCountdown() {
        keepAliveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (countdownTime > 0) {
                    countdownTime--;
                    mKeepAliveCountdown.setText("Keep alive in: " + countdownTime + "s");
                    updateKeepAliveCountdown();
                }
            }
        }, 1000);
    }

    private void showProgressDialog() {
        activity.runOnUiThread(() -> {
            progressDialog.show();
            new Handler().postDelayed(() -> progressDialog.dismiss(), 3000);
        });
    }

    private void dismissProgressDialog() {
        activity.runOnUiThread(() -> progressDialog.dismiss());
    }
}
