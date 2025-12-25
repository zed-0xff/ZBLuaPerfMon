package me.zed_0xff.zb_lua_perf_mon;

public class TimingStats {
    private static final long BUCKET_TIME_NS = 50_000_000L; // Each bucket represents 50ms in nanoseconds
    
    // Round-robin array for storing timestamp bucket data
    // Each element represents a 50ms time window:
    // - buckets[currentBucketIndex]: last 50ms (most recent)
    // - buckets[(currentBucketIndex-1) % windowSize]: 50-100ms ago
    // - buckets[(currentBucketIndex-2) % windowSize]: 100-150ms ago
    // - etc.
    private static class BucketData {
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long bucketEndTimeNs = 0; // Timestamp when this bucket's time window ended (nanoseconds)
    }
    
    private final BucketData[] buckets; // Once-allocated round-robin array
    private int currentBucketIndex = 0; // Points to the current (most recent) timestamp bucket
    private final int windowSize; // Number of timestamp buckets to keep (total time window = windowSize * 50ms)
    private long lastBucketTime = System.nanoTime(); // Track when current bucket started
    
    // Aggregated stats across all buckets
    private long totalCount = 0;
    private long totalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = Long.MIN_VALUE;
    
    public TimingStats(int windowSize) {
        this.windowSize = windowSize;
        this.buckets = new BucketData[windowSize];
        // Initialize all buckets
        for (int i = 0; i < windowSize; i++) {
            buckets[i] = new BucketData();
        }
    }
    
    public synchronized void addSample(long startTimeNs, long durationNanos) {
        // Advance buckets based on current time (not sample start time) to keep buckets synchronized
        // This ensures all TimingStats instances advance at the same rate
        long currentTimeNs = System.nanoTime();
        long timeSinceLastBucket = currentTimeNs - lastBucketTime;
        if (timeSinceLastBucket >= BUCKET_TIME_NS) {
            // Calculate how many buckets to advance (could be more than 1 if time jumped)
            int bucketsToAdvance = (int) (timeSinceLastBucket / BUCKET_TIME_NS);
            // Cap at windowSize to avoid clearing all buckets at once
            if (bucketsToAdvance > windowSize) {
                bucketsToAdvance = windowSize;
            }
            // Advance each bucket
            for (int i = 0; i < bucketsToAdvance; i++) {
                advanceTimeBucket();
            }
            lastBucketTime = currentTimeNs;
        }
        
        // Get current timestamp bucket (holds samples from the last 50ms)
        BucketData currentBucket = buckets[currentBucketIndex];
        
        // Add sample to current timestamp bucket
        currentBucket.count++;
        currentBucket.sum += durationNanos;
        if (durationNanos < currentBucket.min) currentBucket.min = durationNanos;
        if (durationNanos > currentBucket.max) currentBucket.max = durationNanos;
        // Note: bucketEndTimeNs will be set when this bucket is advanced
        
        // Update global aggregated stats across all timestamp buckets
        totalCount++;
        totalSum += durationNanos;
        if (durationNanos < globalMin) globalMin = durationNanos;
        if (durationNanos > globalMax) globalMax = durationNanos;
    }
    
    // Move to next timestamp bucket (called when 50ms of wall-clock time has elapsed)
    // DON'T clear the current bucket - it may still be queried within the time window
    // Only clear a bucket when we're about to reuse it after a full round-robin cycle
    private void advanceTimeBucket() {
        long currentTimeNs = System.nanoTime();
        
        // Mark the current bucket's time window as ended (but keep its data!)
        BucketData currentBucket = buckets[currentBucketIndex];
        int oldIndex = currentBucketIndex;
        long oldCount = currentBucket.count;
        
        // Record when this bucket's time window ended
        if (currentBucket.count > 0) {
            currentBucket.bucketEndTimeNs = currentTimeNs;
        }
        
        // Move to next bucket (round-robin)
        int newIndex = (currentBucketIndex + 1) % windowSize;
        BucketData newBucket = buckets[newIndex];
        
        // Only clear the new bucket if it's being reused and is definitely too old
        // A bucket is too old if it's older than the maximum possible query window
        if (newBucket.count > 0 && newBucket.bucketEndTimeNs > 0) {
            long ageNs = currentTimeNs - newBucket.bucketEndTimeNs;
            long maxWindowNs = windowSize * BUCKET_TIME_NS; // Maximum possible query window
            if (ageNs > maxWindowNs) {
                // This bucket is definitely too old (older than max window), safe to clear
                totalCount -= newBucket.count;
                totalSum -= newBucket.sum;
                newBucket.count = 0;
                newBucket.sum = 0;
                newBucket.min = Long.MAX_VALUE;
                newBucket.max = Long.MIN_VALUE;
                // Keep bucketEndTimeNs for time calculations
            }
        }
        
        currentBucketIndex = newIndex;
        
        // DEBUG: Log bucket advancement (only called for Gauges functions, so always log if has data)
        // if (oldCount > 0) {
        //     DebugLogger.log(String.format("advanceBucket: bucket[%d] had count=%d, set endTime=%d, newIdx=%d",
        //         oldIndex, oldCount, currentBucket.bucketEndTimeNs, currentBucketIndex));
        // }
            
        // Recalculate global min/max if needed (only if we actually cleared a bucket)
        if (newBucket.count == 0 && (newBucket.min == globalMin || newBucket.max == globalMax)) {
            recalculateGlobalMinMax();
        }
    }
    
