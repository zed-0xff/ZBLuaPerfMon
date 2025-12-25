package me.zed_0xff.zb_lua_perf_mon;

import java.util.concurrent.Callable;

import me.zed_0xff.zombie_buddy.Patch;
import se.krka.kahlua.integration.LuaCaller;

public class Patch_LuaCaller {
    public static long minTimeNS = 50_000;

    public static void recordTime(Object fun, long duration, long startTime) {
        // Skip statistics gathering if both OSD and logs are disabled
        if (!shouldGatherStatistics()) {
            return;
        }
        PerformanceMonitor.recordTiming(fun, startTime, duration);
        PerformanceMonitor.checkAndLogStatistics();
    }

    private static boolean shouldGatherStatistics() {
        // Gather statistics if:
        // - OSD is enabled (for display), OR
        // - Logging is enabled (for console output)
        // Note: logWhenOSDOff only affects whether logs are written when OSD is off,
        // but statistics gathering is controlled by logEnabled itself
        return ZBLuaPerfMon.osdEnabled || PerformanceMonitor.logEnabled;
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

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName="protectedCallBoolean")
    public static class Patch_protectedCallBoolean {
        @Patch.OnEnter
        public static void enter(@Patch.AllArguments Object[] args, @Patch.Local("startTime") long startTime) {
            // we need args[1] - functionObject
            if (args.length < 2)
                return;

            // because the original method calls pcallBoolean, which we already hook:
            //
            //     public Boolean protectedCallBoolean(KahluaThread thread, Object functionObject, Object[] args) {
            //         return pcallBoolean(thread, functionObject, args);
            //     }
            if (args.length > 1 && args[1] instanceof Object[])
                return;

            startTime = System.nanoTime();
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            if (startTime == 0)
                return;

            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            if (duration >= minTimeNS) {
                recordTime(fun, duration, startTime);
            }
        }
    }

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName="protectedCallVoid")
    public static class Patch_protectedCallVoid {
        @Patch.OnEnter
        public static void enter(@Patch.AllArguments Object[] args, @Patch.Local("startTime") long startTime) {
            // we need args[1] - functionObject
            if (args.length < 2)
                return;

            // because the original method calls pcallvoid, which we already hook:
            //
            //     public void protectedCallVoid(KahluaThread thread, Object functionObject, Object[] args) {
            //         pcallvoid(thread, functionObject, args);
            //     }
            if (args.length > 1 && args[1] instanceof Object[])
                return;

            startTime = System.nanoTime();   
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(1) Object fun, @Patch.Local("startTime") long startTime) {
            if (startTime == 0)
                return;

            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            if (duration >= minTimeNS) {
                recordTime(fun, duration, startTime);
            }
        }
    }
}
