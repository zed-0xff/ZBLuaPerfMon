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
        // Advance buckets if needed (handle multiple bucket advances if time jumped)
        long timeSinceLastBucket = startTimeNs - lastBucketTime;
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
            lastBucketTime = startTimeNs;
        }
        
        // Get current timestamp bucket (holds samples from the last 50ms)
        BucketData currentBucket = buckets[currentBucketIndex];
        
        // Add sample to current timestamp bucket
        currentBucket.count++;
        currentBucket.sum += durationNanos;
        if (durationNanos < currentBucket.min) currentBucket.min = durationNanos;
        if (durationNanos > currentBucket.max) currentBucket.max = durationNanos;
        
        // Update global aggregated stats across all timestamp buckets
        totalCount++;
        totalSum += durationNanos;
        if (durationNanos < globalMin) globalMin = durationNanos;
        if (durationNanos > globalMax) globalMax = durationNanos;
    }
    
    // Move to next timestamp bucket (called when 50ms of wall-clock time has elapsed)
    // The old bucket (oldest in the window) is cleared and reused for the new 50ms window
    private void advanceTimeBucket() {
        // Clear the bucket we're about to overwrite
        BucketData oldBucket = buckets[currentBucketIndex];
        totalCount -= oldBucket.count;
        totalSum -= oldBucket.sum;
        
        // Reset the bucket
        oldBucket.count = 0;
        oldBucket.sum = 0;
        oldBucket.min = Long.MAX_VALUE;
        oldBucket.max = Long.MIN_VALUE;
        
        // Move to next bucket (round-robin)
        currentBucketIndex = (currentBucketIndex + 1) % windowSize;
        
        // Recalculate global min/max if needed
        if (oldBucket.min == globalMin || oldBucket.max == globalMax) {
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
    
    // Get aggregated stats for buckets within the specified time window (using current timestamp)
    // Uses round-robin nature to efficiently aggregate only relevant buckets
    // Returns null if there's no data in the window (allows efficient filtering)
    public synchronized WindowStats getWindowStats(long currentTimestampNs, long windowDurationMS) {
        // Calculate how many buckets represent this time window
        int numBuckets = (int) (windowDurationMS / (BUCKET_TIME_NS / 1_000_000));
        // Cap at windowSize
        if (numBuckets > windowSize) {
            numBuckets = windowSize;
        }
        
        // Ensure buckets are up to date for the current timestamp
        long timeSinceLastBucket = currentTimestampNs - lastBucketTime;
        if (timeSinceLastBucket >= BUCKET_TIME_NS) {
            int bucketsToAdvance = (int) (timeSinceLastBucket / BUCKET_TIME_NS);
            if (bucketsToAdvance > windowSize) {
                bucketsToAdvance = windowSize;
            }
            for (int i = 0; i < bucketsToAdvance; i++) {
                advanceTimeBucket();
            }
            lastBucketTime = currentTimestampNs;
        }
        
        long windowCount = 0;
        long windowSum = 0;
        long windowMin = Long.MAX_VALUE;
        long windowMax = Long.MIN_VALUE;
        
        // Aggregate data from the last numBuckets buckets using round-robin indexing
        // currentBucketIndex points to the most recent bucket (last 50ms)
        // We go back numBuckets buckets to cover the time window
        for (int i = 0; i < numBuckets; i++) {
            int bucketIndex = (currentBucketIndex - i + windowSize) % windowSize;
            BucketData bucket = buckets[bucketIndex];
            if (bucket.count > 0) {
                windowCount += bucket.count;
                windowSum += bucket.sum;
                if (bucket.min < windowMin) windowMin = bucket.min;
                if (bucket.max > windowMax) windowMax = bucket.max;
            }
        }
        
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