    private void recalculateGlobalMinMax() {
        globalMin = Long.MAX_VALUE;
        globalMax = Long.MIN_VALUE;
        for (BucketData bucket : buckets) {
            if (bucket.count > 0) {
                if (bucket.min < globalMin) globalMin = bucket.min;
                if (bucket.max > globalMax) globalMax = bucket.max;
            }
        }
    }
    
    public synchronized double getAverage() {
        return totalCount > 0 ? (totalSum / 1_000_000.0) / totalCount : 0.0;
    }
    
    public synchronized double getMin() {
        return globalMin == Long.MAX_VALUE ? 0.0 : globalMin / 1_000_000.0;
    }
    
    public synchronized double getMax() {
        return globalMax == Long.MIN_VALUE ? 0.0 : globalMax / 1_000_000.0;
    }
    
    public synchronized int getCount() {
        return (int) totalCount;
    }
    
    public synchronized double getTotalSum() {
        return totalSum / 1_000_000.0; // Convert to milliseconds
    }
    
    // Get aggregated stats for buckets within the specified time window
    // Uses round-robin nature to efficiently aggregate only relevant buckets
    // Returns null if there's no data in the window (allows efficient filtering)
    // Read-only: does not modify buckets (only addSample advances buckets)
    // Calculates which buckets to look at based on current time vs lastBucketTime
    public synchronized WindowStats getWindowStats(long windowDurationMS, String functionName) {
        // Don't advance buckets here - that should only happen in addSample
        // Advancing here would change lastBucketTime and break time calculations for old buckets
        long currentTimeNs = System.nanoTime();
        
        // Calculate how many buckets represent this time window
        // BUCKET_TIME_NS is in nanoseconds, windowDurationMS is in milliseconds
        // Convert: windowDurationMS * 1_000_000 / BUCKET_TIME_NS
        int numBuckets = (int) ((windowDurationMS * 1_000_000L) / BUCKET_TIME_NS);
        // Cap at windowSize
        if (numBuckets > windowSize) {
            numBuckets = windowSize;
        }
        // Ensure at least 1 bucket is checked (for very small windows)
        if (numBuckets < 1) {
            numBuckets = 1;
        }
        
        // Calculate the time window boundaries in nanoseconds
        long windowDurationNs = windowDurationMS * 1_000_000L;
        long windowStartTimeNs = currentTimeNs - windowDurationNs;
        
        long windowCount = 0;
        long windowSum = 0;
        long windowMin = Long.MAX_VALUE;
        long windowMax = Long.MIN_VALUE;
        
        // DEBUG: Print initial state (only for Gauges functions)
        // boolean shouldDebug = (functionName != null && functionName.contains("Gauges"));
        // if (shouldDebug) {
        //     DebugLogger.log(String.format("%s: window=%dms, currentIdx=%d, lastBucketTime=%d, windowStart=%d",
        //         functionName, windowDurationMS, currentBucketIndex, lastBucketTime, windowStartTimeNs));
        // }
        boolean shouldDebug = false;
        
        // Check all buckets and include those whose time window overlaps with the requested window
        // We need to check enough buckets to cover the full time window
        // Check all buckets to ensure we don't miss any (buckets wrap around in round-robin fashion)
        int bucketsChecked = 0;
        int bucketsWithData = 0;
        int bucketsIncluded = 0;
        for (int i = 0; i < windowSize; i++) {
            // Calculate bucket index going backwards from current
            // i=0 is current bucket, i=1 is previous, etc.
            int bucketIndex = (currentBucketIndex - i + windowSize) % windowSize;
            BucketData bucket = buckets[bucketIndex];
            bucketsChecked++;
            
            // Calculate time window even for empty buckets (for early exit optimization)
            long bucketWindowEndNs;
            long bucketWindowStartNs;
            
            if (i == 0) {
                // Current bucket: always use lastBucketTime to currentTimeNs (even if bucketEndTimeNs is set)
                // The current bucket is still active and hasn't been advanced yet
                // For window queries, only include current bucket if lastBucketTime is within the window
                // If lastBucketTime is too old, the current bucket shouldn't be included
                if (lastBucketTime < windowStartTimeNs) {
                    // Current bucket started before the window, so it's outside the window
                    // Skip it (but don't break, as older buckets might still be in the window)
                    continue;
                }
                bucketWindowStartNs = lastBucketTime;
                bucketWindowEndNs = currentTimeNs;
            } else if (bucket.bucketEndTimeNs > 0) {
                // Bucket has an end timestamp - use it to determine the window
                bucketWindowEndNs = bucket.bucketEndTimeNs;
                bucketWindowStartNs = bucketWindowEndNs - BUCKET_TIME_NS;
            } else {
                // Previous buckets without end timestamp: calculate from current time going backwards
                // Use currentTimeNs as reference, not lastBucketTime (which might be stale)
                // Bucket i was active i*50ms ago
                bucketWindowEndNs = currentTimeNs - ((i - 1) * BUCKET_TIME_NS);
                bucketWindowStartNs = bucketWindowEndNs - BUCKET_TIME_NS;
            }
            
            // Early exit: if we've gone far enough back that no bucket can overlap
            // Check if this bucket is completely before the window start
            // A bucket is too old if its end time is before the window start
            if (bucketWindowEndNs <= windowStartTimeNs) {
                // This bucket is completely before the window, and all subsequent buckets will be even older
                // We can break early since buckets are ordered by time (newest first)
                break;
            }
            
            // DEBUG: Log bucket contents when enumerating (if debug enabled)
            // if (shouldDebug) {
            //     String minStr = (bucket.min == Long.MAX_VALUE) ? "N/A" : String.valueOf(bucket.min);
            //     String maxStr = (bucket.max == Long.MIN_VALUE) ? "N/A" : String.valueOf(bucket.max);
            //     DebugLogger.log(String.format("  Enumerating Bucket[%d] (i=%d): count=%d, sum=%d, min=%s, max=%s, endTime=%d, window=[%d,%d]",
            //         bucketIndex, i, bucket.count, bucket.sum, minStr, maxStr, bucket.bucketEndTimeNs,
            //         bucketWindowStartNs, bucketWindowEndNs));
            // }
            
            if (bucket.count == 0) {
                continue; // Skip empty buckets (but we've already checked for early exit)
            }
            bucketsWithData++;
            
            // Include bucket if its time window overlaps with the requested window
            // Window is [windowStartTimeNs, currentTimeNs]
            // Bucket overlaps if: bucket ends after window starts AND bucket starts before window ends
            // This ensures at least some part of the bucket falls within the window
            // More strictly: bucket must have some time within [windowStartTimeNs, currentTimeNs]
            boolean overlaps = bucketWindowEndNs > windowStartTimeNs && bucketWindowStartNs < currentTimeNs;
            
            if (overlaps) {
                bucketsIncluded++;
                    windowCount += bucket.count;
                    windowSum += bucket.sum;
                    if (bucket.min < windowMin) windowMin = bucket.min;
                    if (bucket.max > windowMax) windowMax = bucket.max;
                // if (shouldDebug) {
                //     DebugLogger.log(String.format("  Bucket[%d] (i=%d): INCLUDED (total=%d)",
                //         bucketIndex, i, windowCount));
                // }
            } // else if (shouldDebug) {
            //     DebugLogger.log(String.format("  Bucket[%d] (i=%d): NO OVERLAP [%d,%d] vs [%d,%d]",
            //         bucketIndex, i, bucketWindowStartNs, bucketWindowEndNs, windowStartTimeNs, currentTimeNs));
            // }
        }
        
        // DEBUG: Print summary (only if debug enabled)
        // if (shouldDebug) {
        //     DebugLogger.log(String.format("Result: checked=%d, withData=%d, included=%d, count=%d",
        //         bucketsChecked, bucketsWithData, bucketsIncluded, windowCount));
        // }
        
        // Return null if no data in window (allows efficient filtering)
        if (windowCount == 0) {
            return null;
        }
        
        return new WindowStats(windowCount, windowSum, windowMin, windowMax);
    }
    
    // Helper class for window statistics
    public static class WindowStats {
        public final long count;
        public final long sum;
        public final long min;
        public final long max;
        
        public WindowStats(long count, long sum, long min, long max) {
            this.count = count;
            this.sum = sum;
            this.min = min;
            this.max = max;
        }
        
        public double getTotalSumMs() {
            return sum / 1_000_000.0;
        }
        
        public double getAverageMs() {
            return count > 0 ? (sum / 1_000_000.0) / count : 0.0;
        }
        
        public double getMinMs() {
            return min == Long.MAX_VALUE ? 0.0 : min / 1_000_000.0;
        }
        
        public double getMaxMs() {
            return max == Long.MIN_VALUE ? 0.0 : max / 1_000_000.0;
        }
    }
}
