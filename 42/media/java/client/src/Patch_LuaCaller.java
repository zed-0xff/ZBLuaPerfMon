package me.zed_0xff.zb_lua_perf_mon;

import java.util.concurrent.Callable;

import me.zed_0xff.zombie_buddy.Patch;
import se.krka.kahlua.integration.LuaCaller;

public class Patch_LuaCaller {
    public static long minTimeNS = 10_000;

    public static void recordTime(Object fun, long duration, long startTime) {
        PerformanceMonitor.recordTiming(fun, startTime, duration);
        PerformanceMonitor.checkAndLogStatistics();
    }

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName="pcall")
    public static class Patch_pcall {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            startTime = System.nanoTime();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            if (duration >= minTimeNS) {
                recordTime(fun, duration, startTime);
            }
        }
    }

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName="pcallvoid")
    public static class Patch_pcallvoid {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            startTime = System.nanoTime();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            if (duration >= minTimeNS) {
                recordTime(fun, duration, startTime);
            }
        }
    }

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName="pcallBoolean")
    public static class Patch_pcallBoolean {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            startTime = System.nanoTime();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            if (duration >= minTimeNS) {
                recordTime(fun, duration, startTime);
            }
        }
    }
}
