import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 导出真实目录页 HTML 与链接清单，排查章节缺失 */
public class DebugFetch {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        String out = args[1];
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .timeout(20000).maxBodySize(0)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get();
        java.nio.file.Files.write(java.nio.file.Paths.get(out + ".html"), doc.html().getBytes(StandardCharsets.UTF_8));
        try (PrintWriter w = new PrintWriter(out + ".links.txt", StandardCharsets.UTF_8)) {
            for (Element a : doc.select("a[href]")) {
                w.println(a.absUrl("href") + " | " + a.text().trim());
            }
        }
        System.out.println("saved " + out);
    }
}
