local MOD_ID   = "ZBLuaPerfMon"
local MOD_NAME = "LuaPerfMon"

local config = {
    osdX                     = nil,
    osdY                     = nil,
    osdAlpha                 = nil,
    osdBackgroundAlpha       = nil,
    osdWindowMS              = nil,
    osdUpdateIntervalMS      = nil,
    osdTopN                  = nil,
    osdMinTimeMS             = nil,
    excludeGameEntries       = nil,
    logEnabled               = nil,
    logWhenOSDOff            = nil,
    logIntervalSeconds       = nil,
    minTimeMicroseconds      = nil,
    trackInternalPerformance = nil,
    toggleOSDKey             = nil,
    freezeOSDKey             = nil,
}

local options = PZAPI.ModOptions:create(MOD_ID, MOD_NAME)

-- OSD (On-Screen Display) Settings
options:addTitle("OSD")

config.toggleOSDKey = options:addKeyBind(  "toggleOSDKey", "Toggle OSD Hotkey", Keyboard.KEY_NONE, "Hotkey to toggle the OSD overlay on/off")
config.freezeOSDKey = options:addKeyBind(  "freezeOSDKey", "Freeze OSD Hotkey", Keyboard.KEY_NONE, "Hotkey to freeze/unfreeze the OSD display (pauses updating, allowing you to inspect the current stats)")

config.osdX     = options:addTextEntry("osdX", "X", "0", "Horizontal position of the OSD overlay. Negative values move left, positive values move right.")
config.osdY     = options:addTextEntry("osdY", "Y", "-1", "Vertical position of the OSD overlay. -1 = bottom, negative values move up, positive values move down.")
config.osdAlpha = options:addSlider(   "osdAlpha", "Alpha", 0.0, 1.0, 0.05, 0.7, "Transparency of the OSD overlay (0.0 = fully transparent, 1.0 = fully opaque)")

config.osdBackgroundAlpha = options:addSlider(
    "osdBackgroundAlpha",
    "Background Alpha",
     0.0,  -- min
     1.0,  -- max
     0.05, -- step
     0.5,  -- default
    "Transparency of the dark background behind the OSD (0.0 = no background, 1.0 = fully opaque)"
)

config.osdWindowMS = options:addSlider(
    "osdWindowMS", 
    "Time Window (ms)", 
     100, 
   30000, 
     100, 
    1000, 
    "Time window in milliseconds for the OSD statistics (how far back to look)"
)

config.osdUpdateIntervalMS = options:addSlider(
    "osdUpdateIntervalMS",
    "Update Interval (ms)",
     100,
    3000,
     100,
    1000,
    "How often to update the OSD display in milliseconds"
)

config.osdTopN = options:addSlider(
    "osdTopN",
    "Top N Entries",
     5,
    50,
     1,
    10,
    "Number of top entries to display in the OSD"
)

config.osdMinTimeMS = options:addTextEntry(
    "osdMinTimeMS",
    "Min Time (ms)",
    "0.1",
    "Don't show entries with total time less than this value (i.e. 0.1 is a 1/10000 of a second)"
)

config.excludeGameEntries = options:addTickBox("excludeGameEntries", "Exclude GAME Entries", false, "Don't track or display entries from the base game (GAME prefix)")

options:addSeparator()

-- Logging Settings
options:addTitle("Log")

config.logEnabled               = options:addTickBox("logEnabled", "Enable Logging", false, "Enable or disable console logging of performance statistics")
config.logWhenOSDOff            = options:addTickBox("logWhenOSDOff", "Log When OSD Off", false, "Continue writing logs even when OSD is disabled (default: off)")
config.logIntervalSeconds       = options:addSlider( "logIntervalSeconds", "Log Interval (seconds)", 1, 60, 1, 5, "How often to log performance statistics")
config.trackInternalPerformance = options:addTickBox("trackInternalPerformance", "Track LuaPerfMon Performance", false, "Track performance of the monitoring system itself")


-- Override the apply function to update Java values
options.apply = function(self)
    -- Apply OSD settings
    if config.osdX then
        local xValue = tonumber(config.osdX:getValue())
        if xValue then
            ZBLuaPerfMon.setOSDRenderX(xValue)
        end
    end
    if config.osdY then
        local yValue = tonumber(config.osdY:getValue())
        if yValue then
            ZBLuaPerfMon.setOSDRenderY(yValue)
        end
    end
    if config.osdAlpha then
        ZBLuaPerfMon.setOSDAlpha(config.osdAlpha:getValue())
    end
    if config.osdBackgroundAlpha then
        ZBLuaPerfMon.setOSDBackgroundAlpha(config.osdBackgroundAlpha:getValue())
    end
    if config.osdWindowMS then
        ZBLuaPerfMon.setOSDWindowMS(config.osdWindowMS:getValue())
    end
    if config.osdUpdateIntervalMS then
        ZBLuaPerfMon.setOSDUpdateIntervalMS(config.osdUpdateIntervalMS:getValue())
    end
    if config.osdTopN then
        ZBLuaPerfMon.setOSDTopN(config.osdTopN:getValue())
    end
    if config.osdMinTimeMS then
        local minTimeValue = tonumber(config.osdMinTimeMS:getValue())
        if minTimeValue then
            ZBLuaPerfMon.setOSDMinTimeMS(minTimeValue)
        end
    end
    if config.excludeGameEntries then
        ZBLuaPerfMon.setExcludeGameEntries(config.excludeGameEntries:getValue())
    end
    
    -- Apply logging settings
    if config.logEnabled then
        ZBLuaPerfMon.setLogEnabled(config.logEnabled:getValue())
    end
    if config.logWhenOSDOff then
        ZBLuaPerfMon.setLogWhenOSDOff(config.logWhenOSDOff:getValue())
    end
    if config.logIntervalSeconds then
        ZBLuaPerfMon.setLogIntervalSeconds(config.logIntervalSeconds:getValue())
    end
    if config.minTimeMicroseconds then
        ZBLuaPerfMon.setMinTimeMicroseconds(config.minTimeMicroseconds:getValue())
    end
    if config.trackInternalPerformance then
        ZBLuaPerfMon.setTrackInternalPerformance(config.trackInternalPerformance:getValue())
    end
    
    -- Apply keybindings
    if config.toggleOSDKey then
        local keyCode = config.toggleOSDKey:getValue()
        if keyCode then
            -- Register the keybinding with the core
            getCore():addKeyBinding("Toggle LuaPerfMon OSD", tonumber(keyCode) or 0, 0, false, false, false)
        end
    end
    if config.freezeOSDKey then
        local keyCode = config.freezeOSDKey:getValue()
        if keyCode then
            -- Register the keybinding with the core
            getCore():addKeyBinding("Freeze LuaPerfMon OSD", tonumber(keyCode) or 0, 0, false, false, false)
        end
    end
end

-- Apply settings when entering main menu (after loading saved options)
Events.OnMainMenuEnter.Add(function()
    options:apply()
end)

-- Apply settings when game starts
Events.OnGameStart.Add(function()
    options:apply()
end)
