package me.zed_0xff.zb_lua_perf_mon;

import me.zed_0xff.zombie_buddy.Exposer;

public class Main {
    public static void main(String[] args) {
        Exposer.exposeClassToLua(ZBLuaPerfMon.class);
    }
}
