package me.zed_0xff.zb_lua_perf_mon;

import me.zed_0xff.zombie_buddy.Patch;

import zombie.core.Core;

@Patch(className = "zombie.ui.UIManager", methodName = "render")
public class Patch_ActiveMods {
    @Patch.OnEnter
    public static void render() {
        if (!Core.getInstance().uiRenderThisFrame) {
            return;
        }
        PerfRenderer.render();
    }
}
