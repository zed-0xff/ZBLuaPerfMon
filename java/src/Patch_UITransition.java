package me.zed_0xff.zb_lua_perf_mon;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.ui.UITransition", methodName = "UpdateAll")
public class Patch_UITransition {
    @Patch.OnEnter
    public static void enter() {
        PerfRenderer.render();
    }
}
