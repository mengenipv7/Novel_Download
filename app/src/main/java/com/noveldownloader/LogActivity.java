package com.noveldownloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class LogActivity extends Activity {

    private static final int WRITE_LOG_CODE = 3001;

    private TextView tvLog;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Logger.LogListener listener = line ->
            handler.post(() -> {
                tvLog.append(line + "\n");
                scrollToBottom();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        StringBuilder sb = new StringBuilder();
        for (String line : Logger.getAll()) sb.append(line).append('\n');
        tvLog.setText(sb.toString());
        scrollToBottom();

        Button btnClear = findViewById(R.id.btnClearLog);
        Button btnBack = findViewById(R.id.btnBack);
        btnClear.setOnClickListener(v -> {
            Logger.clear();
            tvLog.setText("");
            tvLog.append(Logger.getAll().isEmpty() ? "" : join(Logger.getAll()));
        });
        btnBack.setOnClickListener(v -> finish());

        Button btnExport = findViewById(R.id.btnExportLog);
        btnExport.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("text/plain");
            it.putExtra(Intent.EXTRA_TITLE, "novel-downloader-log.txt");
            startActivityForResult(it, WRITE_LOG_CODE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_LOG_CODE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String text = tvLog.getText().toString();
            new Thread(() -> {
                try {
                    FileExporter.writeToUri(this, uri, text);
                    handler.post(() -> Toast.makeText(this, "日志已导出", Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    handler.post(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private String join(java.util.List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    private void scrollToBottom() {
        tvLog.post(() -> {
            int scroll = tvLog.getLayout() == null ? 0 :
                    tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
            tvLog.scrollTo(0, Math.max(0, scroll));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.addListener(listener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Logger.removeListener(listener);
    }
}
