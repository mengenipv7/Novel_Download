import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地仿真小说站：
 *  目录: index.html(1-30, 下一页) → index_2.html(31-40, 查看更多章节) → index_3.html(41-45)
 *  章节:
 *    1.html →(下一页) 1_2.html →(下一页) 1_3.html
 *    2.html                          GBK 编码
 *    3.html →(查看全文) 3_2.html →(下一页»带符号) 3_3.html
 *    4.html →(第2页/数字2) 4_2.html
 *    5..45.html                      普通章节
 *  页首含“最新章节”预览块（45 章），需去重。
 */
public class FixtureServer {
    static final int PORT = 18091;
    static final int TOTAL_CHAPTERS = 45;
    static final Map<String, Page> PAGES = new HashMap<>();

    static class Page {
        final byte[] body;
        final String contentType;
        Page(String body, String charset) {
            this.body = body.getBytes(Charset.forName(charset));
            this.contentType = "text/html; charset=" + charset;
        }
    }

    public static void main(String[] args) throws IOException {
        buildToc();
        buildChapter1();
        PAGES.put("/book/1/2.html", new Page(chapter2Gbk(), "gbk"));
        buildChapter3();
        buildChapter4();
        for (int n = 5; n <= TOTAL_CHAPTERS; n++) {
            PAGES.put("/book/1/" + n + ".html", new Page(genericChapter(n), "utf-8"));
        }
        buildDupChapters();
        buildOverlapChapter();
        buildHeaderChapter();
        buildDirStyleChapter();
        buildCumulativeChapter();
        buildSite2();
        buildSite3();
        buildSite4();
        buildSite5();
        buildSite6();
        buildSite7();
        buildSite8();
        buildSite9();
        buildSite10();
        buildSite11();
        buildSite12();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Page page = PAGES.get(path);
            if (page == null) {
                byte[] nf = "<html><body>404</body></html>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, nf.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(nf); }
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", page.contentType);
            exchange.sendResponseHeaders(200, page.body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(page.body); }
            exchange.close();
        });
        server.start();
        System.out.println("FixtureServer started on " + PORT);
    }

    static String filler(String label, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(label).append("第").append(i + 1).append("段，这是一段用来凑足长度的正文内容。");
        }
        return sb.toString();
    }

    static String tocPage(String dt, int from, int to, String pager) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>测试修仙传最新章节</title></head><body>");
        sb.append("<div id=\"info\"><h1>测试修仙传</h1><p>作者：测试作者</p></div>");
        sb.append("<div id=\"list\"><dl><dt>最新章节</dt>");
        for (int i = TOTAL_CHAPTERS - 2; i <= TOTAL_CHAPTERS; i++) {
            sb.append("<dd><a href=\"/book/1/").append(i).append(".html\">第").append(i).append("章 预览标题</a></dd>");
        }
        sb.append("<dt>").append(dt).append("</dt>");
        for (int i = from; i <= to; i++) {
            sb.append("<dd><a href=\"/book/1/").append(i).append(".html\">第").append(i).append("章 正文</a></dd>");
        }
        sb.append("</dl></div><div class=\"page\">").append(pager).append("</div></body></html>");
        return sb.toString();
    }

    static void buildToc() {
        PAGES.put("/book/1/index.html", new Page(
                tocPage("正文卷", 1, 30,
                        "<a href=\"/book/1/index.html\">上一页</a> <a href=\"/book/1/index_2.html\">下一页</a>"), "utf-8"));
        PAGES.put("/book/1/index_2.html", new Page(
                tocPage("正文卷(续)", 31, 40,
                        "<a href=\"/book/1/index.html\">上一页</a> <a href=\"/book/1/index_3.html\">查看更多章节</a>"), "utf-8"));
        PAGES.put("/book/1/index_3.html", new Page(
                tocPage("正文卷(完)", 41, 45,
                        "<a href=\"/book/1/index_2.html\">上一页</a>"), "utf-8"));
    }

    static void putChapter(String url, String title, String paragraphs, String nav, String charset) {
        String head = title == null ? "" : "<div class=\"bookname\"><h1>" + title + "</h1></div>";
        String html = "<html><head><meta charset=\"" + charset + "\"><title>" + (title == null ? "" : title) + "</title></head><body>"
                + head
                + "<div id=\"content\">" + paragraphs + "</div>"
                + "<div class=\"page\">" + nav + " <a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        PAGES.put(url, new Page(html, charset));
    }

    static void buildChapter1() {
        putChapter("/book/1/1.html", "第1章 正文",
                "<p>第一章内容第一部分。" + filler("少年张三站在山门前，", 6) + "</p><p>请记住本书首发域名：www.example.com</p>",
                "<a href=\"/book/1/1_2.html\">下一页</a>", "utf-8");
        putChapter("/book/1/1_2.html", "第1章 正文",
                "<p>第一章内容第二部分。" + filler("他深吸一口气，迈出了第一步。", 6) + "</p>",
                "<a href=\"/book/1/1_3.html\">下一页</a>", "utf-8");
        // 1_3 页无 <h1> 标题（模拟 bixia002 分页页），验证不会误判为下一章
        putChapter("/book/1/1_3.html", null,
                "<p>第一章内容第三部分。钟声响起，考核开始。" + filler("众人屏息凝神。", 5) + "</p><p>（本章完）</p>",
                "", "utf-8");
    }

    static String chapter2Gbk() {
        return "<html><head><meta charset=\"gbk\"><title>第2章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第2章 正文</h1></div>"
                + "<div id=\"content\"><p>第二章内容，GBK编码测试。李四在集市买了一把剑。" + filler("剑身泛着寒光，十分锋利。", 6) + "</p></div>"
                + "<div class=\"page\"><a href=\"/book/1/1.html\">上一章</a> <a href=\"/book/1/3.html\">下一章</a></div>"
                + "</body></html>";
    }

    static void buildChapter3() {
        putChapter("/book/1/3.html", "第3章 正文",
                "<p>第三章内容第一部分。" + filler("夜色渐深，灯火摇曳。", 6) + "</p>",
                "<a href=\"/book/1/3_2.html\">查看全文</a>", "utf-8");
        putChapter("/book/1/3_2.html", "第3章 正文",
                "<p>第三章内容第二部分。" + filler("他推门而入，四下无人。", 6) + "</p>",
                "<a href=\"/book/1/3_3.html\">下一页»</a>", "utf-8");
        putChapter("/book/1/3_3.html", "第3章 正文",
                "<p>第三章内容第三部分。" + filler("真相即将水落石出。", 5) + "</p><p>本章未完，点击下一页继续阅读</p>",
                "", "utf-8");
    }

    static void buildChapter4() {
        putChapter("/book/1/4.html", "第4章 正文",
                "<p>第四章内容第一部分。" + filler("山雨欲来风满楼。", 6) + "</p>",
                "<a href=\"/book/1/4_2.html\">第2页</a> <a href=\"/book/1/4_2.html\">2</a>", "utf-8");
        putChapter("/book/1/4_2.html", "第4章 正文",
                "<p>第四章内容第二部分。" + filler("他握紧了手中的剑。", 6) + "</p><p>3/2</p>",
                "<a href=\"/book/1/4.html\">第1页</a>", "utf-8");
    }

    static String genericChapter(int n) {
        return "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第" + n + "章 正文</h1></div>"
                + "<div id=\"content\"><p>第" + n + "章的正文内容。" + filler("这里是一些测试文字。", 6) + "</p></div>"
                + "<div class=\"page\"><a href=\"/book/1/" + (n - 1) + ".html\">上一章</a>"
                + " <a href=\"/book/1/" + (n + 1) + ".html\">下一章</a></div>"
                + "</body></html>";
    }

    // ===== 第6/7章：重复页防护（6_2=整页复制；7_2=开头插广告的复制） =====
    static void buildDupChapters() {
        String ch6 = "<html><head><meta charset=\"utf-8\"><title>第6章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第6章 正文</h1></div>"
                + "<div id=\"content\"><p>第六章内容第一部分。" + filler("山风吹过竹林。", 6) + "</p></div>"
                + "<div class=\"page\"><a href=\"/book/1/6_2.html\">下一页</a> <a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        PAGES.put("/book/1/6.html", new Page(ch6, "utf-8"));
        PAGES.put("/book/1/6_2.html", new Page(ch6, "utf-8"));

        String ch7Body = "<p>第七章内容第一部分。" + filler("他抬头看天。", 6) + "</p>";
        String ch7 = "<html><head><meta charset=\"utf-8\"><title>第7章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第7章 正文</h1></div>"
                + "<div id=\"content\">" + ch7Body + "</div>"
                + "<div class=\"page\"><a href=\"/book/1/7_2.html\">下一页</a> <a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        String ch7Dup = "<html><head><meta charset=\"utf-8\"><title>第7章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第7章 正文</h1></div>"
                + "<div id=\"content\"><p>广告：点击领取今日福利。</p>" + ch7Body + "</div>"
                + "<div class=\"page\"><a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        PAGES.put("/book/1/7.html", new Page(ch7, "utf-8"));
        PAGES.put("/book/1/7_2.html", new Page(ch7Dup, "utf-8"));
    }

    // ===== 第8章：分页重叠（8_2 开头重复 8 结尾一整句） =====
    static final String OVERLAP_SENTENCE = "山道尽头云雾散去，少年回头望了一眼远处的城池。";

    static void buildOverlapChapter() {
        String html = "<html><head><meta charset=\"utf-8\"><title>第8章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第8章 正文</h1></div>"
                + "<div id=\"content\"><p>第八章内容第一部分。" + filler("风从谷底吹上来。", 6) + "</p>"
                + "<p>" + OVERLAP_SENTENCE + "</p></div>"
                + "<div class=\"page\"><a href=\"/book/1/8_2.html\">下一页</a> <a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        String html2 = "<html><head><meta charset=\"utf-8\"><title>第8章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第8章 正文</h1></div>"
                + "<div id=\"content\"><p>" + OVERLAP_SENTENCE + "第八章内容第二部分。" + filler("灯火在远处明灭。", 6) + "</p></div>"
                + "<div class=\"page\"><a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        PAGES.put("/book/1/8.html", new Page(html, "utf-8"));
        PAGES.put("/book/1/8_2.html", new Page(html2, "utf-8"));
    }

    // ===== 第9章：每页正文带相同的长页眉（≥60字），旧逻辑会误判重复页丢失后续页 =====
    static final String PAGE_HEADER =
            "本书《测试修仙传》由www.testxiaoshuo.com为您提供优质小说阅读服务，内容由网友上传，"
                    + "如有侵权请联系我们删除，祝您阅读愉快，请在阅读之后帮忙宣传推荐。";

    static void buildHeaderChapter() {
        String page = String.format(
                "<html><head><meta charset=\"utf-8\"><title>第9章 正文</title></head><body>"
                        + "<div class=\"bookname\"><h1>第9章 正文</h1></div>"
                        + "<div id=\"content\"><p>%s</p><p>%s</p></div>"
                        + "<div class=\"page\">%s <a href=\"/book/1/index.html\">目录</a></div>"
                        + "</body></html>", PAGE_HEADER, "%s", "%s");
        PAGES.put("/book/1/9.html", new Page(String.format(page,
                "第九章内容第一部分。" + filler("云海翻涌如潮。", 6),
                "<a href=\"/book/1/9_2.html\">下一页</a>"), "utf-8"));
        PAGES.put("/book/1/9_2.html", new Page(String.format(page,
                "第九章内容第二部分。" + filler("剑意直上云霄。", 6),
                ""), "utf-8"));
    }

    // ===== 第10章：目录式分页 10.html → 10/2.html → 10/3.html =====
    static void buildDirStyleChapter() {
        String page = "<html><head><meta charset=\"utf-8\"><title>第10章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第10章 正文</h1></div>"
                + "<div id=\"content\"><p>%s</p></div>"
                + "<div class=\"page\">%s <a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        PAGES.put("/book/1/10.html", new Page(String.format(page,
                "第十章内容第一部分。" + filler("雷声滚滚而来。", 6),
                "<a href=\"/book/1/10/2.html\">下一页</a>"), "utf-8"));
        PAGES.put("/book/1/10/2.html", new Page(String.format(page,
                "第十章内容第二部分。" + filler("大雨倾盆而下。", 6),
                "<a href=\"/book/1/10/3.html\">下一页</a>"), "utf-8"));
        PAGES.put("/book/1/10/3.html", new Page(String.format(page,
                "第十章内容第三部分。" + filler("雨停后长虹贯日。", 5),
                ""), "utf-8"));
    }

    // ===== 第11章：累积式分页（11_2 完整包含 11 内容+新增段） =====
    static void buildCumulativeChapter() {
        String part1 = "<p>第十一章内容第一部分。" + filler("掌门宣布闭山大典。", 6) + "</p>";
        String html = "<html><head><meta charset=\"utf-8\"><title>第11章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第11章 正文</h1></div>"
                + "<div id=\"content\">" + part1 + "</div>"
                + "<div class=\"page\"><a href=\"/book/1/11_2.html\">下一页</a> <a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        String html2 = "<html><head><meta charset=\"utf-8\"><title>第11章 正文</title></head><body>"
                + "<div class=\"bookname\"><h1>第11章 正文</h1></div>"
                + "<div id=\"content\">" + part1
                + "<p>第十一章内容第二部分。" + filler("大典定在三日后。", 6) + "</p></div>"
                + "<div class=\"page\"><a href=\"/book/1/index.html\">目录</a></div>"
                + "</body></html>";
        PAGES.put("/book/1/11.html", new Page(html, "utf-8"));
        PAGES.put("/book/1/11_2.html", new Page(html2, "utf-8"));
    }

    // ===== 站点3：目录乱序 + 同名重复章节（不同URL） =====
    static void buildSite3() {
        int[] order = {3, 1, 4, 2, 7, 5, 8, 6, 11, 9, 12, 10};
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>星海征途</title></head><body><h1>星海征途</h1>");
        for (int n : order) {
            sb.append("<div><a href=\"/site3/html/c").append(n).append(".html\">第").append(n).append("章 启航</a></div>");
        }
        sb.append("<div><a href=\"/site3/html/c6b.html\">第6章 启航</a></div>");
        sb.append("</body></html>");
        PAGES.put("/site3/book/77/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 12; n++) {
            String html = "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 启航</title></head><body>"
                    + "<div id=\"content\"><p>第" + n + "章内容。" + filler("星光洒满甲板。", 20) + "</p></div>"
                    + "</body></html>";
            PAGES.put("/site3/html/c" + n + ".html", new Page(html, "utf-8"));
        }
        PAGES.put("/site3/html/c6b.html", new Page(
                "<html><body><div id=\"content\"><p>第6章重复页内容。" + filler("星光洒满甲板。", 20) + "</p></div></body></html>",
                "utf-8"));
    }

    // ===== 站点2：章节路径与目录不同目录 + section 正文容器 + 推荐链接污染 =====
    static void buildSite2() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>都市重生录</title></head><body>");
        sb.append("<h1>都市重生录</h1><p>作者：张三丰</p>");
        sb.append("<div class=\"rec\">")
          .append("<a href=\"/site2/book/3/1.html\">推荐：隔壁老王传</a>")
          .append("<a href=\"/site2/book/5/2.html\">推荐：天下第一</a></div>");
        for (int n = 1; n <= 8; n++) {
            sb.append("<div class=\"item\"><a href=\"/site2/html/9/chap").append(n)
              .append(".html\">第").append(n).append("章 重生</a></div>");
        }
        sb.append("</body></html>");
        PAGES.put("/site2/book/9/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 8; n++) {
            String html = "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 重生</title></head><body>"
                    + "<h2>第" + n + "章 重生</h2>"
                    + "<section id=\"chapter_content\"><p>重生第" + n + "章内容。"
                    + filler("他睁开了眼睛。", 20) + "</p></section>"
                    + "<div class=\"nav\"><a href=\"/site2/html/9/chap" + Math.min(n + 1, 8)
                    + ".html\">下一章</a></div>"
                    + "</body></html>";
            PAGES.put("/site2/html/9/chap" + n + ".html", new Page(html, "utf-8"));
        }
    }

    // ===== 站点4：目录基本递增但个别章节位置错乱（旧逻辑倒序比例低→不排序，仍显示网页顺序） =====
    static void buildSite4() {
        int[] order = {1, 2, 3, 4, 5, 6, 7, 8, 10, 9, 11, 12};
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>星河彼岸</title></head><body><h1>星河彼岸</h1>");
        for (int n : order) {
            sb.append("<div><a href=\"/site4/html/c").append(n).append(".html\">第").append(n).append("章 远航</a></div>");
        }
        sb.append("</body></html>");
        PAGES.put("/site4/book/88/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 12; n++) {
            String html = "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 远航</title></head><body>"
                    + "<div id=\"content\"><p>第" + n + "章内容。" + filler("星河璀璨。", 20) + "</p></div>"
                    + "</body></html>";
            PAGES.put("/site4/html/c" + n + ".html", new Page(html, "utf-8"));
        }
    }

    // ===== 站点5：分卷重新编号（第一卷1-12、第二卷又1-12），网页顺序即正确顺序，禁止重排交错 =====
    static void buildSite5() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>双卷传奇</title></head><body><h1>双卷传奇</h1>");
        for (int n = 1; n <= 12; n++) {
            sb.append("<div><a href=\"/site5/html/v1c").append(n).append(".html\">第一卷 第")
              .append(n).append("章 启程</a></div>");
        }
        for (int n = 1; n <= 12; n++) {
            sb.append("<div><a href=\"/site5/html/v2c").append(n).append(".html\">第二卷 第")
              .append(n).append("章 归途</a></div>");
        }
        sb.append("</body></html>");
        PAGES.put("/site5/book/55/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 12; n++) {
            PAGES.put("/site5/html/v1c" + n + ".html", new Page(volChapter("第一卷", n, "启程"), "utf-8"));
            PAGES.put("/site5/html/v2c" + n + ".html", new Page(volChapter("第二卷", n, "归途"), "utf-8"));
        }
    }

    // ===== 站点6：目录分页被轮转打乱（第13-20章在前、第1-12章在后），
    // 章节号全局不重复（旧逻辑误判为分卷重新编号→不重排→仍显示乱序） =====
    static void buildSite6() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>轮回书屋</title></head><body><h1>轮回书屋</h1>");
        for (int n = 13; n <= 20; n++) {
            sb.append("<div><a href=\"/site6/html/c").append(n).append(".html\">第")
              .append(n).append("章 渡口</a></div>");
        }
        for (int n = 1; n <= 12; n++) {
            sb.append("<div><a href=\"/site6/html/c").append(n).append(".html\">第")
              .append(n).append("章 渡口</a></div>");
        }
        sb.append("</body></html>");
        PAGES.put("/site6/book/66/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 20; n++) {
            String html = "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 渡口</title></head><body>"
                    + "<div id=\"content\"><p>第" + n + "章内容。" + filler("江风起。", 20) + "</p></div>"
                    + "</body></html>";
            PAGES.put("/site6/html/c" + n + ".html", new Page(html, "utf-8"));
        }
    }

    // ===== 站点7：静态页只有“阅读模式”提示（正文由脚本注入），需浏览器渲染兜底 =====
    static final String MODE_HINT = "请勿开启浏览器阅读模式，否则将导致章节内容缺失及无法阅读下一章。";

    static void buildSite7() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"><title>渡口风云</title></head><body><h1>渡口风云</h1>");
        for (int n = 1; n <= 2; n++) {
            sb.append("<div><a href=\"/site7/html/c").append(n).append(".html\">第")
              .append(n).append("章 渡口</a></div>");
        }
        sb.append("</body></html>");
        PAGES.put("/site7/book/77/index.html", new Page(sb.toString(), "utf-8"));

        // 第1章：提示语很短，正文容器检测不到（旧逻辑直接抛“未找到正文内容”）
        PAGES.put("/site7/html/c1.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 渡口</title></head><body>"
                        + "<h1>第1章 渡口</h1>"
                        + "<div id=\"content\"><p>" + MODE_HINT + "</p></div>"
                        + "</body></html>", "utf-8"));

        // 第2章：提示语凑够长度被当成正文容器，清洗后正文为空
        StringBuilder c2 = new StringBuilder("<html><head><meta charset=\"utf-8\"><title>第2章 渡口</title>")
                .append("</head><body><h1>第2章 渡口</h1><div id=\"content\">");
        for (int i = 0; i < 4; i++) c2.append("<p>").append(MODE_HINT).append("</p>");
        c2.append("</div></body></html>");
        PAGES.put("/site7/html/c2.html", new Page(c2.toString(), "utf-8"));
    }

    // ===== 站点8：桩站且分页链接也由脚本注入（静态页连“下一页”都没有），
    //             需从渲染后的 DOM 补找分页链接并拼接多页 =====
    static void buildSite8() {
        PAGES.put("/site8/book/88/index.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>渡口晨昏</title></head><body><h1>渡口晨昏</h1>"
                        + "<div><a href=\"/site8/html/p1.html\">第1章 双页</a></div>"
                        + "</body></html>", "utf-8"));

        // 两页静态都只有提示语，正文与分页链接均靠渲染注入
        PAGES.put("/site8/html/p1.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 双页</title></head><body>"
                        + "<h1>第1章 双页</h1><div id=\"content\"><p>" + MODE_HINT + "</p></div>"
                        + "</body></html>", "utf-8"));
        PAGES.put("/site8/html/p1_2.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 双页</title></head><body>"
                        + "<h1>第1章 双页</h1><div id=\"content\"><p>" + MODE_HINT + "</p></div>"
                        + "</body></html>", "utf-8"));
    }

    // ===== 站点9：回顾式分页（bixia002 模式） =====
    // 第1页 = 正文A1-A8 + 尾部垃圾重复A1-A2；第2页 = 回顾A3-A8 + 新内容B1-B4；第3页 = 全新C1-C3
    static void buildSite9() {
        StringBuilder toc = new StringBuilder("<html><head><meta charset=\"utf-8\"><title>渡口回顾</title></head><body><h1>渡口回顾</h1>");
        toc.append("<div><a href=\"/site9/html/r1.html\">第1章 回顾</a></div></body></html>");
        PAGES.put("/site9/book/99/index.html", new Page(toc.toString(), "utf-8"));

        StringBuilder p1 = new StringBuilder();
        for (int i = 1; i <= 8; i++)
            p1.append("<p>甲").append(i).append("段，渡口的风从江面吹过来，吹得棚口的灯笼直晃。</p>");
        for (int i = 1; i <= 2; i++) // 尾部垃圾重复段
            p1.append("<p>甲").append(i).append("段，渡口的风从江面吹过来，吹得棚口的灯笼直晃。</p>");
        PAGES.put("/site9/html/r1.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 回顾</title></head><body><h1>第1章 回顾</h1>"
                        + "<div id=\"content\">" + p1 + "</div>"
                        + "<div class=\"page\"><a href=\"/site9/html/r1_2.html\">下一页</a></div></body></html>", "utf-8"));

        StringBuilder p2 = new StringBuilder();
        for (int i = 3; i <= 8; i++) // 大段回顾上一页
            p2.append("<p>甲").append(i).append("段，渡口的风从江面吹过来，吹得棚口的灯笼直晃。</p>");
        for (int i = 1; i <= 4; i++)
            p2.append("<p>乙").append(i).append("段，他把船撑离渡口，晨雾还没有散尽。</p>");
        PAGES.put("/site9/html/r1_2.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 回顾</title></head><body><h1>第1章 回顾</h1>"
                        + "<div id=\"content\">" + p2 + "</div>"
                        + "<div class=\"page\"><a href=\"/site9/html/r1_3.html\">下一页</a></div></body></html>", "utf-8"));

        StringBuilder p3 = new StringBuilder();
        for (int i = 1; i <= 3; i++)
            p3.append("<p>丙").append(i).append("段，午后的日头照在水面上，碎成一片金箔，他把缆绳收紧，小船轻轻靠上了对岸的码头。</p>");
        PAGES.put("/site9/html/r1_3.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 回顾</title></head><body><h1>第1章 回顾</h1>"
                        + "<div id=\"content\">" + p3 + "</div>"
                        + "<div class=\"page\"><a href=\"/site9/book/99/index.html\">目录</a></div></body></html>", "utf-8"));
    }

    // ===== 站点10：分类导航 dl/dd 块干扰章节容器识别（hongxiu 模式） =====
    // 命名容器选择器只命中 8 条分类导航，真正章节列表在未命名容器里，必须靠全页扫描纠正
    static void buildSite10() {
        String[] cats = {"现代言情", "古代言情", "浪漫青春", "玄幻奇幻", "武侠仙侠", "科幻灵异", "游戏竞技", "短篇小说"};
        StringBuilder sb = new StringBuilder("<html><head><meta charset=\"utf-8\"><title>渡口迷航</title></head><body>");
        sb.append("<div id=\"info\"><h1>渡口迷航</h1><p>作者：测试</p></div>");
        sb.append("<div class=\"type-list\"><dl>");
        for (int i = 0; i < cats.length; i++)
            sb.append("<dd><a href=\"/site10/category/cat_").append(i).append("_f1\">").append(cats[i]).append("</a></dd>");
        sb.append("</dl></div>");
        sb.append("<div class=\"book-mulu\"><ul>");
        for (int i = 1; i <= 12; i++)
            sb.append("<li><a href=\"/site10/html/").append(i).append(".html\">第").append(i).append("章 迷航</a></li>");
        sb.append("</ul></div></body></html>");
        PAGES.put("/site10/book/100/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 12; n++) {
            PAGES.put("/site10/html/" + n + ".html", new Page(
                    "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 迷航</title></head><body>"
                            + "<div id=\"content\"><p>第" + n + "章的正文内容。" + filler("江面上的雾越来越浓。", 6) + "</p></div>"
                            + "</body></html>", "utf-8"));
        }
    }

    // ===== 站点11：目录式章节URL（末尾斜杠）+ 页脚杂链（hongxiu 模式） =====
    // 旧 dirOf 对末尾斜杠会取到全路径，每章各成一组 → 分组失效 → 页脚 .html 杂链混入章节
    static void buildSite11() {
        StringBuilder sb = new StringBuilder("<html><head><meta charset=\"utf-8\"><title>斜杠目录站</title></head><body>");
        sb.append("<div id=\"info\"><h1>雾中渡口</h1><p>作者：测试</p></div>");
        sb.append("<div class=\"book-mulu2\"><ul>");
        for (int i = 1; i <= 12; i++)
            sb.append("<li><a href=\"/site11/read/").append(1000 + i).append("/\">第").append(i).append("章 雾航</a></li>");
        sb.append("</ul></div>");
        sb.append("<div class=\"footer\"><a href=\"/site11/about.html\">关于我们</a></div>");
        sb.append("</body></html>");
        PAGES.put("/site11/book/7/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 12; n++) {
            PAGES.put("/site11/read/" + (1000 + n) + "/", new Page(
                    "<html><head><meta charset=\"utf-8\"><title>第" + n + "章 雾航</title></head><body>"
                            + "<div id=\"content\"><p>第" + n + "章的正文内容。" + filler("雾把渡口吞没了。", 6) + "</p></div>"
                            + "</body></html>", "utf-8"));
        }
        PAGES.put("/site11/about.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>关于我们</title></head><body><p>站点介绍</p></body></html>", "utf-8"));
    }

    // ===== 站点12：分卷 + 编号笔误噪声回落（hongxiu 斗破模式） =====
    // 正文1-20章升序但夹一处倒置(16章后是12章)，末尾番外重置为第1章；
    // 旧逻辑遇到噪声回落即否定分卷 → 番外第1章被重排到卷首
    static void buildSite12() {
        StringBuilder sb = new StringBuilder("<html><head><meta charset=\"utf-8\"><title>笔误分卷站</title></head><body>");
        sb.append("<div id=\"info\"><h1>长河流域</h1><p>作者：测试</p></div>");
        sb.append("<div id=\"list\"><dl>");
        for (int i = 1; i <= 20; i++) {
            if (i == 17) { // 第17个位置错放成第12章（站点局部错序），标题不同以免被同名去重
                sb.append("<dd><a href=\"/site12/html/17.html\">第12章 逆流</a></dd>");
                continue;
            }
            sb.append("<dd><a href=\"/site12/html/").append(i).append(".html\">第").append(i).append("章 远航</a></dd>");
        }
        sb.append("<dd><a href=\"/site12/html/w1.html\">第1章 番外：后来</a></dd>");
        sb.append("</dl></div></body></html>");
        PAGES.put("/site12/book/8/index.html", new Page(sb.toString(), "utf-8"));
        for (int n = 1; n <= 20; n++) {
            PAGES.put("/site12/html/" + n + ".html", new Page(
                    "<html><head><meta charset=\"utf-8\"><title>第" + n + "章</title></head><body>"
                            + "<div id=\"content\"><p>第" + n + "章的正文内容。" + filler("河水向东流去。", 6) + "</p></div>"
                            + "</body></html>", "utf-8"));
        }
        PAGES.put("/site12/html/w1.html", new Page(
                "<html><head><meta charset=\"utf-8\"><title>第1章 番外：后来</title></head><body>"
                        + "<div id=\"content\"><p>番外的正文内容。" + filler("许多年后他又回到渡口。", 6) + "</p></div>"
                        + "</body></html>", "utf-8"));
    }

    static String volChapter(String vol, int n, String sub) {
        return "<html><head><meta charset=\"utf-8\"><title>" + vol + " 第" + n + "章 " + sub + "</title></head><body>"
                + "<div id=\"content\"><p>" + vol + "第" + n + "章内容。" + filler("风云变幻。", 20) + "</p></div>"
                + "</body></html>";
    }
}
