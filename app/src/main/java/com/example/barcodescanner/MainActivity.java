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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;

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
    private boolean isScanning = true;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        tvResult = findViewById(R.id.tvResult);
        tvHint = findViewById(R.id.tvHint);
        btnCopy = findViewById(R.id.btnCopy);
        btnRescan = findViewById(R.id.btnRescan);
        resultCard = findViewById(R.id.resultCard);
        scannerOverlay = findViewById(R.id.scannerOverlay);
        scanLine = findViewById(R.id.scanLine);
        ivSuccess = findViewById(R.id.ivSuccess);

        cameraExecutor = Executors.newSingleThreadExecutor();

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnRescan.setOnClickListener(v -> startScanning());

        // 开始扫描线动画
        startScanLineAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    private void startScanLineAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, 600f);
        animator.setDuration(2000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

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

            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty() && isScanning) {
                            Barcode barcode = barcodes.get(0);
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null && !rawValue.isEmpty()) {
                                isScanning = false;
                                runOnUiThread(() -> showResult(rawValue));
                            }
                        }
                    })
                    .addOnFailureListener(e -> e.printStackTrace())
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void showResult(String result) {
        lastScannedResult = result;

        // 振动反馈
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        // 显示结果卡片
        tvResult.setText(result);
        resultCard.setVisibility(View.VISIBLE);
        scannerOverlay.setVisibility(View.GONE);
        tvHint.setText("扫描完成");
        ivSuccess.setVisibility(View.VISIBLE);

        // 动画效果
        resultCard.setAlpha(0f);
        resultCard.animate().alpha(1f).setDuration(300).start();
    }

    private void copyToClipboard() {
        if (lastScannedResult.isEmpty()) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("barcode", lastScannedResult);
        clipboard.setPrimaryClip(clip);

        // 按钮反馈
        btnCopy.setText("✓ 已复制");
        btnCopy.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success_green));
        btnCopy.postDelayed(() -> {
            btnCopy.setText("复制");
            btnCopy.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent_blue));
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
                tvHint.setText("请授予相机权限后重启应用");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
