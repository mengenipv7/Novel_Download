package com.noveldownloader;

public class ChapterInfo {
    public final String title;
    public final String url;
    public String content;

    public ChapterInfo(String title, String url) {
        this.title = title != null ? title.trim() : "";
        this.url = url;
    }
}
