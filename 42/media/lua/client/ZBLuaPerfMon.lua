local function onKeyPressed(key)
    if getCore():isKey("Toggle LuaPerfMon OSD", key) then
        ZBLuaPerfMon.toggleOSD()
    elseif getCore():isKey("Freeze LuaPerfMon OSD", key) then
        ZBLuaPerfMon.toggleOSDFreeze()
    end
end

Events.OnKeyPressed.Add(onKeyPressed)
