package me.zed_0xff.zb_lua_perf_mon;

public class FileInfo {
    public String prefix;
    public String relativePath;
    public int line;
    
    public FileInfo(String prefix, String relativePath) {
        this.prefix = prefix;
        this.relativePath = relativePath;
        this.line = 0;
    }
    
    public FileInfo(String prefix, String relativePath, int line) {
        this.prefix = prefix;
        this.relativePath = relativePath;
        this.line = line;
    }
}

