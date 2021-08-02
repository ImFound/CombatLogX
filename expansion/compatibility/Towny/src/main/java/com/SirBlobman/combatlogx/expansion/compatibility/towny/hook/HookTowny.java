package com.SirBlobman.combatlogx.expansion.compatibility.towny.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyWorld;
import io.github.townyadvanced.flagwar.FlagWarAPI;

public final class HookTowny {
    public static boolean isSafeZone(Location location) {
        TownBlock townBlock = getTownBlock(location);
        if(townBlock == null) return false;
        
        TownyAPI townyAPI = TownyAPI.getInstance();
        if(townyAPI.isWarTime()) return false;
        
        TownyWorld townyWorld = townBlock.getWorld();
        if(townyWorld == null || townyWorld.isForcePVP()) return false;
        
        Town town;
        try {
            town = townBlock.getTown();
            if(town == null || town.isPVP() || town.isAdminEnabledPVP()) return false;
        } catch(NotRegisteredException ex) {
            return false;
        }
        
        PluginManager pluginManager = Bukkit.getPluginManager();
        if(pluginManager.isPluginEnabled("FlagWar")) {
            if(FlagWarAPI.isUnderAttack(town)) return false;
        }
        
        TownyPermission townBlockPermissions = townBlock.getPermissions();
        return !townBlockPermissions.pvp;
    }
    
    private static TownBlock getTownBlock(Location location) {
        TownyAPI townyAPI = TownyAPI.getInstance();
        return townyAPI.getTownBlock(location);
    }
}
