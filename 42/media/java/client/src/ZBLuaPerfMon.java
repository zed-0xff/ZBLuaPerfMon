package me.zed_0xff.zb_lua_perf_mon;

public class ZBLuaPerfMon {
    public static int osdX = 0;
    public static int osdY = -1;
    public static float osdAlpha = 0.7f;
    public static float osdBackgroundAlpha = 0.5f;
    public static int osdWindowMS = 3_000;
    public static int osdUpdateIntervalMS = 1_000;
    public static int osdTopN = 10;
    public static double osdMinTimeMS = 0.1; // Minimum time in milliseconds to show in OSD

    public static void setOSDRenderX(int x) {
        osdX = x;
    }

    public static void setOSDRenderY(int y) {
        osdY = y;
    }

    public static void setOSDAlpha(float alpha) {
        osdAlpha = alpha;
    }

    public static void setOSDBackgroundAlpha(float alpha) {
        osdBackgroundAlpha = alpha;
    }

    public static void setOSDWindowMS(int windowMS) {
        osdWindowMS = windowMS;
    }

    public static void setOSDUpdateIntervalMS(int updateIntervalMS) {   
        osdUpdateIntervalMS = updateIntervalMS;
    }

    public static void setOSDTopN(int topN) {
        osdTopN = topN;
    }

    public static void setOSDMinTimeMS(double minTimeMS) {
        osdMinTimeMS = minTimeMS;
    }

    public static int logIntervalSeconds = 5;
    public static long minTimeMicroseconds = 10; // 10 microseconds = 10,000 nanoseconds
    public static boolean osdEnabled = true; // OSD is enabled by default
    public static boolean logWhenOSDOff = false; // Don't log when OSD is off by default
    public static boolean excludeGameEntries = false; // Don't track GAME entries by default

    public static void setLogIntervalSeconds(int seconds) {
        logIntervalSeconds = seconds;
        PerformanceMonitor.logIntervalSeconds = seconds;
    }

    public static void setLogEnabled(boolean enabled) {
        PerformanceMonitor.logEnabled = enabled;
    }

    public static void setMinTimeMicroseconds(long microseconds) {
        minTimeMicroseconds = microseconds;
        Patch_LuaCaller.minTimeNS = microseconds * 1000; // Convert to nanoseconds
    }

    public static void setOSDEnabled(boolean enabled) {
        osdEnabled = enabled;
    }

    public static void setLogWhenOSDOff(boolean enabled) {
        logWhenOSDOff = enabled;
    }

    public static boolean getOSDEnabled() {
        return osdEnabled;
    }

    public static boolean getLogWhenOSDOff() {
        return logWhenOSDOff;
    }

    public static void setExcludeGameEntries(boolean exclude) {
        excludeGameEntries = exclude;
        // Clear excluded keys when setting is turned off, so GAME entries can be tracked again
        if (!exclude) {
            PerformanceMonitor.clearExcludedSlowKeys();
        }
    }

    public static boolean getExcludeGameEntries() {
        return excludeGameEntries;
    }

    public static void setTrackInternalPerformance(boolean enabled) {
        PerformanceMonitor.trackInternalPerformance = enabled;
    }

    public static boolean getTrackInternalPerformance() {
        return PerformanceMonitor.trackInternalPerformance;
    }
}
