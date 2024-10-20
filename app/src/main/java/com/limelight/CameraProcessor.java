package com.limelight;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Size;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class CameraProcessor {
    private final ProcessCameraProvider cameraProvider;
    private final CameraSelector cameraSelector;

    private final ImageAnalysis imageAnalysis;

    public int CAMERA_PORT = 50006;

    private WebSocket ws;

    @androidx.camera.core.ExperimentalGetImage
    CameraProcessor(Context context, String host) throws SocketException {
        WebSocketFactory factory = new WebSocketFactory();
        try {
            ws = factory.createSocket("ws://" + host + ":" + CAMERA_PORT + "/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
        try {
            cameraProvider = ProcessCameraProvider.getInstance(context).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }


        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        var resolutionSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                ResolutionStrategy.FALLBACK_RULE_NONE)).build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if(!ws.isOpen()){
                try {
                    if(ws.getState() != WebSocketState.CREATED) {
                        ws = ws.recreate();
                    }
                    ws.connect();
                    ws.sendText(imageProxy.getWidth() + "x" + imageProxy.getHeight() + "x30");
                } catch (WebSocketException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Bitmap bmp = BitmapUtils.getBitmap(imageProxy);
            assert bmp != null;
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 50, data);
            ws.sendBinary(data.toByteArray());
            imageProxy.close();
        });


    }

    public void unbind(){
        cameraProvider.unbindAll();
        ws.sendClose();
    }

    public boolean isBound(){
        return cameraProvider.isBound(imageAnalysis);
    }

    public void bind(LifecycleOwner lifecycleOwner){
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis);
    }
}
