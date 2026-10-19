# 小说下载器（Android）

输入小说**目录页 URL**，自动解析目录、抓取正文、导出整本 TXT 的安卓应用。

当前版本：**v1.0.18（build 19）**，以 [version.properties](version.properties) 为准；每次发布都会同步更新本文件的「版本记录」。

## 功能特性

### 目录解析
- 支持多页目录自动合并（「下一页 / 查看更多章节 / 加载更多 / 展开更多」等触发词，含数字分页链接）
- 页首「最新章节」预览块与正文目录自动去重
- **按章节号升序重排**：网页把目录分页顺序打乱（如第 101-323 章排在前面、第 1-100 章排在后面）时自动纠正
- 例外：识别到**分卷重新编号**（每卷都从第 1 章重新起算、段间编号重叠）时保留网页原顺序，避免各卷章节交错

### 正文抓取
- 章节内分页全覆盖：`123_2.html` 派生式、同路径不同参数式、目录式（`123.html → 123/2.html → 123/3.html`）
- 分页边界按 URL 结构判断（不依赖标题变化），避免把分页页误判成下一章
- 分页拼接去重：重叠段（上一页后缀 = 新页前缀）截断、累积式分页（新页完整包含上一页）替换
- 固定页眉剥离：与上一页相同且长度 ≥ 30 字的整行去除，剩余内容照常保留
- 广告行 / 导航行 / 页码行 / 「本站提示」行过滤；`<script>`、`<style>` 等噪声剔除
- 软 404 防护：分页返回内容与首页高度重复时停止，避免死循环
- **脚本动态正文兜底**：站点静态页只返回「请勿开启浏览器阅读模式，否则将导致章节内容缺失」这类提示页时，改用系统 WebView 渲染（执行站点脚本）后再取正文
- GBK / UTF-8 编码自适应；移动端与桌面端 UA 自动重试

### 下载与导出
- 3 线程抓取（降低触发站点限流的概率）
- 断点续传：导出时自动跳过已有正文的章节，只补抓缺失内容；单章正文缺失不清空其它章节
- 章节预览、整本导出 TXT、单章存 TXT、运行日志查看与导出、随时取消

## 使用说明

1. 在浏览器打开小说**目录页**，复制地址（形如 `https://站点/chapter/12345/` 或 `.../ml1.html`）
2. 粘贴到 App 输入框，点「解析」
3. 目录解析完成后：
   - 点击任意章节可**预览正文**（含分页合并结果）
   - 点「导出 txt」抓取整本并保存到「下载目录」（可在 App 顶部查看路径）
4. 首页右上角图标可查看**运行日志**，排障时可直接导出日志文本

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `app/src/main/java/com/noveldownloader/` | App 源码 |
| `├─ MainActivity.java` | 首页：解析目录、发起下载、导出、日志入口 |
| `├─ ChapterPreviewActivity.java` | 章节正文预览、单章存 TXT |
| `├─ ChapterAdapter.java` | 章节目录列表适配器 |
| `├─ DownloadTask.java` | 多线程抓取调度、进度与取消 |
| `├─ NovelParser.java` | 解析核心：目录/分页/正文抽取与清洗（纯 Java，可离线测试） |
| `├─ WebViewRenderer.java` | 动态正文兜底：WebView 渲染后取正文 |
| `├─ FileExporter.java` | TXT 导出（含断点续传） |
| `├─ LogActivity.java` / `Logger.java` | 运行日志查看、导出与统一日志 |
| `├─ NovelResult.java` / `ChapterInfo.java` | 数据结构 |
| `parser-test/` | 解析器本地仿真测试 |
| `├─ FixtureServer.java` | 本地夹具站（site1~site7），`http://localhost:18091` |
| `├─ TestParser.java` | 断言集（目录合并、排序、分页、去重、动态正文兜底等） |
| `├─ *.html` | 真实站点离线样本与夹具页 |
| `build.ps1` | 一键构建：递增版本号 → 编译 → 输出 `builds/novel-downloader-vX.Y.Z-bN.apk` |
| `version.properties` | 版本号来源（`VERSION_CODE` / `VERSION_NAME`） |

## 构建

环境要求：**JDK 17**、Android SDK（`compileSdk 36`，`minSdk 24`）。

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

脚本会：递增 `version.properties` → 执行 `gradlew :app:assembleRelease` → 复制产物到 `builds/novel-downloader-vX.Y.Z-bN.apk`。

签名说明：release 构建读取 `keystore/keystore.properties` 与 `keystore/release.keystore`；两者**不入库**（见 `.gitignore`），克隆后如需自行签名请自备 keystore。`local.properties`（Android SDK 路径）同样不入库。

## 本地解析器仿真测试

`NovelParser` 不依赖 Android，可脱离手机直接跑断言集：

```powershell
javac -encoding UTF-8 -cp parser-test\jsoup-1.18.3.jar -d parser-test\out `
  app\src\main\java\com\noveldownloader\NovelParser.java `
  app\src\main\java\com\noveldownloader\ChapterInfo.java `
  app\src\main\java\com\noveldownloader\NovelResult.java `
  app\src\main\java\com\noveldownloader\Logger.java `
  parser-test\FixtureServer.java parser-test\TestParser.java

Start-Process java -ArgumentList "-cp","parser-test\out;parser-test\jsoup-1.18.3.jar","FixtureServer"
java -cp "parser-test\out;parser-test\jsoup-1.18.3.jar" TestParser
```

注意：跑测试前先清理占用 **18091** 端口的旧夹具进程，否则新夹具不生效。

## 版本记录

### v1.0.18（build 19）
- 新增**浏览器渲染兜底**：静态页只返回「阅读模式」提示页的站点（如 55小说网、策书网模板），改用 WebView 渲染执行脚本后取正文；提示语行同时加入广告过滤，不再残留
- 仅当静态正文缺失（找不到正文容器 / 正文命中提示页特征 / 短于 60 字）时才触发渲染，正常站点抓取速度不受影响

### v1.0.17（build 18）
- 修正**目录排序**：网页把目录分页顺序轮转打乱（第 101-323 章在前、第 1-100 章在后）时按章节号重排；判据为「长段后回落到第 1 章且章节号全局不重复」
- 保留分卷重新编号的原顺序（编号重叠即视为多卷），避免各卷章节交错

### v1.0.16（build 17）
- 基线版本：目录排序（按章节号升序）、正文分页边界、断点续传、日志导出等已修复问题的合集

## 相关文件

- `novel-downloader.html` + `cors-proxy.py`：早期**网页版**工具（仓库历史遗留文件，与 App 功能独立）
- `小说下载器.apk`：仓库根目录保留的最新 APK，方便手机直接安装

## 免责声明

本项目仅供个人学习与离线阅读使用，请遵守目标站点robots 与版权规定，控制访问频率，勿用于任何商业用途。
