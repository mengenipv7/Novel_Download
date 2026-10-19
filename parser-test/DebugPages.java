package com.noveldownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 逐页调试 bixia002 第1章分页（同包以访问包级方法） */
public class DebugPages {
    public static void main(String[] args) throws Exception {
        try (PrintWriter w = new PrintWriter("d:/网页小说下载器/parser-test/debug_result.txt", StandardCharsets.UTF_8)) {
            NovelParser p = new NovelParser();
            String base = "https://www.bixia002.com/xs/167218/";
            String[] pages = {
                    base + "70633036.html",
                    base + "70633036_2.html",
                    base + "70633036_3.html",
                    base + "70633036_4.html"
            };
            for (String url : pages) {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .timeout(20000).maxBodySize(0)
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Referer", base + "70633036.html")
                        .get();
                String next = p.findChapterNextPage(doc, url, null, null);
                w.println("PAGE " + url.substring(url.lastIndexOf('/') + 1) + " -> NEXT=" + next);
            }
            // 直接从第3页开始整章抓取，看内容提取是否正常
            String c3 = p.fetchChapterContent(new ChapterInfo("第1章_从3页开始", pages[2]), null, null);
            w.println("FROM_P3 LEN=" + c3.length() + " HEAD=" + c3.substring(0, Math.min(60, c3.length())).replace('\n', ' '));
            // 整章从头抓
            String c1 = p.fetchChapterContent(new ChapterInfo("第1章", pages[0]), null, null);
            w.println("FULL LEN=" + c1.length());
            w.println("FULL TAIL=" + c1.substring(Math.max(0, c1.length() - 100)).replace('\n', ' '));
            for (String l : Logger.getAll()) w.println("LOG: " + l);
            w.println("DONE");
        }
    }
}
