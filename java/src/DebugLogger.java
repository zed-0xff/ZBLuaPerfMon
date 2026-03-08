package me.zed_0xff.zb_lua_perf_mon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import zombie.ZomboidFileSystem;

public class DebugLogger {
    private static PrintWriter logWriter = null;
    private static final Object lock = new Object();
    private static final String LOG_FILE = "ZBLuaPerfMon_debug.log";
    
    private static PrintWriter getWriter() {
        if (logWriter == null) {
            synchronized (lock) {
                if (logWriter == null) {
                    try {
                        // Get cache directory from ZomboidFileSystem
                        String cacheDir = ZomboidFileSystem.instance.getCacheDir();
                        File logFile = new File(cacheDir, LOG_FILE);
                        // Ensure parent directory exists
                        logFile.getParentFile().mkdirs();
                        // Recreate file if it exists (don't append)
                        logWriter = new PrintWriter(new FileWriter(logFile, false), false);
                    } catch (IOException e) {
                        // Fallback to System.out if file write fails
                        System.err.println("[ZBLuaPerfMon] Failed to open debug log file: " + e.getMessage());
                        return null;
                    }
                }
            }
        }
        return logWriter;
    }
    
    public static void log(String message) {
        PrintWriter writer = getWriter();
        if (writer != null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            writer.println("[" + timestamp + "] " + message);
        } else {
            // Fallback to System.out if file logging fails
            System.out.println(message);
        }
    }
    
    public static void close() {
        synchronized (lock) {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
        }
    }
}

