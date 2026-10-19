package com.noveldownloader;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发下载全部章节正文。
 * - 取消后立即停止进度刷新，已抓取内容保留
 * - 已有正文的章节自动跳过（取消后再次导出可断点续传）
 */
public class DownloadTask {

    private static final String TAG = "DownloadTask";
    private static final int THREADS = 3;

    public interface ProgressListener {
        void onProgress(int done, int total, String lastTitle);
        void onComplete(int success, int failed);
        void onError(String msg);
        void onLog(String msg);
    }

    private final NovelParser parser = new NovelParser();
    private final NovelParser.CancelToken token = new NovelParser.CancelToken();
    private ExecutorService pool;

    /** 正文由脚本动态填充的站点需要浏览器内核兜底（由界面层注入 WebView 实现） */
    public void setContentRenderer(NovelParser.ContentRenderer renderer) {
        parser.setContentRenderer(renderer);
    }

    public void cancel() {
        token.cancelled = true;
        if (pool != null) pool.shutdownNow();
        Logger.i(TAG, "下载任务已取消，已抓取章节保留");
    }

    public void start(NovelResult novel, ProgressListener listener) {
        List<ChapterInfo> chapters = novel.chapters;
        int total = chapters.size();
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        Logger.i(TAG, "开始下载《" + novel.title + "》 共 " + total + " 章, 线程数 " + THREADS);
        pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < total; i++) {
            final int idx = i;
            final ChapterInfo ch = chapters.get(i);
            pool.execute(() -> {
                if (token.cancelled) return;
                try {
                    if (ch.content != null && !ch.content.isEmpty()) {
                        // 已有内容（上次下载/取消保留），跳过
                        success.incrementAndGet();
                    } else {
                        ch.content = parser.fetchChapterContent(ch, null, token);
                        if (ch.content == null || ch.content.isEmpty()) {
                            failed.incrementAndGet();
                            Logger.e(TAG, "第" + (idx + 1) + "章《" + ch.title + "》内容为空");
                        } else {
                            success.incrementAndGet();
                        }
                    }
                } catch (IOException e) {
                    failed.incrementAndGet();
                    ch.content = "";
                    Logger.e(TAG, "第" + (idx + 1) + "章《" + ch.title + "》下载失败: " + e.getMessage());
                }
                if (token.cancelled) return; // 取消后不再刷新进度
                int d = done.incrementAndGet();
                if (listener != null) listener.onProgress(d, total, ch.title);
                if (d == total && listener != null) {
                    listener.onComplete(success.get(), failed.get());
                }
            });
        }
        pool.shutdown();
    }
}
