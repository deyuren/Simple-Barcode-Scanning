package com.example.barcodescanner;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
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

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BarcodeScanner";
    private static final String PREFS_NAME = "barcode_prefs";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 50;

    private PreviewView previewView;
    private TextView tvResult, tvHint;
    private Button btnCopy, btnRescan, btnHistory;
    private View resultCard, scannerOverlay, scanLine;
    private ImageView ivSuccess;

    private ExecutorService cameraExecutor;
    private volatile boolean isScanning = true;
    private String lastScannedResult = "";
    private BarcodeScanner barcodeScanner;
    private ToneGenerator toneGenerator;
    private List<String[]> historyList = new ArrayList<>();

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
        btnHistory     = findViewById(R.id.btnHistory);
        resultCard     = findViewById(R.id.resultCard);
        scannerOverlay = findViewById(R.id.scannerOverlay);
        scanLine       = findViewById(R.id.scanLine);
        ivSuccess      = findViewById(R.id.ivSuccess);

        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (Exception e) {
            Log.e(TAG, "ToneGenerator init failed", e);
        }

        barcodeScanner = BarcodeScanning.getClient(
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build());

        loadHistory();

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnRescan.setOnClickListener(v -> resumeScanning());
        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

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

                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

            } catch (Exception e) {
                Log.e(TAG, "startCamera failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "相机启动失败，请重启应用", Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(androidx.camera.core.ImageProxy imageProxy) {
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
                    mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty() && isScanning) {
                            String value = barcodes.get(0).getRawValue();
                            if (value != null && !value.isEmpty()) {
                                isScanning = false;
                                runOnUiThread(() -> showResult(value));
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "scan failed", e))
                    .addOnCompleteListener(t -> imageProxy.close());
        } catch (Exception e) {
            Log.e(TAG, "analyzeImage error", e);
            imageProxy.close();
        }
    }

    private void showResult(String result) {
        lastScannedResult = result;
        beep();
        vibrate();
        saveToHistory(result);

        tvResult.setText(result);
        scannerOverlay.setVisibility(View.GONE);
        tvHint.setText("扫描完成");
        ivSuccess.setVisibility(View.VISIBLE);
        resultCard.setAlpha(0f);
        resultCard.setVisibility(View.VISIBLE);
        resultCard.animate().alpha(1f).setDuration(250).start();
    }

    private void beep() {
        try {
            if (toneGenerator != null)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (Exception e) {
            Log.e(TAG, "beep error", e);
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator())
                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception e) {
            Log.e(TAG, "vibrate error", e);
        }
    }

    private void saveToHistory(String value) {
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date());
        historyList.add(0, new String[]{value, time});
        if (historyList.size() > MAX_HISTORY)
            historyList.remove(historyList.size() - 1);
        persistHistory();
    }

    private void persistHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (String[] item : historyList) {
                JSONArray entry = new JSONArray();
                entry.put(item[0]);
                entry.put(item[1]);
                arr.put(entry);
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(KEY_HISTORY, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "persistHistory error", e);
        }
    }

    private void loadHistory() {
        try {
            String json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_HISTORY, "[]");
            JSONArray arr = new JSONArray(json);
            historyList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONArray entry = arr.getJSONArray(i);
                historyList.add(new String[]{entry.getString(0), entry.getString(1)});
            }
        } catch (Exception e) {
            Log.e(TAG, "loadHistory error", e);
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
        if (toneGenerator != null) toneGenerator.release();
    }
}
