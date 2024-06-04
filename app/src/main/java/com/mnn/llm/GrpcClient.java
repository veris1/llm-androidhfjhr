package com.mnn.llm;

import ai.neurochain.grpc.gateway.*;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.mnn.llm.Chat;
import com.mnn.llm.R;

import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.TimeUnit;

public class GrpcClient {
    private static final String TAG = "GrpcClient";
    private final ManagedChannel channel;
    private final WorkerDataServiceGrpc.WorkerDataServiceStub asyncStub;

    public GrpcClient(Context context, String hostWithPort) throws Exception {
        URI uri = new URI("https://" + hostWithPort);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 443 : uri.getPort(); // Default to port 443 if none specified

        try {
            // Load the server's certificate from the raw resource directory
            InputStream certInputStream = context.getResources().openRawResource(R.raw.server); // Use R.raw.server if the file is named server.crt
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca = cf.generateCertificate(certInputStream);

            // Create a KeyStore containing our trusted CAs
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            this.channel = OkHttpChannelBuilder.forAddress(host, port)
                    .sslSocketFactory(sslContext.getSocketFactory())
                    .build();

            this.asyncStub = WorkerDataServiceGrpc.newStub(channel);

            Log.i(TAG, "Connected to gRPC server at " + hostWithPort);
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to gRPC server at " + hostWithPort, e);
            throw e;
        }
    }

    public void shutdown() throws InterruptedException {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            Log.i(TAG, "gRPC channel shutdown successfully.");
        } catch (InterruptedException e) {
            Log.e(TAG, "gRPC channel shutdown interrupted.", e);
            throw e;
        }
    }

    public void streamTaskRequests(String signature, String workerId, Chat chat, Handler responseHandler) {
        Log.i(TAG, "streamTaskRequests called with signature: " + signature + " and workerId: " + workerId);

        Registration registration = Registration.newBuilder()
                .setSignature(signature)
                .setWorkerId(workerId)
                .build();

        asyncStub.streamTaskRequests(registration, new StreamObserver<TaskRequest>() {
            @Override
            public void onNext(TaskRequest taskRequest) {
                Log.i(TAG, "Task arrived...");

                // Log message arrival
                Message logMsgArrival = new Message();
                logMsgArrival.obj = "Task arrived with messageId: " + taskRequest.getMessageId();
                responseHandler.sendMessage(logMsgArrival);

                chat.Submit(taskRequest.getPrompt());
                String last_response = "";
                System.out.println("[MNN_DEBUG] start response\n");
                while (!last_response.contains("<eop>")) {
                    try {
                        Thread.sleep(50);
                    } catch (Exception e) {}
                    String response = new String(chat.Response());
                    if (response.equals(last_response)) {
                        continue;
                    } else {
                        last_response = response;
                    }
                    System.out.println("[MNN_DEBUG] " + response);
                }
                System.out.println("[MNN_DEBUG] response end\n");

                chat.Done();
                chat.Reset();
                last_response = last_response.replaceFirst("<eop>", "");
                Log.i(TAG, "Task response: " + last_response);
                TaskResponse taskResponse = TaskResponse.newBuilder()
                        .setSignature(signature)
                        .setWorkerId(workerId)
                        .setDataOutput(last_response)
                        .setDataType("string")
                        .setMessageId(taskRequest.getMessageId())
                        .build();

                // Send the TaskResponse to the server
                StreamObserver<TaskResponse> responseObserver = asyncStub.streamTaskResponses(new StreamObserver<StatusResponse>() {
                    @Override
                    public void onNext(StatusResponse statusResponse) {
                        if (statusResponse.getSuccess()) {
                            Log.i(TAG, "Task processed successfully.");
                        } else {
                            Log.e(TAG, "Failed to process task: " + statusResponse.getMessage());
                        }
                    }
                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "Failed to process task response: " + t.getMessage());
                    }
                    @Override
                    public void onCompleted() {
                        Log.i(TAG, "Task response stream completed.");
                    }
                });

                responseObserver.onNext(taskResponse);
                responseObserver.onCompleted();

                // Log message response
                Message logMsgResponse = new Message();
                logMsgResponse.obj = "Response sent for messageId: " + taskRequest.getMessageId();
                responseHandler.sendMessage(logMsgResponse);
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "Failed to receive task request: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                Log.i(TAG, "Task request stream completed.");
            }
        });
    }
}
