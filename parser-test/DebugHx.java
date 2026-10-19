package com.noveldownloader;

/** 打印 hongxiu 目录章节号序列中的所有回落点，定位分卷误判原因 */
public class DebugHx {
    public static void main(String[] args) throws Exception {
        NovelParser p = new NovelParser();
        // 直接抓目录页 HTML，走 extractChapterLinks 同款流程太繁琐；
        // 改为 parseNovel 后用标题反推不可行（顺序已变），所以复制 parseNovel 的抓取再手动分析
        java.lang.reflect.Method fetch = NovelParser.class.getDeclaredMethod("fetch", String.class);
        fetch.setAccessible(true);
        org.jsoup.nodes.Document doc = (org.jsoup.nodes.Document) fetch.invoke(p, "https://www.hongxiu.com/bookquery/peqbbprh");
        java.lang.reflect.Method ext = NovelParser.class.getDeclaredMethod("extractChapterLinks", org.jsoup.nodes.Document.class, String.class);
        ext.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<ChapterInfo> list = (java.util.List<ChapterInfo>) ext.invoke(p, doc, "https://www.hongxiu.com/bookquery/peqbbprh");
        java.lang.reflect.Method ded = NovelParser.class.getDeclaredMethod("dedupeChapters", java.util.List.class);
        ded.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<ChapterInfo> chapters = (java.util.List<ChapterInfo>) ded.invoke(null, list);
        System.out.println("SIZE=" + chapters.size());
        long prev = -1;
        int runLen = 0;
        for (int i = 0; i < chapters.size(); i++) {
            long k = NovelParser.chapterNum(chapters.get(i).title);
            if (k < 0) continue;
            if (prev >= 0 && k < prev) {
                System.out.println("DROP at idx=" + i + " prev=" + prev + " cur=" + k + " runLen=" + runLen);
                System.out.println("  prev title: " + chapters.get(i - 1).title);
                System.out.println("  cur  title: " + chapters.get(i).title);
            }
            runLen++;
            prev = k;
        }
        System.out.println("DONE");
    }
}
