package com.noveldownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class Logger {
    private static final int MAX_LINES = 5000;
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static final SimpleDateFormat FILE_FMT =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    public interface LogListener {
        void onNewLog(String line);
    }

    private static final List<String> buffer = new ArrayList<>();
    private static final List<LogListener> listeners = new CopyOnWriteArrayList<>();
    private static File logFile;

    public static synchronized void init(File filesDir) {
        if (logFile == null && filesDir != null) {
            File dir = new File(filesDir, "logs");
            if (!dir.exists()) dir.mkdirs();
            logFile = new File(dir, "novel_" + FILE_FMT.format(new Date()) + ".log");
        }
    }

    public static void addListener(LogListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(LogListener l) {
        listeners.remove(l);
    }

    public static synchronized void i(String tag, String msg) {
        append("I", tag, msg);
    }

    public static synchronized void e(String tag, String msg) {
        append("E", tag, msg);
    }

    public static synchronized void e(String tag, String msg, Throwable t) {
        append("E", tag, msg + "\n  -> " + t);
    }

    private static void append(String level, String tag, String msg) {
        String line = TIME_FMT.format(new Date()) + " [" + level + "/" + tag + "] " + msg;
        buffer.add(line);
        if (buffer.size() > MAX_LINES) {
            buffer.subList(0, buffer.size() - MAX_LINES).clear();
        }
        writeToFile(line);
        for (LogListener l : listeners) {
            try { l.onNewLog(line); } catch (Throwable ignored) {}
        }
    }

    private static void writeToFile(String line) {
        if (logFile == null) return;
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(logFile, true), true)) {
            pw.println(line);
        } catch (Exception ignored) {}
    }

    public static synchronized List<String> getAll() {
        return new ArrayList<>(buffer);
    }

    public static synchronized void clear() {
        buffer.clear();
        i("Logger", "日志已清空");
    }

    public static File getLogFile() {
        return logFile;
    }
}
