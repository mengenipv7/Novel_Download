package com.noveldownloader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 用系统 WebView 渲染章节页（执行站点脚本）取正文。
 * 用于“浏览器能看、但静态 HTML 里没有正文”的站点（如只返回
 * “请勿开启浏览器阅读模式，否则将导致章节内容缺失”提示页的反爬站）。
 * 必须在后台线程调用，内部自行切到主线程创建/销毁 WebView。
 */
public class WebViewRenderer implements NovelParser.ContentRenderer {

    private static final String TAG = "WebViewRenderer";

    /** onPageFinished 之后再等一会，留给脚本填充正文 */
    private static final long SETTLE_MS = 1200L;

    /** 取文字最多的容器，同时带回整页 HTML（分页链接也由脚本注入的站点需要再从 DOM 找分页） */
    private static final String PICK_PAGE_JS =
            "(function(){var b='';var a=document.querySelectorAll('div,section,article');"
                    + "for(var i=0;i<a.length;i++){var t=a[i].innerText||'';if(t.length>b.length)b=t;}"
                    + "if(!b&&document.body){b=document.body.innerText;}"
                    + "return JSON.stringify({t:b,h:document.documentElement.outerHTML});})()";

    private final Context context;
    private final long timeoutMs;

    public WebViewRenderer(Context context) {
        this(context, 30000L);
    }

    public WebViewRenderer(Context context, long timeoutMs) {
        this.context = context.getApplicationContext();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public NovelParser.ContentRenderer.RenderedPage render(String url) {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> out = new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(() -> open(url, done, out));
        try {
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Logger.e(TAG, "浏览器渲染超时: " + url);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return parsePage(out.get());
    }

    /** JS 返回的是 JSON 字符串：{t: 正文文本, h: 整页 HTML} */
    private static NovelParser.ContentRenderer.RenderedPage parsePage(String json) {
        if (json == null || "null".equals(json)) return null;
        try {
            Object o = new org.json.JSONTokener(json).nextValue();
            if (o instanceof String) {
                o = new org.json.JSONTokener((String) o).nextValue();
            }
            if (o instanceof org.json.JSONObject) {
                org.json.JSONObject obj = (org.json.JSONObject) o;
                String text = obj.isNull("t") ? null : obj.optString("t", null);
                String html = obj.isNull("h") ? null : obj.optString("h", null);
                return new NovelParser.ContentRenderer.RenderedPage(text, html);
            }
        } catch (Exception e) {
            Logger.e(TAG, "解析渲染结果失败", e);
        }
        return null;
    }

    /** 主线程：创建 WebView 并加载页面 */
    private void open(String url, CountDownLatch done, AtomicReference<String> out) {
        final Handler main = new Handler(Looper.getMainLooper());
        final WebView web = new WebView(context);
        try {
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String u) {
                    main.postDelayed(() -> extract(view, done, out), SETTLE_MS);
                }
            });
            web.loadUrl(url);
        } catch (Throwable t) {
            Logger.e(TAG, "浏览器渲染失败: " + url, t);
            destroy(web);
            done.countDown();
            return;
        }
        // 页面加载不出来时也要释放，避免整章卡死
        main.postDelayed(() -> {
            if (done.getCount() > 0) {
                Logger.e(TAG, "浏览器渲染超时: " + url);
                destroy(web);
                done.countDown();
            }
        }, timeoutMs);
    }

    /** 主线程：读取渲染后的正文与整页 HTML 并释放 WebView */
    private void extract(WebView web, CountDownLatch done, AtomicReference<String> out) {
        try {
            web.evaluateJavascript(PICK_PAGE_JS, value -> {
                out.set(value);
                destroy(web);
                done.countDown();
            });
        } catch (Throwable t) {
            Logger.e(TAG, "读取渲染正文失败", t);
            destroy(web);
            done.countDown();
        }
    }

    private static void destroy(WebView web) {
        try {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
        } catch (Throwable ignored) {
        }
    }
}
