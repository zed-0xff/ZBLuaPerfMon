-- ModOptions for ZBLuaPerfMon
-- This file creates the mod options UI and connects them to Java setters

local MOD_ID = "ZBLuaPerfMon"
local MOD_NAME = "LuaPerfMon"

local config = {
    osdX = nil,
    osdY = nil,
    osdAlpha = nil,
    osdBackgroundAlpha = nil,
    osdWindowMS = nil,
    osdUpdateIntervalMS = nil,
    osdTopN = nil,
    osdMinTimeMS = nil,
    excludeGameEntries = nil,
    logEnabled = nil,
    logWhenOSDOff = nil,
    logIntervalSeconds = nil,
    minTimeMicroseconds = nil,
    toggleOSDKey = nil,
}

local options = PZAPI.ModOptions:create(MOD_ID, MOD_NAME)

-- OSD (On-Screen Display) Settings
options:addTitle("OSD")

-- Toggle OSD Hotkey
config.toggleOSDKey = options:addKeyBind("toggleOSDKey", "Toggle OSD Hotkey", Keyboard.KEY_NONE, "Hotkey to toggle the OSD overlay on/off")

-- OSD Position X
config.osdX = options:addTextEntry("osdX", "X", "0", "Horizontal position of the OSD overlay. Negative values move left, positive values move right.")

-- OSD Position Y
config.osdY = options:addTextEntry("osdY", "Y", "-1", "Vertical position of the OSD overlay. -1 = bottom, negative values move up, positive values move down.")

-- OSD Alpha/Transparency
config.osdAlpha = options:addSlider("osdAlpha", "Alpha", 0.0, 1.0, 0.05, 0.7, "Transparency of the OSD overlay (0.0 = fully transparent, 1.0 = fully opaque)")

-- OSD Background Alpha/Transparency
config.osdBackgroundAlpha = options:addSlider("osdBackgroundAlpha", "Background Alpha", 0.0, 1.0, 0.05, 0.5, "Transparency of the dark background behind the OSD (0.0 = no background, 1.0 = fully opaque)")

-- OSD Window Duration
config.osdWindowMS = options:addSlider("osdWindowMS", "Time Window (ms)", 100, 30000, 100, 3000, "Time window in milliseconds for the OSD statistics (how far back to look)")

-- OSD Update Interval
config.osdUpdateIntervalMS = options:addSlider("osdUpdateIntervalMS", "Update Interval (ms)", 100, 3000, 100, 1000, "How often to update the OSD display in milliseconds")

-- OSD Top N Entries
config.osdTopN = options:addSlider("osdTopN", "Top N Entries", 5, 50, 1, 10, "Number of top entries to display in the OSD")

-- OSD Minimum Time Threshold
config.osdMinTimeMS = options:addTextEntry("osdMinTimeMS", "Min Time (ms)", "0.1", "Don't show entries with total time less than this value in milliseconds")

-- Exclude GAME Entries
config.excludeGameEntries = options:addTickBox("excludeGameEntries", "Exclude GAME Entries", false, "Don't track or display entries from the base game (GAME prefix)")

options:addSeparator()

-- Logging Settings
options:addTitle("Log")

-- Log Enabled
config.logEnabled = options:addTickBox("logEnabled", "Enable Logging", true, "Enable or disable console logging of performance statistics")

-- Log When OSD Off
config.logWhenOSDOff = options:addTickBox("logWhenOSDOff", "Log When OSD Off", false, "Continue writing logs even when OSD is disabled (default: off)")

-- Log Interval
config.logIntervalSeconds = options:addSlider("logIntervalSeconds", "Log Interval (seconds)", 1, 60, 1, 5, "How often to log performance statistics")

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
    
    -- Apply keybinding
    if config.toggleOSDKey then
        local keyCode = config.toggleOSDKey:getValue()
        if keyCode then
            -- Register the keybinding with the core
            getCore():addKeyBinding("Toggle LuaPerfMon OSD", tonumber(keyCode) or 0, 0, false, false, false)
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
