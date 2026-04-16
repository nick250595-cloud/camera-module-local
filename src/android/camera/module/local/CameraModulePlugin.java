package camera.module.local;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONObject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import android.media.Image;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraModulePlugin extends CordovaPlugin {

    private static final int CAMERA_PERMISSION_REQ_CODE = 1001;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    private ImageAnalysis imageAnalysis;
    private BarcodeScanner barcodeScanner;
    private boolean isScanning = false;

    private CallbackContext scanCallbackContext;
    private CallbackContext permissionCallbackContext;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {

        switch (action) {

            case "checkAndRequestPermission":
                checkAndRequestPermission(callbackContext);
                return true;

            case "startPreview":
                startPreview(callbackContext);
                return true;

            case "startBarcodeScan":
                startBarcodeScan(callbackContext);
                return true;

            case "stopPreview":
                stopPreview(callbackContext);
                return true;

            default:
                return false;
        }
    }

    // ---------------- PERMISOS ----------------

    private void checkAndRequestPermission(CallbackContext callbackContext) {
        if (cordova.hasPermission(Manifest.permission.CAMERA)) {
            callbackContext.success();
        } else {
            this.permissionCallbackContext = callbackContext;
            cordova.requestPermission(this, CAMERA_PERMISSION_REQ_CODE, Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode == CAMERA_PERMISSION_REQ_CODE && permissionCallbackContext != null) {

            boolean granted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                permissionCallbackContext.success();
            } else {
                permissionCallbackContext.error("Permission denied");
            }

            permissionCallbackContext = null;
        }
    }

    // ---------------- PREVIEW ----------------

    private void startPreview(CallbackContext callbackContext) {

        cordova.getActivity().runOnUiThread(() -> {
            try {

                if (!cordova.hasPermission(Manifest.permission.CAMERA)) {
                    callbackContext.error("No permission");
                    return;
                }

                if (previewView != null) {
                    callbackContext.success();
                    return;
                }

                previewView = new PreviewView(cordova.getActivity());
                previewView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

                ViewGroup root = cordova.getActivity().findViewById(android.R.id.content);

                if (previewView.getParent() == null) {
                    root.addView(previewView, 0);
                }

                ListenableFuture<ProcessCameraProvider> future =
                        ProcessCameraProvider.getInstance(cordova.getActivity());

                future.addListener(() -> {
                    try {

                        cameraProvider = future.get();

                        Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        cameraProvider.unbindAll();

                        camera = cameraProvider.bindToLifecycle(
                                (LifecycleOwner) cordova.getActivity(),
                                cameraSelector,
                                preview
                        );

                        // 🔥 FIX CRÍTICO: FORZAR RENDER
                        previewView.post(() -> {
                            previewView.setVisibility(android.view.View.VISIBLE);
                            previewView.bringToFront();
                            previewView.invalidate();
                            previewView.requestLayout();
                        });

                        callbackContext.success();

                    } catch (Exception e) {
                        callbackContext.error("Preview error: " + e.getMessage());
                    }

                }, ContextCompat.getMainExecutor(cordova.getActivity()));

            } catch (Exception e) {
                callbackContext.error("Start error: " + e.getMessage());
            }
        });
    }

    // ---------------- QR SCAN ----------------

    private void startBarcodeScan(CallbackContext callbackContext) {

        cordova.getActivity().runOnUiThread(() -> {
            try {

                if (previewView == null) {
                    callbackContext.error("Preview not started");
                    return;
                }

                if (isScanning) {
                    callbackContext.error("Already scanning");
                    return;
                }

                barcodeScanner = BarcodeScanning.getClient();
                isScanning = true;
                scanCallbackContext = callbackContext;

                PluginResult pending = new PluginResult(PluginResult.Status.NO_RESULT);
                pending.setKeepCallback(true);
                callbackContext.sendPluginResult(pending);

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, this::processImage);

                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                camera = cameraProvider.bindToLifecycle(
                        (LifecycleOwner) cordova.getActivity(),
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (Exception e) {
                callbackContext.error("Scan error: " + e.getMessage());
            }
        });
    }

    private void processImage(ImageProxy imageProxy) {

        if (!isScanning) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {

                    if (barcodes != null && !barcodes.isEmpty()) {

                        Barcode barcode = barcodes.get(0);

                        try {
                            JSONObject ret = new JSONObject();
                            ret.put("value", barcode.getRawValue());

                            PluginResult result = new PluginResult(PluginResult.Status.OK, ret);
                            result.setKeepCallback(false);

                            scanCallbackContext.sendPluginResult(result);

                        } catch (Exception ignored) {}

                        isScanning = false;
                    }

                    imageProxy.close();

                })
                .addOnFailureListener(e -> imageProxy.close());
    }

    // ---------------- STOP ----------------

    private void stopPreview(CallbackContext callbackContext) {

        cordova.getActivity().runOnUiThread(() -> {
            try {

                isScanning = false;

                if (barcodeScanner != null) {
                    barcodeScanner.close();
                    barcodeScanner = null;
                }

                if (cameraProvider != null) {
                    cameraProvider.unbindAll();
                }

                if (previewView != null && previewView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) previewView.getParent()).removeView(previewView);
                    previewView = null;
                }

                callbackContext.success();

            } catch (Exception e) {
                callbackContext.error("Stop error: " + e.getMessage());
            }
        });
    }
}
