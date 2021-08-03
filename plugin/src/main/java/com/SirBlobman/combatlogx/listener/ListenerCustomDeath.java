package com.SirBlobman.combatlogx.listener;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.github.sirblobman.api.utility.MessageUtility;
import com.github.sirblobman.api.utility.Validate;
import com.SirBlobman.combatlogx.api.ICombatLogX;
import com.SirBlobman.combatlogx.api.listener.ICustomDeathListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ListenerCustomDeath implements ICustomDeathListener {
    private final ICombatLogX plugin;
    private final Set<UUID> customDeathSet;
    
    public ListenerCustomDeath(ICombatLogX plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null!");
        this.customDeathSet = new HashSet<>();
    }

    @Override
    public void add(Player player) {
        if(player == null) return;

        UUID uuid = player.getUniqueId();
        this.customDeathSet.add(uuid);
    }

    @Override
    public void remove(Player player) {
        if(player == null) return;

        UUID uuid = player.getUniqueId();
        this.customDeathSet.remove(uuid);
    }

    @Override
    public boolean contains(Player player) {
        if(player == null) return false;

        UUID uuid = player.getUniqueId();
        return this.customDeathSet.contains(uuid);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        YamlConfiguration playerData = this.plugin.getDataFile(player);
        if(!playerData.getBoolean("kill-on-join")) return;

        add(player);
        player.setHealth(0.0D);
        playerData.set("kill-on-join", false);
        this.plugin.saveDataFile(player);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        boolean contains = contains(player);
        remove(player);
        
        String customDeathMessage = getRandomCustomDeathMessage(player);
        if(customDeathMessage != null) {
            e.setDeathMessage(customDeathMessage);
        }
    }
    
    @Nullable
    private String getRandomCustomDeathMessage(@NotNull Player player) {
        Validate.notNull(player, "player must not be null!");
        
        YamlConfiguration configuration = this.plugin.getConfig("config.yml");
        List<String> customDeathMessageList = configuration.getStringList("punishments.custom-death-messages");
        if(customDeathMessageList.isEmpty()) return null;
        
        int customDeathMessageListSize = customDeathMessageList.size();
        int randomIndex = ThreadLocalRandom.current().nextInt(customDeathMessageListSize);
        String message = customDeathMessageList.get(randomIndex);
        
        String playerName = player.getName();
        String messageReplaced = message.replace("{name}", playerName);
        return MessageUtility.color(messageReplaced);
    }
}
