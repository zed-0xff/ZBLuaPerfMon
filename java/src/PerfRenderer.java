package me.zed_0xff.zb_lua_perf_mon;

import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.ui.TextManager;
import zombie.ui.UIFont;
import java.util.List;

public class PerfRenderer {
    private static long lastUpdateTime = 0;
    private static List<FormattedCall> cachedTopCalls = new java.util.ArrayList<>();
    private static long cachedWindowDurationMS = 0;
    private static int allTimeMaxWidth = 0;
    private static int maxPrefixWidth = 0; // Track maximum prefix column width
    
    // Cached expensive-to-compute values (only recalculate when data or settings change)
    private static int cachedTopN = 0;
    private static int cachedX0 = Integer.MIN_VALUE;
    private static int cachedY0 = Integer.MIN_VALUE;
    private static int cachedScreenHeight = 0;
    private static String cachedLongestLine = "";
    
    // Cached rendering values (computed only when shouldUpdate is true)
    private static int cachedLineSpacing = 0;
    private static int cachedTotalHeight = 0;
    private static int cachedCurrentY = 0;
    private static int cachedBackgroundWidth = 0;
    private static int cachedBackgroundX = 0;
    
    // Pre-rendered line cache with colors
    private static class CachedLine {
        String text;
        double r, g, b; // RGB color values
        
        CachedLine(String text, double r, double g, double b) {
            this.text = text;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }
    private static List<CachedLine> cachedLines = new java.util.ArrayList<>();
    
    // Helper class to hold formatted call data with color info
    private static class FormattedCall {
        String timeStr;
        String countStr;
        String prefixStr;
        String pathStr;
        double r, g, b; // RGB color values
        
        FormattedCall(String timeStr, String countStr, String prefixStr, String pathStr, double r, double g, double b) {
            this.timeStr = timeStr;
            this.countStr = countStr;
            this.prefixStr = prefixStr;
            this.pathStr = pathStr;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        String getFullLine(int prefixWidth) {
            // Columns: time (variable, right-aligned), count (5 chars, right-aligned), 
            // prefix (variable width, left-aligned), path (left-aligned, variable width)
            String formattedPrefix = String.format("%-" + prefixWidth + "s", prefixStr);
            return timeStr + "  " + countStr + "  " + formattedPrefix + " " + pathStr;
        }
    }

    public static void render() {
        // Don't render if OSD is disabled
        if (!ZBLuaPerfMon.osdEnabled) {
            return;
        }

        if (PerformanceMonitor.trackInternalPerformance) {
            long renderStartNs = System.nanoTime();
            renderInternal();
            long renderDurationNs = System.nanoTime() - renderStartNs;
            
            int slowKey = -2;
            PerformanceMonitor.recordInternalPerformance(slowKey, renderStartNs, renderDurationNs);
        } else {
            renderInternal();
        }
    }
    
    private static void renderInternal() {
        var textMgr = TextManager.instance;
        if (textMgr == null) {
            return;
        }

        var core = Core.getInstance();
        if (core == null) {
            return;
        }

        // Check if we need to update based on update interval
        long currentTime = System.nanoTime();
        long updateIntervalNS = ZBLuaPerfMon.osdUpdateIntervalMS * 1_000_000L; // Convert ms to ns
        boolean shouldUpdate = (currentTime - lastUpdateTime) >= updateIntervalNS;

        // Get parameters from ZBLuaPerfMon
        int topN = ZBLuaPerfMon.osdTopN;
        long windowDurationMS = ZBLuaPerfMon.osdWindowMS;
        float alpha = ZBLuaPerfMon.osdAlpha;
        float backgroundAlpha = ZBLuaPerfMon.osdBackgroundAlpha;
        int x0 = ZBLuaPerfMon.osdX;
        int y0 = ZBLuaPerfMon.osdY;

        var font = UIFont.CodeSmall;
        var scrH = core.getScreenHeight();

        // Update cached data only when update interval has elapsed
        if (shouldUpdate) {
            cachedTopCalls = getTopCalls(topN, windowDurationMS);
            cachedWindowDurationMS = windowDurationMS;
            lastUpdateTime = currentTime;
            
            // Store settings for reference
            cachedTopN = topN;
            cachedX0 = x0;
            cachedY0 = y0;
            cachedScreenHeight = scrH;

            // Pre-format all lines with colors and find longest (expensive operations)
            String header = "Top " + topN + " Lua Calls (last " + (windowDurationMS / 1000) + "s):";
            cachedLines.clear();
            // Add header as first line (white color)
            cachedLines.add(new CachedLine(header, 1.0, 1.0, 1.0));
            cachedLongestLine = header;
            // Add formatted call lines with their colors
            for (FormattedCall call : cachedTopCalls) {
                String line = call.getFullLine(maxPrefixWidth);
                cachedLines.add(new CachedLine(line, call.r, call.g, call.b));
                if (line.length() > cachedLongestLine.length()) {
                    cachedLongestLine = line;
                }
            }
            // Measure longest line width (expensive operation)
            int maxWidth = textMgr.MeasureStringX(font, cachedLongestLine);
            if (maxWidth > allTimeMaxWidth) {
                allTimeMaxWidth = maxWidth;
            }
            
            // Compute and cache all rendering values
            int textHeight = textMgr.MeasureStringY(font, "XXX");
            cachedLineSpacing = textHeight + 2;
            cachedTotalHeight = (topN + 1) * cachedLineSpacing;
            cachedCurrentY = (y0 < 0) ? scrH + y0 - cachedTotalHeight + 1 : y0;
            cachedBackgroundWidth = allTimeMaxWidth + 10;
            cachedBackgroundX = x0 - 5;
        }

        // Draw dark background if opacity > 0
        if (backgroundAlpha > 0.0f) {
            SpriteRenderer.instance.renderRect(cachedBackgroundX, cachedCurrentY, cachedBackgroundWidth, cachedTotalHeight, 0.0f, 0.0f, 0.0f, backgroundAlpha);
        }

        // Draw all pre-rendered lines from cache
        int drawY = cachedCurrentY;
        for (CachedLine cachedLine : cachedLines) {
            textMgr.DrawString(font, x0, drawY, cachedLine.text, cachedLine.r, cachedLine.g, cachedLine.b, alpha);
            drawY += cachedLineSpacing;
        }
    }
        
