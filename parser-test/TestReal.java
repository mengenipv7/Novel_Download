import com.noveldownloader.ChapterInfo;
import com.noveldownloader.NovelParser;
import com.noveldownloader.NovelResult;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 实测真实站点，结果写入 UTF-8 文件避免控制台编码问题 */
public class TestReal {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        int probe = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        try (PrintWriter w = new PrintWriter("d:/网页小说下载器/parser-test/real_result.txt", StandardCharsets.UTF_8)) {
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
            w.println("DUPLICATES=" + dup);

            int[] idxs = {0, n / 2, n - 1};
            int limit = Math.min(probe, idxs.length);
            for (int k = 0; k < limit; k++) {
                ChapterInfo c = r.chapters.get(idxs[k]);
                long t1 = System.currentTimeMillis();
                String content = p.fetchChapterContent(c, url, null);
                w.println("=== CH[" + idxs[k] + "] " + c.title + " LEN=" + content.length()
                        + " MS=" + (System.currentTimeMillis() - t1) + " URL=" + c.url + " ===");
                String head = content.substring(0, Math.min(80, content.length())).replace('\n', ' ');
                String tail = content.substring(Math.max(0, content.length() - 80)).replace('\n', ' ');
                w.println("HEAD: " + head);
                w.println("TAIL: " + tail);
            }
            w.println("DONE");
        }
    }
}
