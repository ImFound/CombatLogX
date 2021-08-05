package com.SirBlobman.combatlogx.expansion.compatibility.towny.listener;

import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.github.sirblobman.api.utility.Validate;
import com.SirBlobman.combatlogx.api.event.PlayerPreTagEvent;
import com.SirBlobman.combatlogx.expansion.compatibility.towny.CompatibilityTowny;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

public final class ListenerTownyMemberTagging implements Listener {
    private final CompatibilityTowny expansion;
    
    public ListenerTownyMemberTagging(CompatibilityTowny expansion) {
        this.expansion = Validate.notNull(expansion, "expansion must not be null!");
    }
    
    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void beforeTag(PlayerPreTagEvent e) {
        Player player = e.getPlayer();
        LivingEntity enemy = e.getEnemy();
        if(!(enemy instanceof Player)) return;
        
        Player enemyPlayer = (Player) enemy;
        if(shouldNotTag(player, enemyPlayer)) {
            e.setCancelled(true);
        }
    }
    
    private boolean shouldNotTag(Player player1, Player player2) {
        FileConfiguration configuration = this.expansion.getConfig("towny-compatibility.yml");
        if(!configuration.getBoolean("prevent-towny-member-tagging", true)) return false;
        
        Town town1 = getTown(player1);
        if(town1 == null) return false;
        
        Town town2 = getTown(player2);
        if(town2 == null) return false;
    
        UUID uuid1 = town1.getUUID(), uuid2 = town2.getUUID();
        return uuid1.equals(uuid2);
    }
    
    private Town getTown(Player player) {
        if(player == null) return null;
        
        TownyAPI api = TownyAPI.getInstance();
        Resident resident = api.getResident(player);
        if(resident == null) return null;
        
        try {
            return resident.getTown();
        } catch(NotRegisteredException ex) {
            return null;
        }
    }
}
