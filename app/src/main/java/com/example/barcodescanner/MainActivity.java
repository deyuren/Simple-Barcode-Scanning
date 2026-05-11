package com.example.barcodescanner;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
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
    private volatile boolean isScanning = true;
    private String lastScannedResult = "";
    private BarcodeScanner barcodeScanner;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView    = findViewById(R.id.previewView);
        tvResult       = findViewById(R.id.tvResult);
        tvHint         = findViewById(R.id.tvHint);
        btnCopy        = findViewById(R.id.btnCopy);
        btnRescan      = findViewById(R.id.btnRescan);
        resultCard     = findViewById(R.id.resultCard);
        scannerOverlay = findViewById(R.id.scannerOverlay);
        scanLine       = findViewById(R.id.scanLine);
        ivSuccess      = findViewById(R.id.ivSuccess);

        cameraExecutor = Executors.newSingleThreadExecutor();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnRescan.setOnClickListener(v -> resumeScanning());

        startScanLineAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanLineAnimation() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, 480f);
        anim.setDuration(1800);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.setRepeatMode(ObjectAnimator.REVERSE);
        anim.start();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );

            } catch (Exception e) {
                Log.e(TAG, "startCamera failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "相机启动失败，请重启应用", Toast.LENGTH_LONG).show()
                );
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (!isScanning) {
            imageProxy.close();
            return;
        }

        try {
            @SuppressWarnings("UnsafeOptInUsageError")
            android.media.Image mediaImage = imageProxy.getImage();
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
                        if (!barcodes.isEmpty() && isScanning) {
                            Barcode barcode = barcodes.get(0);
                            String value = barcode.getRawValue();
                            if (value != null && !value.isEmpty()) {
                                isScanning = false;
                                runOnUiThread(() -> showResult(value));
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "Scan failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());

        } catch (Exception e) {
            Log.e(TAG, "analyzeImage error", e);
            imageProxy.close();
        }
    }

    private void showResult(String result) {
        lastScannedResult = result;
        vibrate();

        tvResult.setText(result);
        scannerOverlay.setVisibility(View.GONE);
        tvHint.setText("扫描完成");
        ivSuccess.setVisibility(View.VISIBLE);

        resultCard.setAlpha(0f);
        resultCard.setVisibility(View.VISIBLE);
        resultCard.animate().alpha(1f).setDuration(250).start();
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception e) {
            Log.e(TAG, "vibrate error", e);
        }
    }

    private void copyToClipboard() {
        if (lastScannedResult.isEmpty()) return;

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("barcode", lastScannedResult));

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

    private void resumeScanning() {
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
        barcodeScanner.close();
    }
}
