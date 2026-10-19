import com.noveldownloader.ChapterInfo;
import com.noveldownloader.Logger;
import com.noveldownloader.NovelParser;
import com.noveldownloader.NovelResult;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 调试 quanben5 目录排序：输出解析日志与章节顺序样本 */
public class DebugQbw {
    public static void main(String[] args) throws Exception {
        try (PrintWriter w = new PrintWriter("d:/网页小说下载器/parser-test/debug_qbw.txt", StandardCharsets.UTF_8)) {
            NovelParser p = new NovelParser();
            NovelResult r = p.parseNovel("https://www.quanben5.com/n/jueshiwushen/xiaoshuo.html", null);
            w.println("CHAPTERS=" + r.chapters.size());
            int n = r.chapters.size();
            for (int i = 0; i < Math.min(10, n); i++)
                w.println("H[" + i + "] " + r.chapters.get(i).title);
            for (int i = Math.max(0, n - 10); i < n; i++)
                w.println("T[" + i + "] " + r.chapters.get(i).title);
            // 找出所有不含“章”字的标题样本
            int noZhang = 0;
            for (ChapterInfo c : r.chapters) {
                if (!c.title.contains("章")) {
                    if (noZhang < 10) w.println("NOZHANG: " + c.title);
                    noZhang++;
                }
            }
            w.println("NOZHANG_TOTAL=" + noZhang);
            for (String l : Logger.getAll()) {
                if (l.contains("排序") || l.contains("编号") || l.contains("目录") || l.contains("分卷"))
                    w.println("LOG: " + l);
            }
            w.println("DONE");
        }
    }
}
