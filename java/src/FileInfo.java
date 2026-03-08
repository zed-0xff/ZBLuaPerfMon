package me.zed_0xff.zb_lua_perf_mon;

public class FileInfo {
    public FilePrefix prefix;
    public String relativePath;
    public int line;
    
    public FileInfo(FilePrefix prefix, String relativePath) {
        this.prefix = prefix;
        this.relativePath = relativePath;
        this.line = 0;
    }
    
    public FileInfo(FilePrefix prefix, String relativePath, int line) {
        this.prefix = prefix;
        this.relativePath = relativePath;
        this.line = line;
    }
}

