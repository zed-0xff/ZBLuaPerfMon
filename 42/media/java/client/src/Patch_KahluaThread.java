// package me.zed_0xff.zb_lua_perf_mon;

// import java.util.concurrent.Callable;

// import me.zed_0xff.zombie_buddy.Patch;
// import se.krka.kahlua.vm.KahluaThread;

// public class Patch_KahluaThread {
//     public static long minTimeNS = 10_000; // 10us

//     @Patch(className = "se.krka.kahlua.vm.KahluaThread", methodName="pcall")
//     public static class Patch_pcall {
//         @Patch.OnEnter
//         public static void enter(@Patch.Argument(0) Object fun, @Patch.Local("startTime") long startTime) {
//             startTime = System.nanoTime();
//         }

//         @Patch.OnExit
//         public static void exit(@Patch.Argument(0) Object fun, @Patch.Local("startTime") long startTime) {
//             long endTime = System.nanoTime();
//             long duration = endTime - startTime;
//             if (duration >= minTimeNS) {
//                 long timestampMs = System.currentTimeMillis();
//                 PerformanceMonitor.recordTiming(fun, duration, timestampMs);
//                 PerformanceMonitor.checkAndLogStatistics();
//             }
//         }
//     }

//     @Patch(className = "se.krka.kahlua.vm.KahluaThread", methodName="pcallvoid")
//     public static class Patch_pcallvoid {
//         @Patch.OnEnter
//         public static void enter(@Patch.Argument(0) Object fun, @Patch.Local("startTime") long startTime) {
//             startTime = System.nanoTime();
//         }

//         @Patch.OnExit
//         public static void exit(@Patch.Argument(0) Object fun, @Patch.Local("startTime") long startTime) {
//             long endTime = System.nanoTime();
//             long duration = endTime - startTime;
//             if (duration >= minTimeNS) {
//                 long timestampMs = System.currentTimeMillis();
//                 PerformanceMonitor.recordTiming(fun, duration, timestampMs);
//                 PerformanceMonitor.checkAndLogStatistics();
//             }
//         }
//     }

//     @Patch(className = "se.krka.kahlua.vm.KahluaThread", methodName="pcallBoolean")
//     public static class Patch_pcallBoolean {
//         @Patch.OnEnter
//         public static void enter(@Patch.Argument(0) Object fun, @Patch.Local("startTime") long startTime) {
//             startTime = System.nanoTime();
//         }

//         @Patch.OnExit
//         public static void exit(@Patch.Argument(0) Object fun, @Patch.Local("startTime") long startTime) {
//             long endTime = System.nanoTime();
//             long duration = endTime - startTime;
//             if (duration >= minTimeNS) {
//                 long timestampMs = System.currentTimeMillis();
//                 PerformanceMonitor.recordTiming(fun, duration, timestampMs);
//                 PerformanceMonitor.checkAndLogStatistics();
//             }
//         }
//     }
// }
