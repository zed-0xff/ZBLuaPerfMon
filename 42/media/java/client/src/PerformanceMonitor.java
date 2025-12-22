package me.zed_0xff.zb_lua_perf_mon;

import java.util.concurrent.ConcurrentHashMap;

public class PerformanceMonitor {
    public static final ConcurrentHashMap<Integer, TimingStats> statsMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, String> keyToName = new ConcurrentHashMap<>();
    public static final int WINDOW_SIZE = 1000; // Keep last 1000 samples per callee
    public static final int LOG_INTERVAL_SECONDS = 5; // Log stats every N seconds (default 5)
    
    private static volatile long lastLogTime = System.nanoTime();
    private static final Object logLock = new Object();
    
    public static void recordTiming(Object funcObj, long startTimeNs, long durationNanos) {
        // Use hashCode() for fast key generation
        int key = funcObj != null ? funcObj.hashCode() : 0;
        statsMap.computeIfAbsent(key, k -> new TimingStats(WINDOW_SIZE)).addSample(startTimeNs, durationNanos);
        
        if (key != 0 && !keyToName.containsKey(key)) {
            keyToName.put(key, getObjName(funcObj));
        }
    }
    
    // Fast key generation - just uses raw filename and line, no path parsing
    private static String getObjName(Object funcObj) {
        if (funcObj instanceof se.krka.kahlua.vm.LuaClosure) {
            se.krka.kahlua.vm.LuaClosure closure = (se.krka.kahlua.vm.LuaClosure) funcObj;
            if (closure.prototype != null) {
                String fname = closure.prototype.filename != null 
                    ? closure.prototype.filename 
                    : closure.prototype.file != null 
                        ? closure.prototype.file 
                        : "unknown";
                int line = closure.prototype.lines != null && closure.prototype.lines.length > 0
                    ? closure.prototype.lines[0]
                    : 0;
                return fname + ":" + line;
            }
        }
        if (funcObj != null) {
            return "(" + funcObj.getClass().getSimpleName() + ")" + funcObj.toString();
        }
        return "null";
    }
    
    public static void checkAndLogStatistics() {
        long currentTime = System.nanoTime();
        long timeSinceLastLog = currentTime - lastLogTime;
        long logIntervalNs = LOG_INTERVAL_SECONDS * 1_000_000_000L; // Convert seconds to nanoseconds
        
        // Quick check without synchronization for performance
        if (timeSinceLastLog >= logIntervalNs) {
            // Synchronize to ensure only one thread logs
            synchronized (logLock) {
                // Double-check after acquiring lock
                currentTime = System.nanoTime();
                if (currentTime - lastLogTime >= logIntervalNs) {
                    lastLogTime = currentTime;
                    logStatistics();
                }
            }
        }
    }
    
    private static void logStatistics() {
        long currentTimestampNs = System.nanoTime();
        long maxWindowSeconds = (WINDOW_SIZE * 50) / 1000; // Window size in buckets * 50ms per bucket / 1000 = seconds
        long windowDurationMS = Math.min(maxWindowSeconds, LOG_INTERVAL_SECONDS) * 1000;
        
        System.out.println("[ZBLuaPerfMon] ========== Statistics (top 50 by total time in last " + windowDurationMS + "ms window) ==========");
        
        // Print header once
        System.out.println("[ZBLuaPerfMon] Type      Total(ms)  Avg(ms)    Min(ms)    Max(ms)    Count  File:Line");
        System.out.println("[ZBLuaPerfMon] ----------------------------------------------------------------------");
        
        // Aggregate window stats and filter in one pass, then sort and limit to top 50
        // getWindowStats() returns null if no data in window, allowing efficient filtering
        statsMap.entrySet().stream()
            .map(entry -> {
                Integer key = entry.getKey();
                TimingStats stats = entry.getValue();
                // Get aggregated stats for the specified time window (returns null if no data)
                TimingStats.WindowStats windowStats = stats.getWindowStats(currentTimestampNs, windowDurationMS);
                if (windowStats == null) {
                    return null; // No data in window, skip this entry
                }
                String name = keyToName.getOrDefault(key, "unknown");
                // Resolve filename only when printing stats (and only for entries with data)
                FileInfo info = resolveKeyToFileInfo(name);
                return new StatsEntryWithWindow(info, stats, windowStats);
            })
            .filter(entry -> entry != null) // Filter out entries with no data in window
            .sorted((a, b) -> {
                // Sort by total time sum in the specified window (descending)
                double sumA = a.windowStats.getTotalSumMs();
                double sumB = b.windowStats.getTotalSumMs();
                return Double.compare(sumB, sumA);
            })
            .limit(50)
            .forEach(entry -> {
                FileInfo info = entry.info;
                TimingStats.WindowStats windowStats = entry.windowStats;
                
                String type = info.prefix != null ? info.prefix : "UNKNOWN";
                String paddedType = String.format("%-9s", type);
                String fileDisplay = info.relativePath + ":" + info.line;
                
                // Print just the values (no header labels) using window stats
                System.out.println(String.format(
                    "[ZBLuaPerfMon] %s %9.3f  %9.3f  %9.3f  %9.3f  %5d  %s",
                    paddedType,
                    windowStats.getTotalSumMs(),
                    windowStats.getAverageMs(),
                    windowStats.getMinMs(),
                    windowStats.getMaxMs(),
                    windowStats.count,
                    fileDisplay
                ));
            });
        System.out.println("[ZBLuaPerfMon] =============================================================");
    }
    
    // Resolve simple key (filename:line) to FileInfo with path parsing
    private static FileInfo resolveKeyToFileInfo(String name) {
        int colonIndex = name.lastIndexOf(':');
        if (colonIndex > 0) {
            String fname = name.substring(0, colonIndex);
            int line = 0;
            try {
                line = Integer.parseInt(name.substring(colonIndex + 1));
            } catch (NumberFormatException e) {
                // Ignore, line stays 0
            }
         
            // Now do the expensive path parsing
            FileInfo info = PathParser.getFileInfo(fname);
            if (info.prefix == null) {
                info.prefix = "UNKNOWN";
            }
            return new FileInfo(info.prefix, info.relativePath, line);
        }
        return new FileInfo("UNKNOWN", name, 0);
    }
    
    // Helper class for sorting with window stats
    private static class StatsEntryWithWindow {
        final FileInfo info;
        final TimingStats stats;
        final TimingStats.WindowStats windowStats;
        
        StatsEntryWithWindow(FileInfo info, TimingStats stats, TimingStats.WindowStats windowStats) {
            this.info = info;
            this.stats = stats;
            this.windowStats = windowStats;
        }
    }
}

