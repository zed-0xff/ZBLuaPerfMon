package me.zed_0xff.zb_lua_perf_mon;

import se.krka.kahlua.vm.LuaClosure;

public class LuaFileInfoExtractor {
    
    public static FileInfo getLuaFileInfoObj(Object funcObj) {
        if (funcObj instanceof LuaClosure) {
            LuaClosure closure = (LuaClosure) funcObj;
            if (closure.prototype != null) {
                String fname = closure.prototype.filename != null 
                    ? closure.prototype.filename 
                    : closure.prototype.file != null 
                        ? closure.prototype.file 
                        : "unknown";
                
                // Get prefix and relative path in one call
                FileInfo info = PathParser.getFileInfo(fname);
                
                int line = closure.prototype.lines != null && closure.prototype.lines.length > 0
                    ? closure.prototype.lines[0]
                    : 0;
                
                if (info.prefix == null) {
                    info.prefix = "UNKNOWN";
                }
                
                // Create a new FileInfo with the line number
                return new FileInfo(info.prefix, info.relativePath, line);
            }
        }
        return new FileInfo("UNKNOWN", funcObj != null ? funcObj.toString() : "null", 0);
    }
}

