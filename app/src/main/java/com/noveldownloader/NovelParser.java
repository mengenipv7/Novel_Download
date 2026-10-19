package com.noveldownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 网页小说解析器（纯 Java + Jsoup，无 Android 依赖，可桌面单测）。
 *
 * 目录解析：
 *  - 自动识别章节列表容器
 *  - 去除页首“最新章节预览”重复块（同链接保留最后出现位置）
 *  - 多策略翻页合并：下一页/下页/后页、查看更多/更多章节/展开/加载更多、
 *    查看全部/全部章节/完整目录、rel="next"、class 含 next、箭头链接
 *
 * 正文解析：
 *  - 多选择器容错 + 文字最多 div 兜底
 *  - 章节内分页拼接：下一页/下页、查看全文/阅读全文/展开全文/继续阅读、
 *    数字分页（123_2.html、123/2.html 等结构）、rel/class next
 *  - URL 结构判定分页边界，不误将下一章当分页
 *  - 重复/重叠页处理：整页重复停止、累积式合并、固定页眉前缀与结尾重叠去重
 *  - 过滤广告行与“本章未完点击下一页”等样板行
 */
public class NovelParser {

    private static final String TAG = "NovelParser";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private int timeoutMs = 20000;
    private int maxTocPages = 200;
    private int maxChapterPages = 60;
    private int maxRetry = 3;

    /** 目录导航/功能链接，不计入章节 */
    private static final Pattern NAV_TEXT = Pattern.compile(
            "首页|目录|书架|书签|排行|登录|注册|加入书架|开始阅读|手机阅读|上一页|下一页|上一章|下一章|返回|下载|搜索|收藏本站|报错"
            + "|查看更多|展开|加载更多|收起|全部章节|完整目录|更多章节|尾页|订阅|最新章节|求收藏|求推荐");

    /** 目录翻页链接（strip 后全等匹配） */
    private static final Set<String> TOC_NEXT_WORDS = new HashSet<>(Arrays.asList(
            "下一页", "下页", "后一页", "后页", "下一頁", "nextpage", "next",
            "查看更多", "更多章节", "查看更多章节", "展开更多", "加载更多", "显示更多", "更多",
            "查看全部", "查看全部章节", "全部章节", "查看完整目录", "完整目录", "展开全部"));

    /** 章节正文翻页链接（strip 后全等匹配） */
    private static final Set<String> CHAPTER_NEXT_WORDS = new HashSet<>(Arrays.asList(
            "下一页", "下页", "下一頁", "翻页", "nextpage", "next",
            "查看全文", "阅读全文", "展开全文", "继续阅读", "readmore", "readmore..."));

    /** 广告/推广/样板行过滤 */
    private static final List<Pattern> AD_LINE_PATTERNS = Arrays.asList(
            Pattern.compile("请记住本书首发域名"),
            Pattern.compile("天才一秒记住"),
            Pattern.compile("一秒记住"),
            Pattern.compile("手机用户请浏览"),
            Pattern.compile("最新网址"),
            Pattern.compile("本书首发域名"),
            Pattern.compile("推荐地址"),
            Pattern.compile("求书网"),
            Pattern.compile("本章未完.*?(点击|继续)"),
            Pattern.compile("点击下一页"),
            Pattern.compile("未完待续"),
            Pattern.compile("继续阅读(后面|精彩|请)"),
            Pattern.compile("智能功能"),
            Pattern.compile("请关闭.{0,15}模式"),
            Pattern.compile("请勿(开启|使用).{0,10}(阅读|畅读|小说)模式"),
            Pattern.compile("否则将导致章节内容缺失"),
            Pattern.compile("晃动眼睛"),
            Pattern.compile("关闭广告"),
            Pattern.compile("无法显示"),
            Pattern.compile("一闭眼睛就过去"),
            Pattern.compile("^\\s*(https?://)?(www\\.)?[\\w-]+\\.(com|net|cn|cc|org|la|me|info|xyz|top)\\s*/?\\s*$", Pattern.CASE_INSENSITIVE));

    /** 独立成行的翻页/导航样板（整行删除） */
    private static final Pattern NAV_LINE = Pattern.compile(
            "^\\s*[-–—=\\[\\(\\{【（ ]*(上一页|下一页|上页|下页|目录|返回目录|章节目录|加入书签|投推荐票|求收藏|求推荐票|推荐票|本章完|未完待续)[\\]\\)\\}】） ]*\\s*$");

    /** 纯页码行，如 "3/5"、"第2页" */
    private static final Pattern PAGE_NO_LINE = Pattern.compile(
            "^\\s*(第\\s*\\d+\\s*页|\\d{1,3}\\s*/\\s*\\d{1,3})\\s*$");

    private static final Set<String> BLOCK_TAGS = new HashSet<>(Arrays.asList(
            "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6",
            "section", "article", "table", "ul", "ol", "blockquote", "dd", "dt", "dl", "header", "footer"));

    public static class CancelToken {
        public volatile boolean cancelled = false;
    }

