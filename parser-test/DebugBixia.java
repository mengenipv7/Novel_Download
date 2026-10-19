import com.noveldownloader.ChapterInfo;
import com.noveldownloader.Logger;
import com.noveldownloader.NovelParser;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 调试 bixia002 第1章分页合并：输出每页规则命中日志 */
public class DebugBixia {
    public static void main(String[] args) throws Exception {
        try (PrintWriter w = new PrintWriter("d:/网页小说下载器/parser-test/debug_bixia.txt", StandardCharsets.UTF_8)) {
            NovelParser p = new NovelParser();
            String c = p.fetchChapterContent(
                    new ChapterInfo("第1章", "https://www.bixia002.com/xs/167218/70633036.html"),
                    "https://www.bixia002.com/xs/167218/", null);
            w.println("FINAL LEN=" + c.length());
            for (String l : Logger.getAll()) w.println("LOG: " + l);
            w.println("DONE");
        }
    }
}
