package com.example.barcodescanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    static final String PREFS_NAME = "barcode_prefs";
    static final String KEY_HISTORY = "history";

    private List<String[]> historyList = new ArrayList<>();
    private HistoryAdapter adapter;
    private View emptyView;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        emptyView   = findViewById(R.id.emptyView);
        recyclerView = findViewById(R.id.recyclerView);

        loadHistory();

        adapter = new HistoryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        updateEmptyState();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnClear).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("清空历史记录")
                        .setMessage("确定要删除所有记录吗？")
                        .setPositiveButton("清空", (d, w) -> {
                            historyList.clear();
                            persistHistory();
                            adapter.notifyDataSetChanged();
                            updateEmptyState();
                            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show()
        );
    }

    private void updateEmptyState() {
        if (historyList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
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
        } catch (JSONException e) {
            Log.e("HistoryActivity", "loadHistory error", e);
        }
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
        } catch (JSONException e) {
            Log.e("HistoryActivity", "persistHistory error", e);
        }
    }

    // ── RecyclerView Adapter ──────────────────────────────

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvValue, tvTime, btnCopy;
            VH(View v) {
                super(v);
                tvValue = v.findViewById(R.id.tvValue);
                tvTime  = v.findViewById(R.id.tvTime);
                btnCopy = v.findViewById(R.id.btnItemCopy);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            String[] item = historyList.get(position);
            holder.tvValue.setText(item[0]);
            holder.tvTime.setText(item[1]);

            holder.btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("barcode", item[0]));
                Toast.makeText(HistoryActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            });

            // 长按删除单条
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setMessage("删除这条记录？")
                        .setPositiveButton("删除", (d, w) -> {
                            int pos = holder.getAdapterPosition();
                            historyList.remove(pos);
                            notifyItemRemoved(pos);
                            persistHistory();
                            updateEmptyState();
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return historyList.size(); }
    }
}
