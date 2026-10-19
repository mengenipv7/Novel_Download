package com.noveldownloader;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * TXT 导出工具（基于 SAF，无需存储权限）：
 * - writeToUri: 写入用户通过"另存为"选中的文档
 * - createInTree: 在用户通过"选择目录"授权的文件夹内新建文件
 */
public class FileExporter {

    private static final String TAG = "FileExporter";

    public static String sanitize(String name) {
        return name == null ? "未命名" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    }

    /** 整本书 TXT 全文 */
    public static String buildBookText(NovelResult novel) {
        StringBuilder sb = new StringBuilder();
        sb.append("书名: ").append(novel.title).append('\n');
        if (!novel.author.isEmpty()) sb.append("作者: ").append(novel.author).append('\n');
        sb.append("导出时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append('\n');
        sb.append("章节数: ").append(novel.chapters.size()).append('\n').append('\n');
        for (ChapterInfo c : novel.chapters) {
            sb.append(c.title).append('\n').append('\n');
            sb.append(c.content == null ? "" : c.content).append('\n').append('\n');
        }
        return sb.toString();
    }

    /** 单章 TXT 文本 */
    public static String buildChapterText(ChapterInfo c) {
        return c.title + "\n\n" + (c.content == null ? "" : c.content) + "\n";
    }

    /** 覆盖写入用户选中的文档 URI（来自 ACTION_CREATE_DOCUMENT） */
    public static void writeToUri(Context ctx, Uri uri, String text) throws Exception {
        try (OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new Exception("无法打开输出流");
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
        Logger.i(TAG, "已写入用户选择的文档: " + uri.getLastPathSegment());
    }

    /** 在用户授权的文件夹(treeUri)内新建文件并写入 */
    public static Uri createInTree(Context ctx, Uri treeUri, String fileName, String text) throws Exception {
        // createDocument 要求 document URI，不能直接用 tree URI
        Uri parentDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));
        Uri docUri = DocumentsContract.createDocument(
                ctx.getContentResolver(), parentDoc, "text/plain", fileName);
        if (docUri == null) throw new Exception("创建文件失败: " + fileName);
        writeToUri(ctx, docUri, text);
        return docUri;
    }
}
