package com.SirBlobman.combatlogx.listener;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import com.SirBlobman.combatlogx.api.ICombatLogX;
import com.SirBlobman.combatlogx.api.event.PlayerPreTagEvent;
import com.SirBlobman.combatlogx.api.event.PlayerTagEvent;
import com.SirBlobman.combatlogx.api.event.PlayerUntagEvent;
import com.SirBlobman.combatlogx.api.utility.ICombatManager;

public class ListenerCombatChecks implements Listener {
    private final ICombatLogX plugin;
    public ListenerCombatChecks(ICombatLogX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void beforeTag(PlayerPreTagEvent e) {
        Player player = e.getPlayer();
        printDebug("Detected PlayerPreTagEvent for player '" + player.getName() + "'...");

        printDebug("Checking if player is in a disabled world...");
        if(isInDisabledWorld(player)) {
            printDebug("Player is in a disabled world, cancelling event.");
            e.setCancelled(true);
            return;
        }

        printDebug("Checking if player has bypass permission...");
        if(canBypass(player)) {
            printDebug("Player has bypass permission, cancelling event.");
            e.setCancelled(true);
            return;
        }

        LivingEntity enemy = e.getEnemy();
        if(enemy == null) {
            printDebug("Enemy is unknown, ignoring event.");
            return;
        }

        printDebug("Checking if enemy is self and self combat config...");
        if(checkNoSelfCombat(player, enemy)) {
            printDebug("Enemy is self and self-combat is disabled, cancelling event.");
            e.setCancelled(true);
            return;
        }

        printDebug("Completed PlayerPreTagEvent checks without cancellation.");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onTag(PlayerTagEvent e) {
        Player player = e.getPlayer();
        LivingEntity enemy = e.getEnemy();
        runSudoCommands(player, enemy);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        checkEnemyDeathUntag(entity);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        if(!(entity instanceof Creeper)) return;

        Creeper creeper = (Creeper) entity;
        checkEnemyDeathUntag(creeper);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        checkSelfDeathUntag(player);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        checkSelfDeathUntag(player);
    }

    private boolean isInDisabledWorld(Player player) {
        if(player == null) return false;
        World world = player.getWorld();
        String worldName = world.getName();

        FileConfiguration config = this.plugin.getConfig("config.yml");
        List<String> disabledWorldList = config.getStringList("disabled-worlds");

        boolean whitelist = config.getBoolean("disabled-worlds-is-whitelist");
        boolean contains = disabledWorldList.contains(worldName);
        return (whitelist != contains);
    }

    private boolean canBypass(Player player) {
        if(player == null) return false;

        FileConfiguration config = this.plugin.getConfig("config.yml");
        String bypassPermission = config.getString("combat.bypass-permission");
        if(bypassPermission == null || bypassPermission.isEmpty()) return false;
        
        Permission permission = new Permission(bypassPermission, "Bypass permission for CombatLogX.", PermissionDefault.FALSE);
        return player.hasPermission(permission);
    }

    private boolean checkNoSelfCombat(Player player, LivingEntity enemy) {
        if(player == null || enemy == null) return false;

        FileConfiguration config = this.plugin.getConfig("config.yml");
        boolean selfCombat = config.getBoolean("combat.self-combat");
        if(selfCombat) return false;

        UUID playerId = player.getUniqueId();
        UUID enemyId = enemy.getUniqueId();
        return playerId.equals(enemyId);
    }

    private void runSudoCommands(Player player, LivingEntity enemy) {
        if(player == null) return;

        ICombatManager combatManager = this.plugin.getCombatManager();
        if(combatManager.isInCombat(player)) return;

        FileConfiguration config = this.plugin.getConfig("config.yml");
        List<String> sudoCommandList = config.getStringList("combat-sudo-command-list");
        for(String sudoCommand : sudoCommandList) {
            sudoCommand = combatManager.getSudoCommand(player, enemy, sudoCommand);
            if(sudoCommand.startsWith("[PLAYER]")) {
                String command = sudoCommand.substring("[PLAYER]".length());
                player.performCommand(command);
                continue;
            }

            if(sudoCommand.startsWith("[OP]")) {
                String command = sudoCommand.substring("[OP]".length());
                if(player.isOp()) {
                    player.performCommand(command);
                    continue;
                }

                player.setOp(true);
                player.performCommand(command);
                player.setOp(false);

                continue;
            }

            CommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, sudoCommand);
        }
    }

    private void checkEnemyDeathUntag(LivingEntity enemy) {
        if(enemy == null) return;

        FileConfiguration config = this.plugin.getConfig("config.yml");
        if(!config.getBoolean("untag.on-enemy-death")) return;

        ICombatManager combatManager = this.plugin.getCombatManager();
        OfflinePlayer offline = combatManager.getByEnemy(enemy);
        if(offline == null || !offline.isOnline()) return;

        Player player = offline.getPlayer();
        if(player == null || !combatManager.isInCombat(player)) return;

        combatManager.untag(player, PlayerUntagEvent.UntagReason.EXPIRE_ENEMY_DEATH);
    }

    private void checkSelfDeathUntag(Player player) {
        if(player == null) return;

        FileConfiguration config = this.plugin.getConfig("config.yml");
        if(!config.getBoolean("untag.on-self-death")) return;

        ICombatManager combatManager = this.plugin.getCombatManager();
        if(!combatManager.isInCombat(player)) return;

        combatManager.untag(player, PlayerUntagEvent.UntagReason.EXPIRE);
    }

    private void printDebug(String message) {
        this.plugin.printDebug(message);
    }
}
