package com.SirBlobman.combatlogx.expansion.compatibility.citizens.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;

import com.github.sirblobman.api.utility.Validate;
import com.SirBlobman.combatlogx.api.ICombatLogX;
import com.SirBlobman.combatlogx.api.event.PlayerPreTagEvent.TagType;
import com.SirBlobman.combatlogx.api.expansion.Expansion;
import com.SirBlobman.combatlogx.api.expansion.ExpansionManager;
import com.SirBlobman.combatlogx.api.expansion.noentry.NoEntryExpansion;
import com.SirBlobman.combatlogx.api.expansion.noentry.NoEntryHandler;
import com.SirBlobman.combatlogx.expansion.compatibility.citizens.CompatibilityCitizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;

public final class ListenerNPCMove extends BukkitRunnable {
    private final Map<UUID, Location> oldLocationMap = new HashMap<>();
    private final List<NoEntryHandler> noEntryHandlerList = new ArrayList<>();
    private final CompatibilityCitizens expansion;

    public ListenerNPCMove(CompatibilityCitizens expansion) {
        this.expansion = Validate.notNull(expansion, "expansion must not be null!");
    
        ICombatLogX combatLogX = this.expansion.getPlugin();
        JavaPlugin plugin = combatLogX.getPlugin();
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.scheduleSyncDelayedTask(plugin, this::register);
    }
    
    private void register() {
        ICombatLogX combatLogX = this.expansion.getPlugin();
        ExpansionManager expansionManager = combatLogX.getExpansionManager();
        List<Expansion> enabledExpansionList = expansionManager.getEnabledExpansions();
        
        for(Expansion expansion : enabledExpansionList) {
            if(expansion instanceof NoEntryExpansion) {
                NoEntryExpansion noEntryExpansion = (NoEntryExpansion) expansion;
                NoEntryHandler noEntryHandler = noEntryExpansion.getNoEntryHandler();
                this.noEntryHandlerList.add(noEntryHandler);
            }
        }
    
        JavaPlugin plugin = combatLogX.getPlugin();
        runTaskTimerAsynchronously(plugin, 2L, 2L);
    }

    @Override
    public void run() {
        NPCRegistry npcRegistry = CitizensAPI.getNPCRegistry();
        Set<Entry<UUID, Location>> entrySet = this.oldLocationMap.entrySet();
        
        for(Entry<UUID, Location> entry : entrySet) {
            UUID uuid = entry.getKey();
            NPC npc = npcRegistry.getByUniqueId(uuid);
            if(npc == null) continue;
            
            Location fromLocation = entry.getValue();
            Location toLocation = npc.getStoredLocation();
            boolean safeZone = isSafeZone(toLocation);
            
            double fromX = fromLocation.getX(), fromY = fromLocation.getY(), fromZ = fromLocation.getZ();
            double toX = toLocation.getX(), toY = toLocation.getY(), toZ = toLocation.getZ();
            if(safeZone && !(fromX == toX && fromY == toY && fromZ == toZ)) {
                syncTeleport(npc, fromLocation);
            }
        }
    }

    public void registerNPC(NPC npc) {
        UUID uuid = npc.getUniqueId();
        Location location = npc.getStoredLocation();
        this.oldLocationMap.put(uuid, location);
    }

    public void unregisterNPC(NPC npc) {
        UUID uuid = npc.getUniqueId();
        this.oldLocationMap.remove(uuid);
    }

    private boolean isSafeZone(Location location) {
        for(NoEntryHandler noEntryHandler : this.noEntryHandlerList) {
            if(noEntryHandler.isSafeZone(null, location, TagType.PLAYER)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void syncTeleport(NPC npc, Location location) {
        JavaPlugin plugin = this.expansion.getPlugin().getPlugin();
        Runnable task = () -> npc.teleport(location, TeleportCause.UNKNOWN);
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.scheduleSyncDelayedTask(plugin, task);
    }
}
