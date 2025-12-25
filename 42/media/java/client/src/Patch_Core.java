package me.zed_0xff.zb_lua_perf_mon;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.Core", methodName = "ResetLua")
public class Patch_Core {
    @Patch.OnEnter
    public static void enter() {
        PerformanceMonitor.reset();
    }
}
