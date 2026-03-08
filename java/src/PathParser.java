package me.zed_0xff.zb_lua_perf_mon;

import zombie.ZomboidFileSystem;
import zombie.core.znet.SteamWorkshop;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class PathParser {
    // Cache for resolved filenames to avoid repeated parsing
    private static final ConcurrentHashMap<String, FileInfo> filenameCache = new ConcurrentHashMap<>();
    
    public static FileInfo getFileInfo(String fname) {
        // Check cache first
        FileInfo cached = filenameCache.get(fname);
        if (cached != null) {
            // Return a new instance with the same data (since FileInfo may be modified)
            return new FileInfo(cached.prefix, cached.relativePath);
        }
        
        // Parse the filename
        FileInfo info = parseFileInfo(fname);
        
        // Cache the result (only cache if it's a reasonable size to avoid memory issues)
        if (fname.length() < 1000) { // Limit cache to reasonable path lengths
            filenameCache.put(fname, new FileInfo(info.prefix, info.relativePath));
        }
        
        return info;
    }
    
    private static FileInfo parseFileInfo(String fname) {
        try {
            if (ZomboidFileSystem.instance == null) {
                return new FileInfo(FilePrefix.UNK, fname);
            }

            String normalized_fname = fname.replace('\\', '/');
            String cacheDir = ZomboidFileSystem.instance.getCacheDir();
            String relativePath = fname;

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
                    return new FileInfo(FilePrefix.LMOD, relativePath);
                }

                // Check for WORKSHOP: cacheDir/workshop/... (case-insensitive)
                String workshopPrefixLower = (normalizedCacheDir + "/workshop/").toLowerCase();
                String normalized_fname_lower = normalized_fname.toLowerCase();
                if (normalized_fname_lower.startsWith(workshopPrefixLower)) {
                    // Find the actual position of "/workshop/" in the original path (case-insensitive)
                    int cacheDirEnd = normalizedCacheDir.length();
                    String afterCacheDir = normalized_fname.substring(cacheDirEnd);
                    String afterCacheDirLower = afterCacheDir.toLowerCase();
                    int workshopIndex = afterCacheDirLower.indexOf("/workshop/");
                    if (workshopIndex >= 0) {
                        // Extract everything after "/workshop/"
                        relativePath = afterCacheDir.substring(workshopIndex + "/workshop/".length());
                        // Remove leading separator if present
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }
                        return new FileInfo(FilePrefix.WMOD, relativePath);
                    }
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
                                return new FileInfo(FilePrefix.SMOD, relativePath);
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
                    return new FileInfo(FilePrefix.SMOD, relativePath);
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
                return new FileInfo(FilePrefix.GAME, relativePath);
            }

            // Couldn't determine source, return as-is
            return new FileInfo(FilePrefix.UNK, fname);
        } catch (Exception e) {
            return new FileInfo(FilePrefix.UNK, fname);
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
    
    // Clear cache if needed (e.g., when mods are reloaded)
    public static void clearCache() {
        filenameCache.clear();
    }
    
    // Get cache size for debugging
    public static int getCacheSize() {
        return filenameCache.size();
    }
}

