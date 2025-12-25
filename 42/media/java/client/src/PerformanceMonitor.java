package me.zed_0xff.zb_lua_perf_mon;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class PerformanceMonitor {
    public static final ConcurrentHashMap<Integer, TimingStats> statsMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, String> slowKeyToName = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Integer> nameToSlowKey = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, Integer> fastKeyToSlowKey = new ConcurrentHashMap<>();
    public static final int WINDOW_SIZE = 1000; // Keep last 1000 samples per callee
    public static int logIntervalSeconds = 5; // Log stats every N seconds (default 5)
    public static boolean logEnabled = false;
    
    private static volatile long lastLogTime = System.nanoTime();
    private static final Object logLock = new Object();
    // Track active keys to avoid iterating all entries
    private static final ConcurrentHashMap<Integer, Long> activeKeys = new ConcurrentHashMap<>();
    private static final long ACTIVE_KEY_TTL_NS = 60_000_000_000L; // 60 seconds in nanoseconds
    // Cache of excluded slowKeys (GAME entries) to avoid repeated path parsing
    private static final Set<Integer> excludedSlowKeys = ConcurrentHashMap.newKeySet();
    
    public static void reset() {
        statsMap.clear();
        activeKeys.clear();
        fastKeyToSlowKey.clear();
        slowKeyToName.clear();
        nameToSlowKey.clear();
        excludedSlowKeys.clear();
        lastLogTime = System.nanoTime();
    }
    
    public static void clearExcludedSlowKeys() {
        excludedSlowKeys.clear();
    }

    public static void recordTiming(Object funcObj, long startTimeNs, long durationNanos) {
        int slowKey = 0;
        if (funcObj != null) {
            int fastKey = funcObj.hashCode(); // hash of function object, fast, but not unique
            slowKey = fastKeyToSlowKey.computeIfAbsent(fastKey, k -> {
                String name = getObjName(funcObj);
                int slowKey_ = name.hashCode(); // hash of function "filename:line" - slower, but unique
                
                // Check if this is a GAME entry and should be excluded (only check once per unique function)
                if (ZBLuaPerfMon.excludeGameEntries) {
                    FileInfo info = PathParser.getFileInfo(name);
                    if (info.prefix == FilePrefix.GAME) {
                        // Mark this slowKey as excluded and return a sentinel value
                        excludedSlowKeys.add(slowKey_);
                        return slowKey_;
                    }
                }
                
                slowKeyToName.put(slowKey_, name);
                nameToSlowKey.put(name, slowKey_);
                return slowKey_;
            });
            
            // Skip recording if this slowKey is excluded
            if (excludedSlowKeys.contains(slowKey)) {
                return; // Don't track GAME entries at all
            }
            
            // Get function name and skip all processing unless it contains "Gauges"
            String functionName = slowKeyToName.get(slowKey);
            // if (functionName == null || !functionName.contains("Gauges.lua:70")) {
            //     return; // Skip all processing for non-Gauges functions
            // }
            
            // DEBUG: Log when recording "Gauges" calls
            // DebugLogger.log(String.format("recordTiming: %s, duration=%.3fms, slowKey=%d",
            //     functionName, durationNanos / 1_000_000.0, slowKey));
            
            activeKeys.put(slowKey, startTimeNs);
            statsMap.computeIfAbsent(slowKey, k -> new TimingStats(WINDOW_SIZE)).addSample(startTimeNs, durationNanos);
        } else {
            // funcObj is null, skip processing
            return;
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
        // Skip logging if disabled
        if (!logEnabled) {
            return;
        }
        
        // Skip logging if OSD is off and logWhenOSDOff is false
        if (!ZBLuaPerfMon.osdEnabled && !ZBLuaPerfMon.logWhenOSDOff) {
            return;
        }
        
        long currentTime = System.nanoTime();
        long timeSinceLastLog = currentTime - lastLogTime;
        long logIntervalNs = logIntervalSeconds * 1_000_000_000L; // Convert seconds to nanoseconds
        
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
        // Calculate max window: WINDOW_SIZE buckets * 50ms per bucket
        long maxWindowMS = (long) WINDOW_SIZE * 50L;
        // Use the smaller of max window or log interval, but ensure at least 1 second
        long windowDurationMS = Math.min(maxWindowMS, (long) logIntervalSeconds * 1000L);
        // Ensure minimum window of 1 second to catch recent data
        if (windowDurationMS < 1000) {
            windowDurationMS = 1000;
        }
        
        System.out.println("[ZBLuaPerfMon] ========== Statistics (top 50 by total time in last " + windowDurationMS + "ms window) ==========");
        
        // Print header once
        System.out.println("[ZBLuaPerfMon] Type      Total(ms)  Avg(ms)    Min(ms)    Max(ms)    Count  File:Line");
        System.out.println("[ZBLuaPerfMon] ----------------------------------------------------------------------");
        
        // Get top entries and print them
        getTopEntries(windowDurationMS, 50)
            .forEach(entry -> {
                FileInfo info = entry.info;
                TimingStats.WindowStats windowStats = entry.windowStats;
                
                String type = info.prefix != null ? info.prefix.name() : FilePrefix.UNK.name();
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
                info.prefix = FilePrefix.UNK;
            }
            return new FileInfo(info.prefix, info.relativePath, line);
        }
        return new FileInfo(FilePrefix.UNK, name, 0);
    }
    
    // Helper class for sorting with window stats
    public static class StatsEntryWithWindow {
        final FileInfo info;
        final TimingStats stats;
        final TimingStats.WindowStats windowStats;
        
        StatsEntryWithWindow(FileInfo info, TimingStats stats, TimingStats.WindowStats windowStats) {
            this.info = info;
            this.stats = stats;
            this.windowStats = windowStats;
        }
    }
    
    // Shared method to get top entries - DRY principle
    // Only iterates through active keys, not all entries
    public static java.util.List<StatsEntryWithWindow> getTopEntries(long windowDurationMS, int limit) {
        long currentTimestampNs = System.nanoTime();
        
        // Clean up stale active keys
        activeKeys.entrySet().removeIf(entry -> {
            long timeSinceActive = currentTimestampNs - entry.getValue();
            return timeSinceActive > ACTIVE_KEY_TTL_NS;
        });
        
        // Only iterate through active keys, not all entries
        return activeKeys.keySet().stream()
            .map(key -> {
                TimingStats stats = statsMap.get(key);
                if (stats == null) {
                    return null;
                }
                String name = slowKeyToName.getOrDefault(key, "unknown");
                
                // getWindowStats is now read-only, doesn't need timestamp
                TimingStats.WindowStats windowStats = stats.getWindowStats(windowDurationMS, name);
                if (windowStats == null) {
                    return null;
                }
                FileInfo info = resolveKeyToFileInfo(name);
                return new StatsEntryWithWindow(info, stats, windowStats);
            })
            .filter(entry -> entry != null && entry.windowStats.count > 0)
            .sorted((a, b) -> {
                double sumA = a.windowStats.getTotalSumMs();
                double sumB = b.windowStats.getTotalSumMs();
                return Double.compare(sumB, sumA);
            })
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }

}

