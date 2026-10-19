package com.noveldownloader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int WRITE_BOOK_CODE = 1001;
    private static final int PICK_TREE_CODE = 1002;

    // 预览页直接读取当前已解析的小说
    public static NovelResult currentNovel;

    private EditText etUrl;
    private Button btnParse, btnExport, btnCancel, btnOpenFile;
    private android.view.View btnLog, btnChooseDir;
    private TextView tvNovelInfo, tvStatus, tvVersion, tvSavePath;
    private ProgressBar progressBar;
    private ListView listView;
    private ChapterAdapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NovelParser parser = new NovelParser();
    private DownloadTask downloadTask;
    private Uri lastExportUri;

    private Uri saveTreeUri; // 用户选择的下载目录

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Logger.init(getFilesDir());
        Logger.i(TAG, "应用启动 v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        etUrl = findViewById(R.id.etUrl);
        btnParse = findViewById(R.id.btnParse);
        btnExport = findViewById(R.id.btnExport);
        btnCancel = findViewById(R.id.btnCancel);
        btnLog = findViewById(R.id.btnLog);
        btnOpenFile = findViewById(R.id.btnOpenFile);
        btnChooseDir = findViewById(R.id.btnChooseDir);
        tvNovelInfo = findViewById(R.id.tvNovelInfo);
        tvStatus = findViewById(R.id.tvStatus);
        tvVersion = findViewById(R.id.tvVersion);
        tvSavePath = findViewById(R.id.tvSavePath);
        progressBar = findViewById(R.id.progressBar);
        listView = findViewById(R.id.listViewChapters);
        adapter = new ChapterAdapter();
        listView.setAdapter(adapter);

        tvVersion.setText("版本 v" + BuildConfig.VERSION_NAME + " (构建 " + BuildConfig.VERSION_CODE + ")");
        TextView tvHeaderVersion = findViewById(R.id.tvHeaderVersion);
        tvHeaderVersion.setText("v" + BuildConfig.VERSION_NAME);
        android.widget.ImageView btnLogTop = findViewById(R.id.btnLogTop);
        btnLogTop.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        etUrl.setText("");
        restoreSaveDir(prefs.getString("save_tree", null));

        btnParse.setOnClickListener(v -> startParse());
        btnExport.setOnClickListener(v -> startExport());
        btnCancel.setOnClickListener(v -> cancelDownload());
        btnLog.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        btnOpenFile.setOnClickListener(v -> openExportedFile());
        btnChooseDir.setOnClickListener(v -> pickSaveDir());

        // 点击目录条目 → 预览正文
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent it = new Intent(this, ChapterPreviewActivity.class);
            it.putExtra("index", position);
            startActivity(it);
        });

        setStateIdle();
    }

    // ---------- 解析 ----------

    private void startParse() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入小说目录页地址", Toast.LENGTH_SHORT).show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("last_url", url).apply();
        Logger.i(TAG, "用户请求解析目录: " + url);
        setStateParsing();
        new Thread(() -> {
            try {
                NovelResult result = parser.parseNovel(url, null);
                handler.post(() -> onParseSuccess(result));
            } catch (Exception e) {
                Logger.e(TAG, "目录解析失败", e);
                handler.post(() -> onParseFail(e.getMessage()));
            }
        }).start();
    }

    private void onParseSuccess(NovelResult result) {
        currentNovel = result;
        adapter.setItems(result.chapters);
        tvNovelInfo.setText("《" + result.title + "》" +
                (result.author.isEmpty() ? "" : "  作者: " + result.author) +
                "  共 " + result.chapters.size() + " 章（点击章节可预览）");
        tvStatus.setText("目录解析完成，可点击章节预览或导出 TXT");
        Logger.i(TAG, "目录解析成功: " + result.chapters.size() + " 章");
        setStateParsed();
    }

    private void onParseFail(String msg) {
        tvStatus.setText("解析失败: " + msg);
        Toast.makeText(this, "解析失败: " + msg, Toast.LENGTH_LONG).show();
        setStateIdle();
    }

    // ---------- 整本导出 ----------

    private void startExport() {
        if (currentNovel == null || currentNovel.chapters.isEmpty()) {
            Toast.makeText(this, "请先解析目录", Toast.LENGTH_SHORT).show();
            return;
        }
        Logger.i(TAG, "用户请求导出《" + currentNovel.title + "》");
        setStateDownloading();
        downloadTask = new DownloadTask();
        downloadTask.setContentRenderer(new WebViewRenderer(this));
        downloadTask.start(currentNovel, new DownloadTask.ProgressListener() {
            @Override
            public void onProgress(int done, int total, String lastTitle) {
                handler.post(() -> {
                    progressBar.setMax(total);
                    progressBar.setProgress(done);
                    tvStatus.setText("下载中 " + done + "/" + total + "  最近: " + lastTitle);
                    adapter.refresh();
                });
            }

            @Override
            public void onComplete(int success, int failed) {
                handler.post(() -> finishExport(success, failed));
            }

            @Override
            public void onError(String msg) {
                handler.post(() -> {
                    tvStatus.setText("下载出错: " + msg);
                    setStateParsed();
                });
            }

            @Override
            public void onLog(String msg) { }
        });
    }

    private void finishExport(int success, int failed) {
        if (failed > 0) {
            tvStatus.setText("正文下载完成（成功 " + success + " 失败 " + failed + "），正在写出 TXT...");
        } else {
            tvStatus.setText("正文下载完成，正在写出 TXT...");
        }
        String fileName = FileExporter.sanitize(
                currentNovel.title.isEmpty() ? "未命名小说" : currentNovel.title) + ".txt";

        if (saveTreeUri != null) {
            // 已选择下载目录：直接写入
            Uri tree = saveTreeUri;
            NovelResult novel = currentNovel;
            new Thread(() -> {
                try {
                    Uri doc = FileExporter.createInTree(this, tree, fileName, FileExporter.buildBookText(novel));
                    lastExportUri = doc;
                    handler.post(() -> {
                        tvStatus.setText("导出完成: " + fileName + "  (位于已选下载目录, 失败 " + failed + " 章)");
                        Toast.makeText(this, "已保存到下载目录: " + fileName, Toast.LENGTH_LONG).show();
                        btnOpenFile.setVisibility(View.VISIBLE);
                        setStateParsed();
                    });
                } catch (Exception e) {
                    Logger.e(TAG, "写入下载目录失败", e);
                    handler.post(() -> {
                        tvStatus.setText("写出失败: " + e.getMessage());
                        setStateParsed();
                    });
                }
            }).start();
        } else {
            // 未选择目录：弹出系统"另存为"，用户自选位置
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, WRITE_BOOK_CODE);
        }
    }

    private void writeBookTo(Uri uri) {
        NovelResult novel = currentNovel;
        new Thread(() -> {
            try {
                FileExporter.writeToUri(this, uri, FileExporter.buildBookText(novel));
                lastExportUri = uri;
                handler.post(() -> {
                    tvStatus.setText("导出完成: " + uri.getLastPathSegment());
                    Toast.makeText(this, "TXT 已保存", Toast.LENGTH_LONG).show();
                    btnOpenFile.setVisibility(View.VISIBLE);
                    setStateParsed();
                });
            } catch (Exception e) {
                Logger.e(TAG, "写出失败", e);
                handler.post(() -> {
                    tvStatus.setText("写出失败: " + e.getMessage());
                    setStateParsed();
                });
            }
        }).start();
    }

    private void cancelDownload() {
        if (downloadTask != null) {
            downloadTask.cancel();
            tvStatus.setText("已取消下载");
            setStateParsed();
        }
    }

    private void openExportedFile() {
        if (lastExportUri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(lastExportUri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "打开 TXT"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 下载目录选择 ----------

    private void pickSaveDir() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_TREE_CODE);
    }

    private void restoreSaveDir(String saved) {
        if (saved == null || saved.isEmpty()) {
            tvSavePath.setText("下载目录: 未选择（导出时另存为 / 默认下载目录）");
            return;
        }
        try {
            saveTreeUri = Uri.parse(saved);
            tvSavePath.setText("下载目录: " + queryDirName(saveTreeUri));
        } catch (Exception e) {
            saveTreeUri = null;
            tvSavePath.setText("下载目录: 未选择");
        }
    }

    private String queryDirName(Uri treeUri) {
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri));
            try (android.database.Cursor c = getContentResolver().query(
                    doc, new String[]{android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    return c.getString(0);
                }
            }
        } catch (Exception ignored) { }
        return treeUri.getLastPathSegment();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null || resultCode != RESULT_OK) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == WRITE_BOOK_CODE) {
            writeBookTo(uri);
        } else if (requestCode == PICK_TREE_CODE) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) { }
            saveTreeUri = uri;
            getPreferences(MODE_PRIVATE).edit().putString("save_tree", uri.toString()).apply();
            tvSavePath.setText("下载目录: " + queryDirName(uri));
            Toast.makeText(this, "下载目录已设置", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 状态切换 ----------

    private void setStateIdle() {
        btnParse.setEnabled(true);
        btnExport.setEnabled(false);
        btnCancel.setEnabled(false);
        btnOpenFile.setVisibility(View.GONE);
        progressBar.setProgress(0);
    }

    private void setStateParsing() {
        btnParse.setEnabled(false);
        btnExport.setEnabled(false);
        btnCancel.setEnabled(false);
        tvStatus.setText("正在解析目录...");
        tvNovelInfo.setText("");
    }

    private void setStateParsed() {
        btnParse.setEnabled(true);
        btnExport.setEnabled(currentNovel != null);
        btnCancel.setEnabled(false);
    }

    private void setStateDownloading() {
        btnParse.setEnabled(false);
        btnExport.setEnabled(false);
        btnCancel.setEnabled(true);
        btnOpenFile.setVisibility(View.GONE);
    }
}
