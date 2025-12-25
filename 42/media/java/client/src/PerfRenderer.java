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

        // Update cached data if needed
        if (shouldUpdate || cachedTopCalls.isEmpty() || cachedWindowDurationMS != windowDurationMS) {
            cachedTopCalls = getTopCalls(topN, windowDurationMS);
            cachedWindowDurationMS = windowDurationMS;
            lastUpdateTime = currentTime;
        }

        var font = UIFont.CodeSmall;
        var scrH = core.getScreenHeight();

        // Measure text height for spacing
        String sampleText = "XXX";
        var textH = textMgr.MeasureStringY(font, sampleText);
        var lineSpacing = textH + 2; // Add 2 pixels spacing between lines

        // Calculate total height needed (header + topN entries)
        // Always use full height regardless of actual number of entries
        int totalHeight = (topN + 1) * lineSpacing;

        // Calculate Y position
        int currentY = y0;
        if (y0 < 0) {
            currentY = scrH + y0 - totalHeight + 1; // y0 is negative offset from bottom
        }

        // Calculate background width (find longest line, then measure it)
        String header = "Top " + topN + " Lua Calls (last " + (windowDurationMS / 1000) + "s):";
        String longestLine = header;
        if (cachedTopCalls.isEmpty()) {
            // If no data, show a placeholder message
            longestLine = header + " (no data)";
        } else {
        for (FormattedCall call : cachedTopCalls) {
            String line = call.getFullLine(maxPrefixWidth);
            if (line.length() > longestLine.length()) {
                longestLine = line;
                }
            }
        }
        int maxWidth = textMgr.MeasureStringX(font, longestLine);
        if (maxWidth > allTimeMaxWidth) {
            allTimeMaxWidth = maxWidth;
        } else {
            maxWidth = allTimeMaxWidth;
        }
        int backgroundWidth = maxWidth + 10; // Add padding
        int backgroundX = x0 - 5; // Add left padding

        // Draw dark background if opacity > 0
        if (backgroundAlpha > 0.0f) {
            SpriteRenderer.instance.renderRect(backgroundX, currentY, backgroundWidth, totalHeight, 0.0f, 0.0f, 0.0f, backgroundAlpha);
        }

        // Draw header at the top (always draw, even if no data)
        String headerText = cachedTopCalls.isEmpty() 
            ? header + " (no data)"
            : header;
        textMgr.DrawString(font, x0, currentY, headerText, 1.0, 1.0, 1.0, alpha);
        currentY += lineSpacing;

        // Draw each call below the header (in order: 1, 2, 3, ...)
        for (int i = 0; i < cachedTopCalls.size(); i++) {
            FormattedCall call = cachedTopCalls.get(i);
            String line = call.getFullLine(maxPrefixWidth);
            // Use stored RGB color values
            textMgr.DrawString(font, x0, currentY, line, call.r, call.g, call.b, alpha);
            currentY += lineSpacing;
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
