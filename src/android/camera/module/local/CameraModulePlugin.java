package camera.module.local;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraModulePlugin extends CordovaPlugin {

    private static final int CAMERA_PERMISSION_REQ_CODE = 1001;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private Camera camera;

    private final CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

    private boolean isScanning = false;
    private CallbackContext scanCallbackContext;
    private BarcodeScanner barcodeScanner;
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private CallbackContext permissionCallbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        switch (action) {
            case "checkPermission":
                checkPermission(callbackContext);
                return true;

            case "requestPermission":
                requestPermission(callbackContext);
                return true;

            case "checkAndRequestPermission":
                checkAndRequestPermission(callbackContext);
                return true;

            case "startPreview":
                startPreview(callbackContext);
                return true;

            case "stopPreview":
                stopPreview(callbackContext);
                return true;

            case "takePhotoBase64":
                takePhotoBase64(callbackContext);
                return true;

            case "startBarcodeScan":
                startBarcodeScan(callbackContext);
                return true;

            case "stopBarcodeScan":
                stopBarcodeScan(callbackContext);
                return true;

            default:
                return false;
        }
    }

    private void checkPermission(CallbackContext callbackContext) {
        try {
            boolean granted = ContextCompat.checkSelfPermission(
                cordova.getActivity(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED;

            JSONObject ret = new JSONObject();
            ret.put("granted", granted);
            ret.put("status", granted ? "granted" : "prompt");
            ret.put("details", "Android camera permission");

            callbackContext.success(ret);
        } catch (Exception e) {
            callbackContext.error("checkPermission error: " + e.getMessage());
        }
    }

    private void requestPermission(CallbackContext callbackContext) {
        if (cordova.hasPermission(Manifest.permission.CAMERA)) {
            try {
                JSONObject ret = new JSONObject();
                ret.put("granted", true);
                ret.put("status", "granted");
                ret.put("details", "Already granted");
                callbackContext.success(ret);
            } catch (Exception e) {
                callbackContext.error("requestPermission error: " + e.getMessage());
            }
            return;
        }

        this.permissionCallbackContext = callbackContext;
        cordova.requestPermission(this, CAMERA_PERMISSION_REQ_CODE, Manifest.permission.CAMERA);
    }

    private void checkAndRequestPermission(CallbackContext callbackContext) {
        if (cordova.hasPermission(Manifest.permission.CAMERA)) {
            try {
                JSONObject ret = new JSONObject();
                ret.put("granted", true);
                ret.put("status", "granted");
                ret.put("details", "Already granted");
                callbackContext.success(ret);
            } catch (Exception e) {
                callbackContext.error("checkAndRequestPermission error: " + e.getMessage());
            }
        } else {
            requestPermission(callbackContext);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        super.onRequestPermissionResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQ_CODE && permissionCallbackContext != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            JSONObject ret = new JSONObject();
            ret.put("granted", granted);
            ret.put("status", granted ? "granted" : "denied");
            ret.put("details", "Permission request completed");

            permissionCallbackContext.success(ret);
            permissionCallbackContext = null;
        }
    }

    private void startPreview(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (!cordova.hasPermission(Manifest.permission.CAMERA)) {
                    callbackContext.error("Camera permission not granted");
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

                ViewGroup rootView = cordova.getActivity().findViewById(android.R.id.content);
                rootView.addView(previewView, 0);

                ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(cordova.getActivity());

                cameraProviderFuture.addListener(() -> {
                    try {
                        cameraProvider = cameraProviderFuture.get();

                        Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();

                        cameraProvider.unbindAll();

                        camera = cameraProvider.bindToLifecycle(
                            (LifecycleOwner) cordova.getActivity(),
                            cameraSelector,
                            preview,
                            imageCapture
                        );

                    } catch (Exception e) {
                        callbackContext.error("startPreview bind error: " + e.getMessage());
                    }
                }, ContextCompat.getMainExecutor(cordova.getActivity()));

                callbackContext.success();

            } catch (Exception e) {
                callbackContext.error("startPreview error: " + e.getMessage());
            }
        });
    }

    private void stopPreview(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                isScanning = false;
                imageAnalysis = null;

                if (barcodeScanner != null) {
                    barcodeScanner.close();
                    barcodeScanner = null;
                }

                if (scanCallbackContext != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                    result.setKeepCallback(false);
                    scanCallbackContext.sendPluginResult(result);
                    scanCallbackContext = null;
                }

                if (camera != null) {
                    camera = null;
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
                callbackContext.error("stopPreview error: " + e.getMessage());
            }
        });
    }

    private void takePhotoBase64(CallbackContext callbackContext) {
        if (imageCapture == null) {
            callbackContext.error("Camera not initialized");
            return;
        }

        try {
            File photoFile = File.createTempFile(
                "photo_",
                ".jpg",
                cordova.getActivity().getCacheDir()
            );

            ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(cordova.getActivity()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        try {
                            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

                            if (bitmap == null) {
                                callbackContext.error("Unable to decode captured image");
                                return;
                            }

                            Bitmap resized = resizeBitmap(bitmap, 1024);

                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            resized.compress(Bitmap.CompressFormat.JPEG, 80, out);

                            String base64 = Base64.encodeToString(
                                out.toByteArray(),
                                Base64.NO_WRAP
                            );

                            JSONObject ret = new JSONObject();
                            ret.put("base64", base64);
                            ret.put("mimeType", "image/jpeg");

                            callbackContext.success(ret);

                            bitmap.recycle();
                            if (resized != bitmap) {
                                resized.recycle();
                            }
                            photoFile.delete();

                        } catch (Exception e) {
                            callbackContext.error("Error processing image: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        callbackContext.error("Capture failed: " + exception.getMessage());
                    }
                }
            );

        } catch (Exception e) {
            callbackContext.error("Error creating temp file: " + e.getMessage());
        }
    }

    private void startBarcodeScan(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (previewView == null) {
                    callbackContext.error("Preview not started");
                    return;
                }

                if (!cordova.hasPermission(Manifest.permission.CAMERA)) {
                    callbackContext.error("Camera permission not granted");
                    return;
                }

                if (cameraProvider == null) {
                    callbackContext.error("Camera not initialized");
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

                imageAnalysis.setAnalyzer(barcodeExecutor, this::processBarcode);

                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

                camera = cameraProvider.bindToLifecycle(
                    (LifecycleOwner) cordova.getActivity(),
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                );

            } catch (Exception e) {
                callbackContext.error("startBarcodeScan error: " + e.getMessage());
            }
        });
    }

    private void processBarcode(ImageProxy imageProxy) {
        if (!isScanning) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        if (barcodeScanner == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(image)
            .addOnSuccessListener(barcodes -> {
                try {
                    if (!isScanning) {
                        imageProxy.close();
                        return;
                    }

                    if (barcodes != null && !barcodes.isEmpty()) {
                        Barcode barcode = barcodes.get(0);

                        JSONObject ret = new JSONObject();
                        ret.put("rawValue", barcode.getRawValue());
                        ret.put("format", barcode.getFormat());

                        vibrateOnce();

                        if (scanCallbackContext != null) {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, ret);
                            result.setKeepCallback(false);
                            scanCallbackContext.sendPluginResult(result);
                            scanCallbackContext = null;
                        }

                        isScanning = false;

                        if (barcodeScanner != null) {
                            barcodeScanner.close();
                            barcodeScanner = null;
                        }

                        restartNormalPreview();
                    }

                } catch (Exception ignored) {
                } finally {
                    imageProxy.close();
                }
            })
            .addOnFailureListener(e -> imageProxy.close());
    }

    private void stopBarcodeScan(CallbackContext callbackContext) {
        try {
            isScanning = false;
            imageAnalysis = null;

            if (barcodeScanner != null) {
                barcodeScanner.close();
                barcodeScanner = null;
            }

            if (scanCallbackContext != null) {
                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(false);
                scanCallbackContext.sendPluginResult(result);
                scanCallbackContext = null;
            }

            restartNormalPreview();
            callbackContext.success();

        } catch (Exception e) {
            callbackContext.error("stopBarcodeScan error: " + e.getMessage());
        }
    }

    private void restartNormalPreview() {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (cameraProvider == null || previewView == null) {
                    return;
                }

                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

                camera = cameraProvider.bindToLifecycle(
                    (LifecycleOwner) cordova.getActivity(),
                    cameraSelector,
                    preview,
                    imageCapture
                );
            } catch (Exception ignored) {
            }
        });
    }

    private void vibrateOnce() {
        try {
            Vibrator vibrator = (Vibrator) cordova.getActivity().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(120);
            }
        } catch (Exception ignored) {
        }
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float ratio = (float) width / (float) height;

        if (width > height) {
            if (width <= maxSize) return bitmap;
            width = maxSize;
            height = Math.round(width / ratio);
        } else {
            if (height <= maxSize) return bitmap;
            height = maxSize;
            width = Math.round(height * ratio);
        }

        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    @Override
    public void onDestroy() {
        try {
            isScanning = false;

            if (barcodeScanner != null) {
                barcodeScanner.close();
                barcodeScanner = null;
            }

            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }

            if (barcodeExecutor != null && !barcodeExecutor.isShutdown()) {
                barcodeExecutor.shutdown();
            }
        } catch (Exception ignored) {
        }

        super.onDestroy();
    }
}