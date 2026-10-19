import com.noveldownloader.ChapterInfo;
import com.noveldownloader.NovelParser;
import com.noveldownloader.NovelResult;

/** 断言式测试：本地仿真站点验证解析器全部关键行为。 */
public class TestParser {
    static int passed = 0, failed = 0;

    static void assertEq(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { passed++; System.out.println("PASS " + name); }
        else { failed++; System.out.println("FAIL " + name + " expected=[" + expected + "] actual=[" + actual + "]"); }
    }

    static void assertTrue(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("PASS " + name); }
        else { failed++; System.out.println("FAIL " + name + " " + detail); }
    }

    public static void main(String[] args) throws Exception {
        String base = "http://localhost:" + FixtureServer.PORT + "/book/1/index.html";
        NovelParser p = new NovelParser();
        NovelResult r = p.parseNovel(base, null);

        System.out.println("=== TOC RESULT ===");
        System.out.println("title=" + r.title + " author=" + r.author + " chapters=" + r.chapters.size());

        assertEq("书名", "测试修仙传", r.title);
        assertEq("作者", "测试作者", r.author);
        assertEq("章节总数(3页目录+预览去重)", FixtureServer.TOTAL_CHAPTERS, r.chapters.size());

        assertEq("第1章", "第1章 正文", r.chapters.get(0).title);
        assertEq("第45章", "第45章 正文", r.chapters.get(r.chapters.size() - 1).title);
        boolean ascending = true;
        for (int i = 0; i < r.chapters.size(); i++) {
            if (!r.chapters.get(i).title.equals("第" + (i + 1) + "章 正文")) { ascending = false; break; }
        }
        assertTrue("目录顺序正确(跨3页+预览去重)", ascending, "order mismatch");

        // 1: “下一页”分页
        String c1 = p.fetchChapterContent(r.chapters.get(0), base, null);
        assertTrue("ch1 分页1", c1.contains("第一章内容第一部分"), "");
        assertTrue("ch1 分页2", c1.contains("第一章内容第二部分"), "");
        assertTrue("ch1 分页3", c1.contains("第一章内容第三部分"), "");
        assertTrue("ch1 不含下一章", !c1.contains("第二章内容"), "");
        assertTrue("ch1 广告行过滤", !c1.contains("请记住本书首发域名"), "");
        assertTrue("ch1 样板行过滤(本章完)", !c1.contains("本章完"), "");

        // 2: GBK
        String c2 = p.fetchChapterContent(r.chapters.get(1), base, null);
        assertTrue("ch2 GBK 解码", c2.contains("第二章内容，GBK编码测试"), "");

        // 3: “查看全文” + “下一页»”带符号 + 样板行过滤
        String c3 = p.fetchChapterContent(r.chapters.get(2), base, null);
        assertTrue("ch3 查看全文分页1", c3.contains("第三章内容第一部分"), "");
        assertTrue("ch3 分页2", c3.contains("第三章内容第二部分"), "");
        assertTrue("ch3 分页3", c3.contains("第三章内容第三部分"), "");
        assertTrue("ch3 样板行过滤", !c3.contains("点击下一页继续阅读"), "");

        // 4: 数字/“第N页”分页 (4.html → 4_2.html)
        String c4 = p.fetchChapterContent(r.chapters.get(3), base, null);
        assertTrue("ch4 数字分页1", c4.contains("第四章内容第一部分"), "");
        assertTrue("ch4 数字分页2", c4.contains("第四章内容第二部分"), "");

        // 45: 末章
        String cN = p.fetchChapterContent(r.chapters.get(r.chapters.size() - 1), base, null);
        assertTrue("末章内容", cN.contains("第45章的正文内容"), "");

        // 6/7: 重复页防护（第一页不能重复出现）
        String c6 = p.fetchChapterContent(r.chapters.get(5), base, null);
        assertEq("ch6 整页复制去重", 1, c6.split("第六章内容第一部分。", -1).length - 1);
        String c7 = p.fetchChapterContent(r.chapters.get(6), base, null);
        assertEq("ch7 带广告复制去重", 1, c7.split("第七章内容第一部分。", -1).length - 1);

        // 8: 分页重叠（下一页开头重复上一页结尾整句 → 去重叠后拼接）
        String c8 = p.fetchChapterContent(r.chapters.get(7), base, null);
        assertTrue("ch8 重叠去重后含第二部分", c8.contains("第八章内容第二部分"), "");
        assertEq("ch8 重叠句只出现一次", 1, c8.split(FixtureServer.OVERLAP_SENTENCE, -1).length - 1);

        // 9: 每页固定长页眉（旧逻辑误判重复页导致丢失第二页）
        String c9 = p.fetchChapterContent(r.chapters.get(8), base, null);
        assertTrue("ch9 页眉页含第一部分", c9.contains("第九章内容第一部分"), "");
        assertTrue("ch9 固定页眉不丢第二页", c9.contains("第九章内容第二部分"), "");
        assertEq("ch9 页眉只出现一次", 1, c9.split("帮忙宣传推荐", -1).length - 1);

        // 10: 目录式分页 10.html → 10/2.html → 10/3.html（旧逻辑不识别导致缺页）
        String c10 = p.fetchChapterContent(r.chapters.get(9), base, null);
        assertTrue("ch10 目录式分页1", c10.contains("第十章内容第一部分"), "");
        assertTrue("ch10 目录式分页2", c10.contains("第十章内容第二部分"), "");
        assertTrue("ch10 目录式分页3", c10.contains("第十章内容第三部分"), "");
        assertTrue("ch10 不含第11章内容", !c10.contains("第十一章内容"), "");

        // 11: 累积式分页（第二页完整包含第一页+新增）
        String c11 = p.fetchChapterContent(r.chapters.get(10), base, null);
        assertEq("ch11 累积合并第一部分只出现一次", 1, c11.split("第十一章内容第一部分。", -1).length - 1);
        assertTrue("ch11 含第二部分", c11.contains("第十一章内容第二部分"), "");

        // ===== 站点2：跨目录章节 + section 容器 + 推荐链接污染 =====
        NovelResult r2 = p.parseNovel("http://localhost:" + FixtureServer.PORT + "/site2/book/9/index.html", null);
        assertEq("S2 章节数", 8, r2.chapters.size());
        assertEq("S2 书名", "都市重生录", r2.title);
        assertTrue("S2 无推荐污染", !r2.chapters.get(0).title.contains("推荐"), "");
        String s2 = p.fetchChapterContent(r2.chapters.get(0), null, null);
        assertTrue("S2 section容器正文", s2.contains("重生第1章内容"), "");
        assertTrue("S2 正文长度", s2.length() > 400, "len=" + s2.length());
        assertTrue("S2 不含其他章内容", !s2.contains("重生第2章内容"), "");

        // ===== 站点3：乱序排序 + 同名重复章节去重 =====
        NovelResult r3 = p.parseNovel("http://localhost:" + FixtureServer.PORT + "/site3/book/77/index.html", null);
        assertEq("S3 章节数(去重后)", 12, r3.chapters.size());
        boolean s3ok = true;
        for (int i = 0; i < 12; i++) {
            if (!r3.chapters.get(i).title.contains("第" + (i + 1) + "章")) s3ok = false;
        }
        assertTrue("S3 已按章节号排序", s3ok, "");

        // ===== 站点4：目录基本递增但个别章节错位（旧逻辑倒序比例≤15%→不排序） =====
        NovelResult r4 = p.parseNovel("http://localhost:" + FixtureServer.PORT + "/site4/book/88/index.html", null);
        assertEq("S4 章节数", 12, r4.chapters.size());
        boolean s4ok = true;
        for (int i = 0; i < 12; i++) {
            if (!r4.chapters.get(i).title.contains("第" + (i + 1) + "章")) s4ok = false;
        }
        assertTrue("S4 个别错位已重排", s4ok, "");
        assertEq("S4 第9章位置正确", "第9章 远航", r4.chapters.get(8).title);

        // ===== 站点5：分卷重新编号，必须保持网页原顺序（不能被交错） =====
        NovelResult r5 = p.parseNovel("http://localhost:" + FixtureServer.PORT + "/site5/book/55/index.html", null);
        assertEq("S5 章节数", 24, r5.chapters.size());
        assertTrue("S5 第1章为第一卷第1章", r5.chapters.get(0).title.contains("第一卷")
                && r5.chapters.get(0).title.contains("第1章"), "");
        assertTrue("S5 第13章为第二卷第1章(未被交错)", r5.chapters.get(12).title.contains("第二卷")
                && r5.chapters.get(12).title.contains("第1章"), r5.chapters.get(12).title);
        assertTrue("S5 末章为第二卷第12章", r5.chapters.get(23).title.contains("第二卷")
                && r5.chapters.get(23).title.contains("第12章"), "");

        // ===== 站点6：目录页顺序轮转（第13-20章在前、第1-12章在后），编号无重复 → 必须按章节号重排 =====
        NovelResult r6 = p.parseNovel("http://localhost:" + FixtureServer.PORT + "/site6/book/66/index.html", null);
        assertEq("S6 章节数", 20, r6.chapters.size());
        boolean s6ok = true;
        for (int i = 0; i < 20; i++) {
            if (!r6.chapters.get(i).title.contains("第" + (i + 1) + "章")) s6ok = false;
        }
        assertTrue("S6 轮转顺序已重排", s6ok, "order mismatch: " + r6.chapters.get(0).title
                + " ... " + r6.chapters.get(19).title);
        assertEq("S6 首章为第1章", "第1章 渡口", r6.chapters.get(0).title);

        // ===== 站点7：静态页只有“阅读模式”提示（正文由脚本注入）→ 浏览器渲染兜底 =====
        NovelParser p7 = new NovelParser();
        p7.setContentRenderer(url -> url.contains("/site7/")
                ? "渡口的风把灯笼吹得直晃，他蹲在船头数着星星。\n远处的汽笛一声长，夜色沉进江水里。"
                : null);
        NovelResult r7 = p7.parseNovel("http://localhost:" + FixtureServer.PORT + "/site7/book/77/index.html", null);
        assertEq("S7 章节数", 2, r7.chapters.size());
        String s7c1 = p7.fetchChapterContent(r7.chapters.get(0), null, null);
        assertTrue("S7 短提示页改用渲染正文", s7c1.contains("渡口的风把灯笼吹得直晃"), s7c1);
        assertTrue("S7 不再残留阅读模式提示", !s7c1.contains("阅读模式"), s7c1);
        String s7c2 = p7.fetchChapterContent(r7.chapters.get(1), null, null);
        assertTrue("S7 提示语凑满容器也能兜底", s7c2.contains("渡口的风把灯笼吹得直晃"), s7c2);

        System.out.println("\n=== SUMMARY: " + passed + " passed, " + failed + " failed ===");
        System.exit(failed == 0 ? 0 : 1);
    }
}
