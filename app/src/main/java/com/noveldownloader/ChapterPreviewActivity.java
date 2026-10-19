package com.noveldownloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** 章节正文预览页：点击目录条目进入，可查看正文并单章导出 TXT。 */
public class ChapterPreviewActivity extends Activity {

    private static final int WRITE_CHAPTER_CODE = 2001;

    private TextView tvTitle, tvBody, tvStatus;
    private Button btnDownload;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NovelParser parser = new NovelParser();

    private ChapterInfo chapter;
    private volatile boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter_preview);

        tvTitle = findViewById(R.id.tvPreviewTitle);
        tvBody = findViewById(R.id.tvPreviewBody);
        tvStatus = findViewById(R.id.tvPreviewStatus);
        btnDownload = findViewById(R.id.btnDownloadChapter);
        Button btnBack = findViewById(R.id.btnPreviewBack);
        tvBody.setMovementMethod(new ScrollingMovementMethod());
        parser.setContentRenderer(new WebViewRenderer(this));

        int index = getIntent().getIntExtra("index", -1);
        NovelResult novel = MainActivity.currentNovel;
        if (novel == null || index < 0 || index >= novel.chapters.size()) {
            Toast.makeText(this, "章节不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        chapter = novel.chapters.get(index);
        tvTitle.setText(chapter.title);
        btnBack.setOnClickListener(v -> finish());
        btnDownload.setOnClickListener(v -> saveChapterAs());

        if (chapter.content != null && !chapter.content.isEmpty()) {
            showContent(chapter.content);
        } else {
            loadContent();
        }
    }

    private void loadContent() {
        loading = true;
        tvStatus.setText("正在加载正文...");
        btnDownload.setEnabled(false);
        new Thread(() -> {
            try {
                chapter.content = parser.fetchChapterContent(chapter, null, null);
                handler.post(() -> showContent(chapter.content));
            } catch (Exception e) {
                Logger.e("Preview", "加载正文失败", e);
                handler.post(() -> {
                    tvStatus.setText("加载失败: " + e.getMessage());
                    btnDownload.setEnabled(false);
                    loading = false;
                });
            }
        }).start();
    }

    private void showContent(String content) {
        loading = false;
        tvBody.setText(content);
        tvStatus.setText(content.length() + " 字");
        btnDownload.setEnabled(true);
        tvBody.post(() -> tvBody.scrollTo(0, 0));
    }

    /** 单章另存为 TXT（系统保存对话框，可选择位置） */
    private void saveChapterAs() {
        if (chapter.content == null || chapter.content.isEmpty()) {
            Toast.makeText(this, "正文尚未加载完成", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, FileExporter.sanitize(chapter.title) + ".txt");
        startActivityForResult(intent, WRITE_CHAPTER_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_CHAPTER_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            String content = chapter.content;
            tvStatus.setText("正在写出...");
            new Thread(() -> {
                try {
                    FileExporter.writeToUri(this, uri, content);
                    handler.post(() -> {
                        tvStatus.setText("已保存: " + uri.getLastPathSegment());
                        Toast.makeText(this, "本章 TXT 已保存", Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    Logger.e("Preview", "保存失败", e);
                    handler.post(() -> tvStatus.setText("保存失败: " + e.getMessage()));
                }
            }).start();
        }
    }
}
