package com.example.barcodescanner;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BarcodeScanner";

    private PreviewView previewView;
    private TextView tvResult;
    private TextView tvHint;
    private Button btnCopy;
    private Button btnRescan;
    private View resultCard;
    private View scannerOverlay;
    private View scanLine;
    private ImageView ivSuccess;

    private ExecutorService cameraExecutor;
    private String lastScannedResult = "";
    private volatile boolean isScanning = true;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    tvHint.setText("请授予相机权限后重启应用");
                    Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView  = findViewById(R.id.previewView);
        tvResult     = findViewById(R.id.tvResult);
        tvHint       = findViewById(R.id.tvHint);
        btnCopy      = findViewById(R.id.btnCopy);
        btnRescan    = findViewById(R.id.btnRescan);
        resultCard   = findViewById(R.id.resultCard);
        scannerOverlay = findViewById(R.id.scannerOverlay);
        scanLine     = findViewById(R.id.scanLine);
        ivSuccess    = findViewById(R.id.ivSuccess);

        cameraExecutor = Executors.newSingleThreadExecutor();

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnRescan.setOnClickListener(v -> startScanning());

        startScanLineAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanLineAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, 500f);
        animator.setDuration(2000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider =
                        ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build();
                BarcodeScanner scanner = BarcodeScanning.getClient(options);

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (!isScanning) {
                        imageProxy.close();
                        return;
                    }
                    try {
                        @SuppressWarnings("UnsafeOptInUsageError")
                        InputImage image = InputImage.fromMediaImage(
                                imageProxy.getImage(),
                                imageProxy.getImageInfo().getRotationDegrees());

                        scanner.process(image)
                                .addOnSuccessListener(barcodes -> {
                                    if (!barcodes.isEmpty() && isScanning) {
                                        String value = barcodes.get(0).getRawValue();
                                        if (value != null && !value.isEmpty()) {
                                            isScanning = false;
                                            runOnUiThread(() -> showResult(value));
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Scan error", e))
                                .addOnCompleteListener(t -> imageProxy.close());
                    } catch (Exception e) {
                        Log.e(TAG, "Analysis error", e);
                        imageProxy.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Camera init error", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "相机启动失败: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showResult(String result) {
        lastScannedResult = result;
        vibrate();
        tvResult.setText(result);
        resultCard.setVisibility(View.VISIBLE);
        scannerOverlay.setVisibility(View.GONE);
        tvHint.setText("扫描完成");
        ivSuccess.setVisibility(View.VISIBLE);
        resultCard.setAlpha(0f);
        resultCard.animate().alpha(1f).setDuration(300).start();
    }

    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager)
                        getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(
                            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibrate error", e);
        }
    }

    private void copyToClipboard() {
        if (lastScannedResult.isEmpty()) return;
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("barcode", lastScannedResult));

        btnCopy.setText("✓ 已复制");
        btnCopy.setBackgroundTintList(
                ContextCompat.getColorStateList(this, R.color.success_green));
        btnCopy.postDelayed(() -> {
            btnCopy.setText("复制");
            btnCopy.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.accent_blue));
        }, 2000);
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void startScanning() {
        lastScannedResult = "";
        isScanning = true;
        resultCard.setVisibility(View.GONE);
        scannerOverlay.setVisibility(View.VISIBLE);
        tvHint.setText("将条形码对准扫描框");
        ivSuccess.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