    // Get top N calls for rendering (returns a list of formatted call data)
    public static java.util.List<FormattedCall> getTopCalls(int topN, long windowDurationMS) {
        java.util.List<FormattedCall> result = new java.util.ArrayList<>();
        double minTimeMS = ZBLuaPerfMon.osdMinTimeMS;
        int[] currentMaxPrefixWidth = {0}; // Use array to allow modification in lambda
        
        PerformanceMonitor.getTopEntries(windowDurationMS, topN)
            .forEach(entry -> {
                FileInfo info = entry.info;
                TimingStats.WindowStats windowStats = entry.windowStats;
                double totalMs = windowStats.getTotalSumMs();
                
                // Filter out entries below minimum time threshold
                if (totalMs < minTimeMS) {
                    return;
                }
                
                // Format time: if > 100ms, convert to seconds with 2 decimal places, otherwise show as ms with 1 decimal
                // Right-aligned in 8 character column
                String timeStr = String.format("%8.1fms", totalMs);
                double r, g, b;
                if (totalMs > 500.0) {
                    // > 500ms: slight red
                    r = 1.0;
                    g = 0.6;
                    b = 0.6;
                } else if (totalMs > 100.0) {
                    // > 100ms: slight yellow
                    r = 1.0;
                    g = 1.0;
                    b = 0.6;
                } else {
                    // <= 100ms: white
                    r = 1.0;
                    g = 1.0;
                    b = 1.0;
                }
                
                // Format count (right-aligned, 5 character column)
                String countStr = String.format("%5d", windowStats.count);
                
                // Extract first folder from path for LMOD/SMOD/WORKSHOP, otherwise use prefix
                FilePrefix prefix = info.prefix != null ? info.prefix : FilePrefix.UNK;
                String pathToDisplay = info.relativePath;
                String prefixStr;
                
                if (prefix == FilePrefix.INTERNAL) {
                    prefixStr = "LuaPerfMon";
                } else if (prefix == FilePrefix.LMOD || prefix == FilePrefix.SMOD || prefix == FilePrefix.WMOD) {
                    // Extract first folder from path
                    String normalizedPath = pathToDisplay.replace('\\', '/');
                    int firstSlash = normalizedPath.indexOf('/');
                    if (firstSlash > 0) {
                        String firstFolder = normalizedPath.substring(0, firstSlash);
                        // Remove first folder from path
                        pathToDisplay = normalizedPath.substring(firstSlash + 1);
                        prefixStr = firstFolder;
                    } else {
                        prefixStr = prefix.name();
                    }
                } else {
                    prefixStr = prefix.name();
                }
                
                // Update max prefix width for this batch
                int prefixLen = prefixStr.length();
                if (prefixLen > currentMaxPrefixWidth[0]) {
                    currentMaxPrefixWidth[0] = prefixLen;
                }
                
                // Format path (truncate if too long)
                // Don't show line number for internal performance tracking or when line is 0 and path doesn't look like a file
                String fileDisplay;
                if (info.line > 0 || (pathToDisplay.contains("/") || pathToDisplay.contains("\\"))) {
                    fileDisplay = pathToDisplay + ":" + info.line;
                } else {
                    fileDisplay = pathToDisplay;
                }
                String pathStr = fileDisplay.length() > 80 ? fileDisplay.substring(0, 77) + "..." : fileDisplay;
                
                result.add(new FormattedCall(timeStr, countStr, prefixStr, pathStr, r, g, b));
            });
        
        // Update global max prefix width (remember longest size)
        if (currentMaxPrefixWidth[0] > maxPrefixWidth) {
            maxPrefixWidth = currentMaxPrefixWidth[0];
        }
        
        return result;
    }
}