    /**
     * 正文渲染器：部分站点（如“请勿开启浏览器阅读模式”提示页）静态 HTML 里没有正文，
     * 由脚本动态填充。Android 端用 WebView 实现，桌面测试可注入假实现。
     */
    public interface ContentRenderer {
        /** 渲染页面并返回可见正文；失败返回 null */
        String render(String url);
    }

    private ContentRenderer contentRenderer;

    public void setContentRenderer(ContentRenderer renderer) {
        this.contentRenderer = renderer;
    }

    /** 站点把正文替换成“阅读模式/广告屏蔽”提示页的特征 */
    private static final Pattern PLACEHOLDER_PAGE = Pattern.compile(
            "阅读模式|畅读模式|小说模式|章节内容缺失|广告屏蔽|开启浏览器阅读");

    // ---------------- 工具 ----------------

    /** 去掉空白与箭头等修饰符号并转小写，用于翻页文字比对 */
    static String stripSymbols(String t) {
        if (t == null) return "";
        return t.replaceAll("[\\s\\u3000»›‹«<>\\[\\]\\(\\)\\{\\}【】（）\\-_.~,，。;；:：'\"`~!！?？*|\\\\/]+", "")
                .toLowerCase();
    }

    /** URL 最后一段路径（去掉扩展名），用于判断数字分页是否同属一个章节 */
    private static String segmentBase(String url) {
        try {
            String p = url;
            int hash = p.indexOf('#');
            if (hash >= 0) p = p.substring(0, hash);
            int schemeEnd = p.indexOf("//");
            int slash = p.indexOf('/', schemeEnd >= 0 ? schemeEnd + 2 : 0);
            p = slash >= 0 ? p.substring(slash) : p;
            int q = p.indexOf('?');
            if (q >= 0) p = p.substring(0, q);
            p = p.replaceAll("\\.s?html?$", "");
            int lastSlash = p.lastIndexOf('/');
            return lastSlash >= 0 ? p.substring(lastSlash + 1) : p;
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------- 目录解析 ----------------

    public NovelResult parseNovel(String tocUrl, CancelToken token) throws IOException {
        if (tocUrl == null || tocUrl.isEmpty()) throw new IOException("目录地址为空");
        String url = normalizeUrl(tocUrl);
        Logger.i(TAG, "开始解析目录: " + url);

        List<ChapterInfo> all = new ArrayList<>();
        Set<String> visitedPages = new HashSet<>();
        String title = "";
        String author = "";

        String pageUrl = url;
        int pageNo = 0;
        while (pageUrl != null && !visitedPages.contains(pageUrl) && pageNo < maxTocPages) {
            checkCancel(token);
            visitedPages.add(pageUrl);
            pageNo++;
            Logger.i(TAG, "抓取目录第 " + pageNo + " 页: " + pageUrl);
            Document doc = fetch(pageUrl);

            if (pageNo == 1) {
                title = extractNovelTitle(doc);
                author = extractAuthor(doc);
                Logger.i(TAG, "书名: " + (title.isEmpty() ? "(未识别)" : title)
                        + (author.isEmpty() ? "" : "  作者: " + author));
            }

            List<ChapterInfo> pageChapters = extractChapterLinks(doc, bookPrefix(tocUrl != null ? tocUrl : pageUrl));
            Logger.i(TAG, "第 " + pageNo + " 页识别章节链接 " + pageChapters.size() + " 条");
            all.addAll(pageChapters);

            pageUrl = findNextTocPage(doc, pageUrl);
            if (pageUrl != null && visitedPages.contains(pageUrl)) pageUrl = null;
        }

        List<ChapterInfo> chapters = dedupeChapters(all);
        sortChapters(chapters);
        chapters = removeTitleDups(chapters);
        Logger.i(TAG, "目录解析完成: 共 " + chapters.size() + " 章 (抓取 " + pageNo
                + " 页, 去重前 " + all.size() + " 条)");
        if (chapters.isEmpty()) throw new IOException("未能在目录页识别到章节链接，请确认地址是否为目录页");
        return new NovelResult(title, author, chapters);
    }

    /** 同一链接只保留最后出现位置：页首“预览/最新章节”被丢弃，目录顺序保持正确 */
    static List<ChapterInfo> dedupeChapters(List<ChapterInfo> all) {
        List<ChapterInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            ChapterInfo c = all.get(i);
            if (seen.add(c.url)) result.add(0, c);
        }
        return result;
    }

    private static final java.util.regex.Pattern CN_CHAPTER_NUM =
            java.util.regex.Pattern.compile("第\\s*([0-9０-９]{1,7}|[零一二三四五六七八九十百千万两]+)\\s*[章节回篇]");

    /**
     * 目录排序：按章节号升序（稳定排序，同号保持文档顺序），无编号章节置末尾。
     * 仅在识别到“分卷重新编号”结构（多段各自递增且段首回到小号）时保留原顺序；
     * 其余情况一律按章节号重排，避免用户看到的仍是网页原始显示顺序。
     */
    private static void sortChapters(List<ChapterInfo> chapters) {
        int n = chapters.size();
        if (n < 3) return;
        long[] keys = new long[n];
        int numbered = 0;
        for (int i = 0; i < n; i++) {
            keys[i] = chapterNum(chapters.get(i).title);
            if (keys[i] >= 0) numbered++;
        }
        if (numbered < Math.max(3, (n + 1) / 2)) {
            Logger.i(TAG, "章节号覆盖率不足(" + numbered + "/" + n + ")，保持原始目录顺序");
            return;
        }
        if (isMultiVolumeOrder(keys)) {
            Logger.i(TAG, "检测到分卷重新编号结构，保持原始目录顺序");
            return;
        }
        if (isAscending(keys)) return; // 已按章节号升序，无需重排

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        final long[] k = keys;
        java.util.Collections.sort(idx, (a, b) -> {
            long ka = k[a] < 0 ? Long.MAX_VALUE : k[a]; // 无编号章节排最后（番外等）
            long kb = k[b] < 0 ? Long.MAX_VALUE : k[b];
            return ka != kb ? Long.compare(ka, kb) : Integer.compare(a, b);
        });
        List<ChapterInfo> sorted = new ArrayList<>(n);
        for (int i : idx) sorted.add(chapters.get(i));
        chapters.clear();
        chapters.addAll(sorted);
        Logger.i(TAG, "已按章节号重新排序(升序)");
    }

    /** 章节号序列是否已升序（忽略无编号项） */
    private static boolean isAscending(long[] keys) {
        long prev = -1;
        for (long k : keys) {
            if (k < 0) continue;
            if (prev >= 0 && k < prev) return false;
            prev = k;
        }
        return true;
    }

    /**
     * 是否为“分卷重新编号”结构：序列分成多段且每段各自递增，段边界回落到小号(≤5)，
     * 且回落前的段长≥8（分卷通常含较多章节）。命中说明网页顺序即正确阅读顺序，
     * 不应按章节号重排，否则会把各卷章节交错。
     * <p>
     * 但“长段后回落到第1章”也可能是目录分页被轮转打乱（如第101-323章在前、第1-100章在后）：
     * 分卷重新编号必然出现重复章节号（每卷都从第1章起算），若编号全局互不重复，
     * 则属网页顺序错乱，仍应按章节号重排。
     */
    private static boolean isMultiVolumeOrder(long[] keys) {
        long prev = -1;
        int runLen = 0, resets = 0;
        for (long k : keys) {
            if (k < 0) continue;
            if (prev >= 0 && k < prev) {
                if (runLen < 8 || k > 5) return false;
                resets++;
                runLen = 0;
            }
            runLen++;
            prev = k;
        }
        return resets > 0 && hasDupChapterNum(keys);
    }

    /** 章节号是否存在重复（分卷重新编号的判据：各卷编号重叠） */
    private static boolean hasDupChapterNum(long[] keys) {
        Set<Long> seen = new HashSet<>();
        for (long k : keys) {
            if (k >= 0 && !seen.add(k)) return true;
        }
        return false;
    }

    /** 标题相同(忽略空白)的重复章节只保留排序后的第一个（不同URL指向同一章的情况） */
    private static List<ChapterInfo> removeTitleDups(List<ChapterInfo> in) {
        List<ChapterInfo> result = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        int removed = 0;
        for (ChapterInfo c : in) {
            String t = c.title == null ? "" : c.title.replaceAll("\\s+", "");
            if (t.isEmpty() || seenTitles.add(t)) result.add(c);
            else removed++;
        }
        if (removed > 0) Logger.i(TAG, "去除同名重复章节 " + removed + " 条");
        return result;
    }

    /** 从标题提取章节序号：第N章(阿拉伯/全角/中文数字)；无编号返回-1；序章/楔子/引子返回0 */
    static long chapterNum(String title) {
        if (title == null) return -1;
        java.util.regex.Matcher m = CN_CHAPTER_NUM.matcher(title);
        if (m.find()) return parseCnNum(m.group(1));
        if (title.matches(".*(序章|楔子|引子|前言|序言).*")) return 0;
        return -1;
    }

    /** 解析阿拉伯数字/全角数字/中文数字为长整型，失败返回-1 */
    static long parseCnNum(String s) {
        if (s == null || s.isEmpty()) return -1;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '０' && ch <= '９') sb.append((char) ('0' + ch - '０'));
            else sb.append(ch);
        }
        String t = sb.toString();
        if (t.matches("[0-9]+")) {
            try { return Long.parseLong(t); } catch (NumberFormatException e) { return -1; }
        }
        long res = 0, cur = 0;
        for (char ch : t.toCharArray()) {
            switch (ch) {
                case '零': break;
                case '一': cur = 1; break;
                case '二': case '两': cur = 2; break;
                case '三': cur = 3; break;
                case '四': cur = 4; break;
                case '五': cur = 5; break;
                case '六': cur = 6; break;
                case '七': cur = 7; break;
                case '八': cur = 8; break;
                case '九': cur = 9; break;
                case '十': res += (cur == 0 ? 1 : cur) * 10; cur = 0; break;
                case '百': res += (cur == 0 ? 1 : cur) * 100; cur = 0; break;
                case '千': res += (cur == 0 ? 1 : cur) * 1000; cur = 0; break;
                case '万': res = (res + cur) * 10000; cur = 0; break;
                case '亿': res = (res + cur) * 100000000L; cur = 0; break;
                default: return -1;
            }
        }
        return res + cur;
    }

    /** 目录页路径前缀，如 /xs/167218/ —— 用于排除“推荐书单”里其他小说的链接 */
    private String bookPrefix(String tocUrl) {
        try {
            String path = new java.net.URI(tocUrl).getPath();
            if (path == null || path.isEmpty()) return null;
            return path.endsWith("/") ? path : path.substring(0, path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSameBook(String chapterUrl, String prefix) {
        if (prefix == null) return true;
        try {
            String path = new java.net.URI(chapterUrl).getPath();
            return path != null && path.startsWith(prefix);
        } catch (Exception e) {
            return true;
        }
    }

    private List<ChapterInfo> extractChapterLinks(Document doc, String prefix) {
        String[] containerSelectors = {
                "#list a", ".listmain a", ".chapter-list a", "#chapterlist a", "#chapterList a",
                ".mulu a", ".catalog a", ".catalog-list a", ".booklist a", ".book-list a",
                ".section-list a", ".volume-list a", ".chapter a", ".mlist a", "#mlist a",
                "#listall a", ".dirlist a", "dl dd a"
        };
        Elements best = null;
        for (String sel : containerSelectors) {
            Elements els = doc.select(sel);
            if (best == null || els.size() > best.size()) best = els;
        }
        if (best == null || best.size() < 5) {
            Elements all = doc.select("a[href]");
            Elements filtered = new Elements();
            for (Element a : all) {
                String href = a.absUrl("href");
                if (href.matches(".*(\\.html?|/\\d+/?)$")) filtered.add(a);
            }
            if (filtered.size() > (best == null ? 0 : best.size())) best = filtered;
        }

        List<ChapterInfo> list = new ArrayList<>();
        if (best != null) {
            for (Element a : best) {
                String text = a.text() == null ? "" : a.text().trim();
                String href = a.absUrl("href");
                if (text.isEmpty() || href.isEmpty()) continue;
                if (NAV_TEXT.matcher(text).find()) continue;
                if (!href.startsWith("http")) continue;
                list.add(new ChapterInfo(text, href));
            }
        }
        if (list.isEmpty()) return list;

        // 按目录分组取最大组：章节链接几乎总是集中在同一目录，
        // 跨目录的推荐/书单/其他书链接被自然排除，也兼容目录页与章节页不同目录的站点
        java.util.Map<String, List<ChapterInfo>> groups = new java.util.LinkedHashMap<>();
        for (ChapterInfo c : dedupeChapters(list)) {
            groups.computeIfAbsent(dirOf(c.url), k -> new ArrayList<>()).add(c);
        }
        List<ChapterInfo> bestGroup = null;
        for (List<ChapterInfo> g : groups.values()) {
            if (bestGroup == null || g.size() > bestGroup.size()) bestGroup = g;
        }
        if (bestGroup != null && bestGroup.size() >= 5 && bestGroup.size() >= list.size() / 2) {
            Logger.i(TAG, "识别章节目录组: " + dirOf(bestGroup.get(0).url) + " 共 " + bestGroup.size() + " 条");
            return bestGroup;
        }

        // 目录组不占多数 → 回退同书前缀过滤 / 全部
        List<ChapterInfo> sameBook = new ArrayList<>();
        for (ChapterInfo c : list) {
            if (isSameBook(c.url, prefix)) sameBook.add(c);
        }
        // 前缀过滤后章节过少说明该站章节路径与目录不同源，回退为不过滤
        return sameBook.size() >= 5 ? sameBook : list;
    }

    /** 链接所在目录，如 https://x.com/book/1/a.html → /book/1/ */
    private String dirOf(String url) {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.isEmpty()) return "";
            int i = path.lastIndexOf('/');
            return i >= 0 ? path.substring(0, i + 1) : path;
        } catch (Exception e) {
            return "";
        }
    }

    private String findNextTocPage(Document doc, String currentUrl) {
        for (Element a : doc.select("a[href]")) {
            String raw = a.text() == null ? "" : a.text().trim();
            String href = a.absUrl("href");
            if (href.isEmpty() || href.equals(currentUrl)) continue;

            String s = stripSymbols(raw);
            boolean textHit = TOC_NEXT_WORDS.contains(s) || s.equals(">>") || s.equals(">");
            boolean attrHit = "next".equalsIgnoreCase(a.attr("rel"))
                    || a.className().toLowerCase().contains("next");
            if (textHit || attrHit) {
                Logger.i(TAG, "发现目录翻页链接「" + raw + "」: " + href);
                return href;
            }
        }
        return null;
    }

    private String extractNovelTitle(Document doc) {
        String[] sels = {"#info h1", ".booktitle h1", ".booktitle", "#title", ".book-name", "h1"};
        for (String sel : sels) {
            Element e = doc.selectFirst(sel);
            if (e != null && !e.text().trim().isEmpty()) return e.text().trim();
        }
        String t = doc.title();
        return t == null ? "" : t.replaceAll("[-_|].*$", "").trim();
    }

    private String extractAuthor(Document doc) {
        Element meta = doc.selectFirst("meta[name=author], meta[property=og:novel:author]");
        if (meta != null && !meta.attr("content").isEmpty()) return meta.attr("content").trim();
        String bodyText = doc.body() == null ? "" : doc.body().text();
        java.util.regex.Matcher m = Pattern.compile("作者[:：]\\s*([\\u4e00-\\u9fa5\\w·]{2,20})").matcher(bodyText);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    // ---------------- 章节正文解析 ----------------

    public String fetchChapterContent(ChapterInfo chapter, String tocUrl, CancelToken token) throws IOException {
        Set<String> visited = new HashSet<>();
        List<String> pageTexts = new ArrayList<>();
        String url = chapter.url;
        String chapterBase = chapterBasePath(chapter.url);
        int pageNo = 0;

        while (url != null && !visited.contains(url) && pageNo < maxChapterPages) {
            checkCancel(token);
            visited.add(url);
            pageNo++;
            Document doc;
            try {
                doc = fetch(url);
            } catch (IOException e) {
                // 首页失败视为整章失败；后续分页失败则保留已抓到的部分内容
                if (pageNo == 1) throw e;
                Logger.e(TAG, "《" + chapter.title + "》第" + pageNo + "页抓取失败，保留已获取内容: " + e.getMessage());
                break;
            }

            Element content = findContentElement(doc);
            // 先取分页链接，extractText 会剔除正文内的链接元素，顺序不能反
            String nextUrl = findChapterNextPage(doc, url, tocUrl, chapterBase);
            String text = content == null ? "" : extractText(content);

            // 静态 HTML 里没有正文、或只拿到站点“阅读模式”提示页时，用浏览器内核渲染后再取正文
            if (contentRenderer != null && (content == null || needsRender(text))) {
                String rendered = renderWithBrowser(url);
                if (rendered != null && squeeze(rendered).length() > squeeze(text).length()) {
                    Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页静态正文缺失，已改用浏览器渲染");
                    text = rendered;
                }
            }

            if (content == null && squeeze(text).isEmpty()) {
                if (pageNo == 1) throw new IOException("未找到正文内容: " + url);
                break;
            }

            // 重复/重叠页检测（均在去空白后的文本上比较，避免站点插广告导致漏检）：
            // 1) 整页相同 → 软404重复页，停止
            // 2) 新页完整包含上一页且更长 → 累积式分页，用新页替换
            // 3) 新页与上一页开头出现相同的长行 → 站点固定页眉，整行剥离后保留剩余（不可整页停止，避免丢页）
            // 4) 上一页结尾出现在新页开头 → 分页重叠，去重叠段后保留剩余
            // 5) 上一页结尾段出现在新页中间：新页明显更长 → 累积式替换；否则 → 重复页停止
            // 6) 上一页开头段出现在新页中间（顶部广告式重复）：去重复开头后保留剩余，剩余过小则停止
            if (!pageTexts.isEmpty()) {
                String prevRaw = pageTexts.get(pageTexts.size() - 1);
                String prev = squeeze(prevRaw);
                String cur = squeeze(text);
                if (prev.length() > 40 && cur.length() > 40) {
                    if (cur.equals(prev)) {
                        Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页内容与上一页重复，停止");
                        break;
                    }
                    if (cur.length() > prev.length() + 20 && cur.contains(prev)) {
                        Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页为累积式分页，已合并");
                        pageTexts.set(pageTexts.size() - 1, text);
                        url = nextUrl;
                        continue;
                    }
                    // 3) 新页开头与上一页开头出现相同的长行（站点固定页眉/公告），逐行剥离后保留剩余
                    int stripLen = leadingDupLinesLen(prevRaw, text);
                    if (stripLen > 0) {
                        String rest = text.substring(Math.min(stripLen, text.length()));
                        if (squeeze(rest).length() >= 20) {
                            Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页开头与上一页重复，去除页眉后保留剩余");
                            pageTexts.add(rest);
                            url = nextUrl;
                            continue;
                        }
                        Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页为上一页的部分重复，停止");
                        break;
                    }
                    // 4) 上一页结尾与新页开头的重叠
                    int ov = suffixPrefixOverlap(prev, cur, 2000);
                    if (ov >= 20) {
                        String rest = cur.substring(ov);
                        if (rest.length() >= 20) {
                            Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页与上一页结尾重叠" + ov + "字，去重后保留剩余");
                            pageTexts.add(stripSqueezedPrefix(text, ov));
                        } else {
                            Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页与上一页结尾重复，停止");
                        }
                        url = nextUrl;
                        continue;
                    }
                    String tail = prev.substring(Math.max(0, prev.length() - 60));
                    int ti = cur.indexOf(tail);
                    // 5) 上一页结尾段出现在新页中间
                    if (ti >= 0) {
                        if (cur.length() > prev.length() + 40) {
                            Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页为累积式分页，已合并");
                            pageTexts.set(pageTexts.size() - 1, text);
                            url = nextUrl;
                            continue;
                        }
                        Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页内容与上一页重复，停止");
                        break;
                    }
                    // 6) 上一页开头段出现在新页中间（带顶部广告的重复/累积页）
                    if (prev.length() >= 60) {
                        String head = prev.substring(0, 60);
                        int hi = cur.indexOf(head);
                        if (hi >= 0) {
                            String rest = cur.substring(hi + 60);
                            if (cur.length() > prev.length() + 40 && rest.contains(tail)) {
                                Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页为累积式分页，已合并");
                                pageTexts.set(pageTexts.size() - 1, text);
                                url = nextUrl;
                                continue;
                            }
                            if (rest.length() >= 60 && cur.length() > prev.length()) {
                                Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页开头与上一页重复，去重后保留剩余");
                                pageTexts.add(stripSqueezedPrefix(text, hi + 60));
                                url = nextUrl;
                                continue;
                            }
                            Logger.i(TAG, "《" + chapter.title + "》第" + pageNo + "页内容与上一页重复，停止");
                            break;
                        }
                    }
                }
            }
            if (!text.isEmpty()) pageTexts.add(text);

            url = nextUrl;
            if (url != null) Logger.i(TAG, "《" + chapter.title + "》发现分页，继续第 " + (pageNo + 1) + " 页: " + url);
        }
        StringBuilder sb = new StringBuilder();
        for (String t : pageTexts) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
        }
        String result = sb.toString().trim();
        Logger.i(TAG, "《" + chapter.title + "》正文获取完成: " + result.length() + " 字, " + pageTexts.size() + " 页");
        return result;
    }

    private static String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    /** 新页与上一页开头完全相同的长行（≥30字，如固定页眉）总长度（含换行），无则返回 0 */
    private static int leadingDupLinesLen(String prevText, String curText) {
        String[] pl = prevText.split("\n", -1);
        String[] cl = curText.split("\n", -1);
        int n = 0, len = 0;
        while (n < pl.length && n < cl.length && n < 5) {
            String a = squeeze(pl[n]);
            String b = squeeze(cl[n]);
            if (a.isEmpty() || b.isEmpty() || !a.equals(b) || a.length() < 30) break;
            len += cl[n].length() + 1;
            n++;
        }
        return len;
    }

    /** prev 的最长后缀同时是 cur 的前缀（去空白文本上的分页重叠），无 ≥minLen 重叠返回 0 */
    private static int suffixPrefixOverlap(String prev, String cur, int cap) {
        int minLen = 20;
        int max = Math.min(Math.min(prev.length(), cur.length()), cap);
        for (int k = max; k >= minLen; k--) {
            if (prev.regionMatches(prev.length() - k, cur, 0, k)) return k;
        }
        return 0;
    }

    /** 在原文中跳过 n 个非空白字符（与 squeeze 计数一致），返回剩余文本（去首部空白） */
    private static String stripSqueezedPrefix(String original, int n) {
        int i = 0, count = 0, len = original.length();
        while (i < len && count < n) {
            if (!Character.isWhitespace(original.charAt(i))) count++;
            i++;
        }
        while (i < len && Character.isWhitespace(original.charAt(i))) i++;
        return i < len ? original.substring(i) : "";
    }

    private String normalizeTitle(String t) {
        return t == null ? "" : t.replaceAll("\\s+", "");
    }

    private Element findContentElement(Document doc) {
        String[] sels = {"#content", "#chaptercontent", ".read-content", ".show-content",
                "#booktext", "#bookText", "#booktxt", ".novel_content", "#txt", ".read_chapterDetail",
                ".chapter-content", "#htmlContent", "#txtContent", ".txtnav", "#nr1",
                ".nr_nr", "#nr", ".content", "#contenttext",
                "#chapter_content", ".chapter_content", "#readcontent", ".readcontent",
                "#readcotent", ".readcotent", ".showtxt", "#BookText", ".contentbox",
                "#cont-body", ".yd_text", ".readBox", ".article-content", "#article",
                ".reading-content", "#novelcontent", ".novelcontent",
                "#contenttxt", ".contenttxt", ".txt-content", "#text-content", "#zhuyu"};
        for (String sel : sels) {
            Element e = doc.selectFirst(sel);
            if (e != null && e.text().trim().length() > 100) return e;
        }
        Element best = null;
        int bestLen = 0;
        Element body = doc.body();
        if (body != null) {
            for (Element div : body.select("div, section, article")) {
                int len = div.text().trim().length();
                if (len > bestLen) { bestLen = len; best = div; }
            }
        }
        return bestLen > 100 ? best : null;
    }

    /** 静态正文是否缺失到需要浏览器渲染兜底（命中站点提示页特征，或内容明显过短） */
    private boolean needsRender(String text) {
        String s = squeeze(text);
        if (s.length() >= 200) return false;
        if (PLACEHOLDER_PAGE.matcher(s).find()) return true;
        return s.length() < 60;
    }

    /** 调浏览器渲染器取正文（已做行级清洗）；失败或未注入渲染器返回 null */
    private String renderWithBrowser(String url) {
        if (contentRenderer == null) return null;
        try {
            String raw = contentRenderer.render(url);
            if (raw == null || raw.trim().isEmpty()) return null;
            return normalizeText(raw);
        } catch (Throwable t) {
            Logger.e(TAG, "浏览器渲染失败: " + url, t);
            return null;
        }
    }

    /**
     * 章节内翻页识别：
     * 1) 文案命中：下一页/下页/查看全文/阅读全文/展开全文/继续阅读/next...
     * 2) 数字/“第N页”分页链接
     * 3) rel="next" 或 class 含 next
     * 命中文案后，还必须通过 URL 结构判断确认目标是【本章节的分页】而非下一章：
     *   (a) 123.html → 123_2.html / 123-2.html（同章节文件名派生）
     *   (b) 同一路径不同 query（?page=2 风格）
     *   (c) 目录式：章节基路径下的数字页 123.html → 123/2.html → 123/3.html
     * 否则（如末页的“下一页”指向下一章 456.html）不跟随。
     */
    String findChapterNextPage(Document doc, String currentUrl, String tocUrl, String chapterBase) {
        for (Element a : doc.select("a[href]")) {
            String raw = a.text() == null ? "" : a.text().trim();
            String href = a.absUrl("href");
            if (href.isEmpty() || href.equals(currentUrl) || (tocUrl != null && href.equals(tocUrl))) continue;
            String s = stripSymbols(raw);
            if (s.isEmpty()) continue;

            // 明确排除：上一页/返回/目录/下一章
            if (s.contains("上一") || s.contains("返回") || s.contains("目录")
                    || s.contains("书架") || s.equals("下一章") || s.equals("下一頁章")) continue;

            boolean wordHit = CHAPTER_NEXT_WORDS.contains(s);
            boolean prefixHit = s.startsWith("下一页") || s.startsWith("下页") || s.startsWith("查看全文")
                    || s.startsWith("阅读全文") || s.startsWith("展开全文") || s.startsWith("继续阅读");
            boolean attrHit = "next".equalsIgnoreCase(a.attr("rel"))
                    || a.className().toLowerCase().contains("next");
            boolean numericText = s.matches("第?\\d{1,3}页?");

            if ((wordHit || prefixHit || attrHit || numericText) && isSameChapterPage(currentUrl, href, chapterBase)) {
                Logger.i(TAG, "章节分页链接「" + raw + "」: " + href);
                return href;
            }
        }
        return null;
    }

    /** 判断目标链接是否为当前章节自身的分页（而非下一章） */
    private boolean isSameChapterPage(String currentUrl, String candidateUrl, String chapterBase) {
        try {
            java.net.URI cu = new java.net.URI(currentUrl);
            java.net.URI hu = new java.net.URI(candidateUrl);
            if (cu.getHost() == null || !cu.getHost().equals(hu.getHost())) return false;
            String cPath = cu.getPath() == null ? "" : cu.getPath();
            String hPath = hu.getPath() == null ? "" : hu.getPath();
            // (b) 同一路径、不同 query：?page=2
            String cq = cu.getQuery() == null ? "" : cu.getQuery();
            String hq = hu.getQuery() == null ? "" : hu.getQuery();
            if (cPath.equals(hPath) && !cq.equals(hq)) return true;
            // (a) 123.html → 123_2.html / 123-2（去掉当前页自身 _N 后缀后比较）
            String cSeg = segmentBase(currentUrl);
            String hSeg = segmentBase(candidateUrl);
            if (!cSeg.isEmpty() && !hSeg.isEmpty()
                    && hSeg.matches(Pattern.quote(cSeg.replaceAll("[-_]\\d+$", "")) + "[-_]\\d+")) {
                return true;
            }
            // (c) 目录式分页：章节基路径下的纯数字页（123.html → 123/2.html → 123/3.html）
            if (chapterBase != null && !chapterBase.isEmpty()) {
                String hNoExt = stripPageExt(hPath);
                if (hNoExt.startsWith(chapterBase + "/")
                        && hNoExt.substring(chapterBase.length() + 1).matches("\\d{1,4}")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 去掉路径扩展名与末尾斜杠：/a/123.html、/a/123/ → /a/123 */
    private static String stripPageExt(String path) {
        String p = path.replaceAll("\\.s?html?$", "");
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /**
     * 章节基路径（用于目录式分页判定）：
     * /book/123.html、/book/123/、/book/123_1.html → /book/123
     */
    static String chapterBasePath(String url) {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.isEmpty()) return null;
            path = stripPageExt(path);
            int slash = path.lastIndexOf('/');
            String last = slash >= 0 ? path.substring(slash + 1) : path;
            String base = last.replaceAll("[-_]\\d+$", "");
            if (!base.isEmpty() && !base.equals(last)) {
                return slash >= 0 ? path.substring(0, slash + 1) + base : base;
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 正文文本抽取 ----------------

    String extractText(Element root) {
        root.select("script, style, iframe, noscript").remove();
        root.select("[class~=^(ad|ads|advert|banner)$], [id~=^(ad|ads|advert)$]").remove();
        // 正文内的短链接（上一章/下一章按钮、页内章节导航、推广链）一律剔除，
        // 避免其文字混入正文；长文本链接（>30字）极小概率是正文本身，予以保留
        for (Element a : root.select("a")) {
            String t = a.text() == null ? "" : a.text().trim();
            if (t.length() < 30) a.remove();
        }
        StringBuilder sb = new StringBuilder();
        walk(root, sb);
        return normalizeText(sb.toString());
    }

    private void walk(Node node, StringBuilder sb) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode) {
                sb.append(((TextNode) child).getWholeText());
            } else if (child instanceof Element) {
                Element el = (Element) child;
                if (el.tagName().equals("br")) {
                    sb.append('\n');
                } else {
                    walk(el, sb);
                    if (BLOCK_TAGS.contains(el.tagName())) sb.append('\n');
                }
            }
        }
    }

    String normalizeText(String raw) {
        String s = raw.replace('\r', '\n').replace(' ', ' ');
        String[] lines = s.split("\n");
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : lines) {
            String t = line.replaceAll("[ \\t\\u3000]+", " ").trim();
            if (t.isEmpty()) {
                blank++;
                if (blank <= 1) out.append('\n');
                continue;
            }
            blank = 0;
            if (isAdLine(t)) continue;
            if (NAV_LINE.matcher(t).matches()) continue;
            if (PAGE_NO_LINE.matcher(t).matches()) continue;
            out.append(t).append('\n');
        }
        return out.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean isAdLine(String line) {
        for (Pattern p : AD_LINE_PATTERNS) {
            if (p.matcher(line).find()) return true;
        }
        return false;
    }

    // ---------------- 基础抓取 ----------------

    private Document fetch(String url) throws IOException {
        IOException last = null;
        int attempts = maxRetry + 2; // 共5次：前半段移动UA，后半段桌面UA，最后一次换协议
        for (int i = 0; i < attempts; i++) {
            String target = url;
            String ua = UA;
            if (i >= attempts / 2) ua = UA_DESKTOP;
            if (i == attempts - 1) {
                // 最后一次：http/https 互换再试一次
                if (target.startsWith("http://")) target = "https://" + target.substring(7);
                else if (target.startsWith("https://")) target = "http://" + target.substring(8);
            }
            try {
                org.jsoup.Connection.Response r = Jsoup.connect(target)
                        .userAgent(ua)
                        .timeout(timeoutMs)
                        .maxBodySize(0)
                        .followRedirects(true)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .referrer(target)
                        .execute();
                Document doc = r.parse();
                // 乱码自愈：替换符过多说明声明编码与实际不符，用常见中文编码重解码
                String text = doc.text();
                if (countBad(text) > Math.max(4, text.length() / 100)) {
                    Document fixed = redecode(r.bodyAsBytes(), r.url().toString());
                    if (fixed != null) {
                        Logger.i(TAG, "网页编码异常，已自动重解码: " + target);
                        return fixed;
                    }
                }
                return doc;
            } catch (IOException e) {
                last = e;
                Logger.e(TAG, "抓取失败(" + (i + 1) + "/" + attempts + "): " + target + " -> " + friendlyMsg(e));
                try { Thread.sleep(400L * (1L << Math.min(i, 4))); } catch (InterruptedException ignored) {}
            }
        }
        throw new IOException(friendlyMsg(last) + "（已重试" + attempts + "次，站点可能暂时不可用或限制访问）");
    }

    /** 把底层网络异常翻译成用户能看懂的话 */
    private static String friendlyMsg(IOException e) {
        if (e == null) return "未知错误";
        String m = String.valueOf(e.getMessage());
        if (e instanceof java.net.SocketTimeoutException) return "连接超时";
        if (m.contains("unexpected end of stream")) return "服务器中断连接（网关不稳定或站点过载）";
        if (m.contains("502")) return "网关错误502（站点过载或已失效）";
        if (m.contains("503")) return "服务不可用503";
        if (m.contains("504")) return "网关超时504";
        if (m.contains("404")) return "页面不存在404";
        if (m.contains("403")) return "拒绝访问403";
        if (m.contains("Unable to resolve host") || m.contains("UnknownHost")) return "域名无法解析（站点可能已更换域名）";
        if (m.contains("SSL") || m.contains("ssl")) return "SSL握手失败";
        return m;
    }

    private static int countBad(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\uFFFD') n++;
        return n;
    }

    /** 用常见中文编码逐一重解码，替换符最少者胜 */
    private Document redecode(byte[] raw, String baseUri) {
        String[] charsets = {"UTF-8", "GBK", "GB18030", "Big5"};
        Document best = null;
        int bestBad = Integer.MAX_VALUE;
        for (String cs : charsets) {
            String html = new String(raw, java.nio.charset.Charset.forName(cs));
            int bad = countBad(html);
            if (bad < bestBad) {
                bestBad = bad;
                best = Jsoup.parse(html, baseUri);
            }
        }
        return best;
    }

    private String normalizeUrl(String url) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        return u;
    }

    private void checkCancel(CancelToken token) throws IOException {
        if (token != null && token.cancelled) throw new IOException("已取消");
    }
}
