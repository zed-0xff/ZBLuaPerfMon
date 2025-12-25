-- Main Lua file for ZBLuaPerfMon mod
-- Handles key bindings and initialization

local function onKeyPressed(key)
    if getCore():isKey("Toggle LuaPerfMon OSD", key) then
        local currentState = ZBLuaPerfMon.getOSDEnabled()
        ZBLuaPerfMon.setOSDEnabled(not currentState)
    end
end

local function onGameStart()
    Events.OnKeyPressed.Add(onKeyPressed)
end

Events.OnGameStart.Add(onGameStart)

