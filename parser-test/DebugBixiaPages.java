package com.noveldownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 逐页 dump bixia002 第1章各分页文本，对比重叠结构 */
public class DebugBixiaPages {
    public static void main(String[] args) throws Exception {
        try (PrintWriter w = new PrintWriter("d:/网页小说下载器/parser-test/debug_bixia_pages.txt", StandardCharsets.UTF_8)) {
            NovelParser p = new NovelParser();
            String base = "https://www.bixia002.com/xs/167218/";
            String[] pages = {"70633036.html", "70633036_2.html", "70633036_3.html"};
            String prevSq = null;
            for (String pg : pages) {
                Document doc = Jsoup.connect(base + pg)
                        .userAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .timeout(20000).maxBodySize(0).get();
                Element content = null;
                // 复用包级方法找正文
                java.lang.reflect.Method m = NovelParser.class.getDeclaredMethod("findContentElement", Document.class);
                m.setAccessible(true);
                content = (Element) m.invoke(p, doc);
                String text = content == null ? "" : p.extractText(content);
                String sq = squeeze(text);
                w.println("===== " + pg + " rawlen=" + text.length() + " sqlen=" + sq.length());
                w.println("HEAD120: " + sq.substring(0, Math.min(120, sq.length())));
                w.println("TAIL120: " + sq.substring(Math.max(0, sq.length() - 120)));
                if (prevSq != null) {
                    w.println("CONTAINS_PREV=" + sq.contains(prevSq));
                    // 找 prev 在 cur 中的位置
                    w.println("PREV_HEAD_AT=" + sq.indexOf(prevSq.substring(0, Math.min(40, prevSq.length()))));
                }
                prevSq = sq;
            }
            w.println("DONE");
        }
    }

    private static String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}
