package com.SirBlobman.combatlogx.expansion.mob.tagger.listener;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;

import com.SirBlobman.combatlogx.api.ICombatLogX;
import com.SirBlobman.combatlogx.api.event.PlayerPreTagEvent;
import com.SirBlobman.combatlogx.api.event.PlayerPreTagEvent.TagReason;
import com.SirBlobman.combatlogx.api.event.PlayerPreTagEvent.TagType;
import com.SirBlobman.combatlogx.api.utility.ICombatManager;
import com.SirBlobman.combatlogx.expansion.mob.tagger.MobTagger;

public final class ListenerMobCombat implements Listener {
    private final MobTagger expansion;

    public ListenerMobCombat(MobTagger expansion) {
        this.expansion = expansion;
    }
    
    @EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
    public void onAttack(EntityDamageByEntityEvent e) {
        Entity damager = linkPet(linkProjectile(e.getDamager()));
        Entity damaged = e.getEntity();
        
        if(!(damaged instanceof LivingEntity)) return;
        LivingEntity attacked = (LivingEntity) damaged;
        
        if(!(damager instanceof LivingEntity)) return;
        LivingEntity attacker = (LivingEntity) damager;
    
        ICombatManager combatManager = this.expansion.getPlugin().getCombatManager();
        if(attacked instanceof Player && !(attacker instanceof Player)) {
            Player player = (Player) attacked;
            combatManager.tag(player, attacker, TagType.MOB, TagReason.ATTACKED);
        }
        
        if(attacker instanceof Player && !(attacked instanceof Player)) {
            Player player = (Player) attacker;
            combatManager.tag(player, attacked, TagType.MOB, TagReason.ATTACKER);
        }
    }
    
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void beforeTag(PlayerPreTagEvent e) {
        printDebug("Detected PlayerPreTagEvent...");

        LivingEntity enemy = e.getEnemy();
        if(enemy == null || enemy instanceof Player) {
            printDebug("Enemy is player or null, ignoring event.");
            return;
        }

        printDebug("Checking if enemy EntityType is disabled...");
        if(checkMobTypeDisabled(enemy)) {
            printDebug("Enemy EntityType is disabled, cancelling event.");
            e.setCancelled(true);
            return;
        }

        printDebug("Checking if enemy SpawnReason is disabled...");
        if(checkMobSpawnReasonDisabled(enemy)) {
            printDebug("Enemy SpawnReason is disabled, cancelling event.");
            e.setCancelled(true);
            return;
        }

        printDebug("Finished PlayerPreTagEvents without cancellation.");
    }
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSpawn(CreatureSpawnEvent e) {
        LivingEntity entity = e.getEntity();
        SpawnReason spawnReason = e.getSpawnReason();
    
        FixedMetadataValue fixedValue = new FixedMetadataValue(this.expansion.getPlugin().getPlugin(), spawnReason);
        entity.setMetadata("combatlogx_spawn_reason", fixedValue);
    }
    
    private boolean checkMobTypeDisabled(LivingEntity enemy) {
        FileConfiguration configuration = this.expansion.getConfig("mob-tagger.yml");
        boolean mobsDisabled = !configuration.getBoolean("tag-players");
        if(mobsDisabled) return true;

        EntityType enemyType = enemy.getType();
        String enemyTypeName = enemyType.name();
        List<String> mobTypeList = configuration.getStringList("mob-list");

        boolean mobListInverted = configuration.getBoolean("mob-list-inverted");
        boolean containsAll = mobTypeList.contains("*");
        boolean containsEnemy = mobTypeList.contains(enemyTypeName);
        if(mobListInverted) return (containsAll || containsEnemy);
        return (!containsAll && !containsEnemy);
    }
    
    private boolean checkMobSpawnReasonDisabled(LivingEntity enemy) {
        FileConfiguration configuration = this.expansion.getConfig("mob-tagger.yml");
        List<String> spawnReasonList = configuration.getStringList("spawn-reason-list");
        if(spawnReasonList.contains("*")) return true;
        
        SpawnReason spawnReason = getSpawnReason(enemy);
        String spawnReasonName = spawnReason.name();
        return spawnReasonList.contains(spawnReasonName);
    }
    
    private SpawnReason getSpawnReason(LivingEntity entity) {
        if(entity == null) return SpawnReason.DEFAULT;
        if(!entity.hasMetadata("combatlogx_spawn_reason")) return SpawnReason.DEFAULT;
    
        List<MetadataValue> spawnReasonValues = entity.getMetadata("combatlogx_spawn_reason");
        for(MetadataValue metadataValue : spawnReasonValues) {
            Object object = metadataValue.value();
            if(!(object instanceof SpawnReason)) continue;
            
            return (SpawnReason) object;
        }
        
        return SpawnReason.DEFAULT;
    }
    
    private Entity linkProjectile(Entity original) {
        if(original == null) return null;
        
        FileConfiguration config = this.expansion.getPlugin().getConfig("config.yml");
        if(!config.getBoolean("link-projectiles")) return original;
        
        if(original instanceof Projectile) {
            Projectile projectile = (Projectile) original;
            ProjectileSource shooter = projectile.getShooter();
            if(shooter instanceof Entity) return (Entity) shooter;
        }
        
        return original;
    }
    
    private Entity linkPet(Entity original) {
        if(original == null) return null;
        
        FileConfiguration config = this.expansion.getPlugin().getConfig("config.yml");
        if(!config.getBoolean("link-pets")) return original;
        
        if(original instanceof Tameable) {
            Tameable pet = (Tameable) original;
            AnimalTamer owner = pet.getOwner();
            if(owner instanceof Entity) return (Entity) owner;
        }
        
        return original;
    }

    private void printDebug(String message) {
        ICombatLogX plugin = this.expansion.getPlugin();
        YamlConfiguration configuration = plugin.getConfig("config.yml");
        if(!configuration.getBoolean("debug")) return;

        Logger logger = this.expansion.getLogger();
        logger.info("[Debug] " + message);
    }
}
