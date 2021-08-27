package com.SirBlobman.combatlogx.expansion.cheat.prevention.listener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.SirBlobman.combatlogx.api.event.PlayerUntagEvent;
import com.SirBlobman.combatlogx.api.event.PlayerUntagEvent.UntagReason;
import com.SirBlobman.combatlogx.expansion.cheat.prevention.CheatPrevention;

public class ListenerCommandBlocker extends CheatPreventionListener {
    private final Map<UUID, Long> cooldownMap;
    
    public ListenerCommandBlocker(CheatPrevention expansion) {
        super(expansion);
        this.cooldownMap = new ConcurrentHashMap<>();
    }
    
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void beforeCommandLowest(PlayerCommandPreprocessEvent e) {
        checkEvent(e);
    }
    
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void beforeCommandHigh(PlayerCommandPreprocessEvent e) {
        checkEvent(e);
    }
    
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onUntag(PlayerUntagEvent e) {
        UntagReason untagReason = e.getUntagReason();
        if(!untagReason.isExpire()) return;
        
        Player player = e.getPlayer();
        addCooldown(player);
    }
    
    private YamlConfiguration getConfiguration() {
        CheatPrevention expansion = getExpansion();
        ConfigurationManager configurationManager = expansion.getConfigurationManager();
        return configurationManager.get("cheat-prevention.yml");
    }
    
    private long getNewExpireTime() {
        YamlConfiguration configuration = getConfiguration();
        long cooldownSeconds = configuration.getLong("command-blocker.delay-after-combat");
        long cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds);
        
        long systemMillis = System.currentTimeMillis();
        return (systemMillis + cooldownMillis);
    }
    
    private boolean hasCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if(!this.cooldownMap.containsKey(playerId)) {
            return false;
        }
        
        long expireMillis = this.cooldownMap.get(playerId);
        long systemMillis = System.currentTimeMillis();
        if(systemMillis >= expireMillis) {
            this.cooldownMap.remove(playerId);
            return false;
        }
        
        return true;
    }
    
    private void addCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long expireMillis = getNewExpireTime();
        this.cooldownMap.put(playerId, expireMillis);
    }
    
    private String fixCommand(String command) {
        if(!command.startsWith("/")) {
            command = ("/" + command);
        }
        
        return command;
    }
    
    private boolean matchesAny(String string, Iterable<String> values) {
        String stringLower = string.toLowerCase(Locale.US);
        for(String value : values) {
            if(value.equals("*")) return true;
            if(value.equals("/*")) return true;
            
            String valueLower = value.toLowerCase(Locale.US);
            if(stringLower.equals(valueLower)) return true;
            if(stringLower.equals(valueLower + " ")) return true;
        }
        
        return false;
    }
    
    private boolean isBlocked(String command) {
        YamlConfiguration configuration = getConfiguration();
        List<String> blockedCommandList = configuration.getStringList("command-blocker.blocked-commands");
        return matchesAny(command, blockedCommandList);
    }
    
    private boolean isAllowed(String command) {
        YamlConfiguration configuration = getConfiguration();
        List<String> allowedCommandList = configuration.getStringList("command-blocker.allowed-commands");
        return matchesAny(command, allowedCommandList);
    }
    
    private void checkEvent(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if(!isInCombat(player) && !hasCooldown(player)) return;
        
        String originalCommand = e.getMessage();
        String fixedCommand = fixCommand(originalCommand);
        if(isAllowed(fixedCommand) || !isBlocked(fixedCommand)) return;
        
        e.setCancelled(true);
        String message = getMessage("cheat-prevention.command-blocked")
                .replace("{command}", originalCommand);
        sendMessage(player, message);
    }
}
