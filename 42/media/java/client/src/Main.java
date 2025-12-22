package me.zed_0xff.zb_lua_perf_mon;

import me.zed_0xff.zombie_buddy.Patch;
import se.krka.kahlua.vm.LuaClosure;
import zombie.ZomboidFileSystem;
import zombie.core.znet.SteamWorkshop;
import java.io.File;

public class Main {
    public static int pcallCount = 0;
    public static int pcallvoidCount = 0;
    
    // ThreadLocal to store start time for each thread
    public static final ThreadLocal<Long> startTime = new ThreadLocal<>();
    public static final ThreadLocal<Object> currentFuncObj = new ThreadLocal<>();

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName = "pcall")
    public class Patch_pcall {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(1) Object funcObj) {
            pcallCount++;
            if (pcallCount % 1000 == 0) {
                startTime.set(System.nanoTime());
                currentFuncObj.set(funcObj);
            }
        }
        
        @Patch.OnExit
        public static void exit() {
            if (startTime.get() != null) {
                long duration = System.nanoTime() - startTime.get();
                logFunc(currentFuncObj.get(), duration);
                startTime.remove();
                currentFuncObj.remove();
            }
        }
    }

    @Patch(className = "se.krka.kahlua.integration.LuaCaller", methodName = "pcallvoid")
    public class Patch_pcallvoid {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(1) Object funcObj) {
            pcallvoidCount++;
            if (pcallvoidCount % 1000 == 0) {
                startTime.set(System.nanoTime());
                currentFuncObj.set(funcObj);
            }
        }
        
        @Patch.OnExit
        public static void exit() {
            if (startTime.get() != null) {
                long duration = System.nanoTime() - startTime.get();
                logFunc(currentFuncObj.get(), duration);
                startTime.remove();
                currentFuncObj.remove();
            }
        }
    }

    public static void logFunc(Object funcObj, long durationNanos) {
        FileInfo info = getLuaFileInfoObj(funcObj);
        // Convert nanoseconds to milliseconds with 3 decimal places
        double durationMs = durationNanos / 1_000_000.0;
        // Format: TYPE TIME filename
        String type = info.prefix != null ? info.prefix : "UNKNOWN";
        String paddedType = String.format("%-9s", type);
        System.out.println("[ZBLuaPerfMon] " + paddedType + " " + String.format("%.3f", durationMs) + "ms " + info.relativePath + ":" + info.line);
    }
    
    private static FileInfo getLuaFileInfoObj(Object funcObj) {
        if (funcObj instanceof LuaClosure) {
            LuaClosure closure = (LuaClosure) funcObj;
            if (closure.prototype != null) {
                String fname = closure.prototype.filename != null 
                    ? closure.prototype.filename 
                    : closure.prototype.file != null 
                        ? closure.prototype.file 
                        : "unknown";
                
                // Get prefix and relative path in one call
                FileInfo info = getFileInfo(fname);
                
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

    private static class FileInfo {
        String prefix;
        String relativePath;
        int line;
        
        FileInfo(String prefix, String relativePath) {
            this.prefix = prefix;
            this.relativePath = relativePath;
            this.line = 0;
        }
        
        FileInfo(String prefix, String relativePath, int line) {
            this.prefix = prefix;
            this.relativePath = relativePath;
            this.line = line;
        }
    }

    
    private static FileInfo getFileInfo(String fname) {
        try {
            if (ZomboidFileSystem.instance == null) {
                return new FileInfo("UNKNOWN", fname);
            }

            String normalized_fname = fname.replace('\\', '/');
            String cacheDir = ZomboidFileSystem.instance.getCacheDir();
            String relativePath = fname;
            String prefix = null;

            // Check for LOCAL_MOD: cacheDir/mods/...
            if (cacheDir != null) {
                String normalizedCacheDir = cacheDir.replace('\\', '/');
                
                String localModsPrefix = normalizedCacheDir + "/mods/";
                if (normalized_fname.startsWith(localModsPrefix)) {
                    relativePath = normalized_fname.substring(localModsPrefix.length());
                    // Remove leading separator if present
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                    return new FileInfo("LOCAL_MOD", relativePath);
                }

                // Check for WORKSHOP: cacheDir/workshop/...
                String workshopPrefix = normalizedCacheDir + "/workshop/";
                if (normalized_fname.startsWith(workshopPrefix)) {
                    relativePath = normalized_fname.substring(workshopPrefix.length());
                    // Remove leading separator if present
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                    return new FileInfo("WORKSHOP", relativePath);
                }
            }

            // Check for STEAM_MOD: any path from GetInstalledItemFolders()
            if (SteamWorkshop.instance != null) {
                String[] steamFolders = SteamWorkshop.instance.GetInstalledItemFolders();
                if (steamFolders != null) {
                    for (String steamFolder : steamFolders) {
                        if (steamFolder != null) {
                            String normalizedSteamFolder = steamFolder.replace('\\', '/');
                            if (normalized_fname.startsWith(normalizedSteamFolder)) {
                                relativePath = normalized_fname.substring(normalizedSteamFolder.length());
                                // Remove leading separator if present
                                if (relativePath.startsWith("/")) {
                                    relativePath = relativePath.substring(1);
                                }
                                // Strip "mods/" prefix if present
                                String normalizedRelative = relativePath.replace('\\', '/');
                                if (normalizedRelative.startsWith("mods/")) {
                                    relativePath = relativePath.substring(5); // Skip "mods/"
                                } else if (normalizedRelative.startsWith("mods\\")) {
                                    relativePath = relativePath.substring(5); // Skip "mods\\"
                                }
                                // Strip "lua/" prefix if present for STEAM_MOD
                                normalizedRelative = relativePath.replace('\\', '/');
                                if (normalizedRelative.startsWith("lua/")) {
                                    relativePath = relativePath.substring(4); // Skip "lua/"
                                } else if (normalizedRelative.startsWith("lua\\")) {
                                    relativePath = relativePath.substring(4); // Skip "lua\\"
                                }
                                return new FileInfo("STEAM_MOD", relativePath);
                            }
                        }
                    }
                }
            }

            // Check for workshop/content pattern (Steam workshop downloads)
            // This is a fallback for paths that match the pattern but weren't caught by GetInstalledItemFolders()
            if (normalized_fname.contains("/workshop/content/")) {
                // Find the /mods/ folder - everything before it is the steamFolder path
                int modsIndex = normalized_fname.indexOf("/mods/");
                if (modsIndex >= 0) {
                    // Strip the steamFolder path (everything up to and including /mods/)
                    relativePath = normalized_fname.substring(modsIndex + 6); // Skip "/mods/"
                    // Remove leading separator if present
                    String normalizedRelative = relativePath.replace('\\', '/');
                    if (normalizedRelative.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                        normalizedRelative = relativePath.replace('\\', '/');
                    }
                    // Strip "mods/" prefix if still present
                    if (normalizedRelative.startsWith("mods/")) {
                        relativePath = relativePath.substring(5); // Skip "mods/"
                        normalizedRelative = relativePath.replace('\\', '/');
                    } else if (normalizedRelative.startsWith("mods\\")) {
                        relativePath = relativePath.substring(5); // Skip "mods\\"
                        normalizedRelative = relativePath.replace('\\', '/');
                    }
                    // Strip "lua/" prefix if present for STEAM_MOD
                    if (normalizedRelative.startsWith("lua/")) {
                        relativePath = relativePath.substring(4); // Skip "lua/"
                    } else if (normalizedRelative.startsWith("lua\\")) {
                        relativePath = relativePath.substring(4); // Skip "lua\\"
                    }
                    return new FileInfo("STEAM_MOD", relativePath);
                }
            }

            // Not a mod, check if it's a game file and strip game root directory
            relativePath = stripGameRootDir(fname);
            if (!relativePath.equals(fname)) {
                // Successfully stripped game root, it's a game file
                // Strip "media/lua/" prefix if present
                String normalizedRelative = relativePath.replace('\\', '/');
                if (normalizedRelative.startsWith("media/lua/")) {
                    // Work with normalized path for consistent substring calculation
                    relativePath = normalizedRelative.substring(10); // Skip "media/lua/" (10 chars)
                }
                return new FileInfo("GAME", relativePath);
            }

            // Couldn't determine source, return as-is
            return new FileInfo("UNKNOWN", fname);
        } catch (Exception e) {
            return new FileInfo("UNKNOWN", fname);
        }
    }
    

    private static String stripGameRootDir(String fname) {
        try {
            if (ZomboidFileSystem.instance != null && 
                ZomboidFileSystem.instance.base != null && 
                ZomboidFileSystem.instance.base.canonicalFile != null) {
                File rootDir = ZomboidFileSystem.instance.base.canonicalFile;
                String rootPath = rootDir.getAbsolutePath();
                
                // Normalize paths for comparison (handle both forward and backward slashes)
                String normalizedRoot = rootPath.replace('\\', '/');
                String normalizedFilename = fname.replace('\\', '/');
                
                // Remove trailing separator from root if present
                if (normalizedRoot.endsWith("/")) {
                    normalizedRoot = normalizedRoot.substring(0, normalizedRoot.length() - 1);
                }
                
                // If fname starts with root path, strip it
                if (normalizedFilename.startsWith(normalizedRoot)) {
                    String relative = normalizedFilename.substring(normalizedRoot.length());
                    // Remove leading separator if present
                    if (relative.startsWith("/") || relative.startsWith("\\")) {
                        relative = relative.substring(1);
                    }
                    return relative;
                }
            }
        } catch (Exception e) {
            // If anything goes wrong, just return the original fname
        }
        return fname;
    }


}