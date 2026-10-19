import com.noveldownloader.ChapterInfo;
import com.noveldownloader.NovelParser;
import com.noveldownloader.NovelResult;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** 实测真实站点，结果写入 UTF-8 文件避免控制台编码问题；检测重复段落与页数异常 */
public class TestReal {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        int probe = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        String tag = args.length > 2 ? args[2] : "real";
        try (PrintWriter w = new PrintWriter("d:/网页小说下载器/parser-test/real_" + tag + ".txt", StandardCharsets.UTF_8)) {
            NovelParser p = new NovelParser();
            long t0 = System.currentTimeMillis();
            NovelResult r = p.parseNovel(url, null);
            w.println("TITLE=" + r.title);
            w.println("AUTHOR=" + r.author);
            w.println("CHAPTERS=" + r.chapters.size());
            w.println("TOC_MS=" + (System.currentTimeMillis() - t0));
            for (int i = 0; i < Math.min(3, r.chapters.size()); i++)
                w.println("  HEAD[" + i + "] " + r.chapters.get(i).title);
            int n = r.chapters.size();
            for (int i = Math.max(0, n - 3); i < n; i++)
                w.println("  TAIL[" + i + "] " + r.chapters.get(i).title);
            java.util.Set<String> seen = new java.util.HashSet<>();
            int dup = 0;
            for (ChapterInfo c : r.chapters) if (!seen.add(c.url)) dup++;
            w.println("DUP_CHAPTER_URLS=" + dup);

            if (n == 0) { w.println("DONE"); return; }
            int[] idxs = {0, 1, n / 2, n - 2, n - 1};
            int limit = Math.min(probe, idxs.length);
            for (int k = 0; k < limit; k++) {
                ChapterInfo c = r.chapters.get(idxs[k]);
                long t1 = System.currentTimeMillis();
                String content;
                try {
                    content = p.fetchChapterContent(c, url, null);
                } catch (Exception e) {
                    w.println("=== CH[" + idxs[k] + "] " + c.title + " FETCH_FAIL: " + e.getMessage() + " ===");
                    continue;
                }
                w.println("=== CH[" + idxs[k] + "] " + c.title + " LEN=" + content.length()
                        + " MS=" + (System.currentTimeMillis() - t1) + " URL=" + c.url + " ===");
                // 重复段落检测：>=20字的行出现次数
                Map<String, Integer> cnt = new HashMap<>();
                for (String line : content.split("\n")) {
                    String t = line.trim();
                    if (t.length() >= 20) cnt.merge(t, 1, Integer::sum);
                }
                int dupLines = 0;
                for (Map.Entry<String, Integer> e : cnt.entrySet()) {
                    if (e.getValue() >= 2) {
                        dupLines++;
                        if (dupLines <= 3) {
                            String s = e.getKey();
                            w.println("  DUP_LINE x" + e.getValue() + ": "
                                    + s.substring(0, Math.min(50, s.length())));
                        }
                    }
                }
                w.println("  DUP_LINES=" + dupLines);
                String head = content.substring(0, Math.min(60, content.length())).replace('\n', ' ');
                String tail = content.substring(Math.max(0, content.length() - 60)).replace('\n', ' ');
                w.println("  HEAD: " + head);
                w.println("  TAIL: " + tail);
                Files.write(Paths.get("d:/网页小说下载器/parser-test/real_" + tag + "_ch" + idxs[k] + ".txt"),
                        content.getBytes(StandardCharsets.UTF_8));
            }
            w.println("DONE");
        }
    }
}
