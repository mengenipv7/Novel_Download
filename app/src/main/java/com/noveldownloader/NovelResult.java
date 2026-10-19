package com.noveldownloader;

import java.util.List;

public class NovelResult {
    public final String title;
    public final String author;
    public final List<ChapterInfo> chapters;

    public NovelResult(String title, String author, List<ChapterInfo> chapters) {
        this.title = title != null ? title.trim() : "";
        this.author = author != null ? author.trim() : "";
        this.chapters = chapters;
    }
}
