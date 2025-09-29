package com.sipilen.truedamagenullifier;

import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrueDamageNullifier extends JavaPlugin implements Listener {

    public final Map<UUID, DamageModifier> playerModifiers = new HashMap<>();
    public final Map<UUID, Long> expiryTimes = new HashMap<>();
    public final Map<UUID, String> disableReasons = new HashMap<>();
    public FileConfiguration config;
    public FileConfiguration messages;

    public enum DamageModifier {
        DISABLED, REDUCED, AMPLIFIED, NORMAL
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("tdn") != null) {
            getCommand("tdn").setExecutor(new TDNCommand(this));
            getCommand("tdn").setTabCompleter(new TDNTabCompleter());
        }
        saveDefaultConfig();
        config = getConfig();
        // Load messages.yml
        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        loadPlayerModifiers();
        startExpiryChecker();
        getLogger().info("TrueDamageNullifier enabled!");
    }

    @Override
    public void onDisable() {
        savePlayerModifiers();
        getLogger().info("TrueDamageNullifier disabled!");
    }

    public void loadPlayerModifiers() {
        ConfigurationSection section = config.getConfigurationSection("player-modifiers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                DamageModifier modifier = DamageModifier.valueOf(config.getString("player-modifiers." + key + ".modifier"));
                playerModifiers.put(uuid, modifier);
                long expiry = config.getLong("player-modifiers." + key + ".expiry", 0);
                String reason = config.getString("player-modifiers." + key + ".reason", "Не указана");
                boolean hasExpiry = expiry > 0 && expiry > System.currentTimeMillis();
                if (modifier != DamageModifier.NORMAL && hasExpiry) {
                    expiryTimes.put(uuid, expiry);
                    if (modifier == DamageModifier.DISABLED) {
                        disableReasons.put(uuid, reason);
                    }
                } else if (modifier != DamageModifier.NORMAL && expiry > 0) {
                    playerModifiers.put(uuid, DamageModifier.NORMAL);
                }
            }
        }
    }

    public void savePlayerModifiers() {
        for (Map.Entry<UUID, DamageModifier> entry : playerModifiers.entrySet()) {
            String path = "player-modifiers." + entry.getKey().toString();
            config.set(path + ".modifier", entry.getValue().toString());
            if (entry.getValue() != DamageModifier.NORMAL) {
                config.set(path + ".expiry", expiryTimes.getOrDefault(entry.getKey(), 0L));
                if (entry.getValue() == DamageModifier.DISABLED) {
                    config.set(path + ".reason", disableReasons.getOrDefault(entry.getKey(), "Не указана"));
                } else {
                    config.set(path + ".reason", null);
                }
            } else {
                config.set(path + ".expiry", null);
                config.set(path + ".reason", null);
            }
        }
        saveConfig();
    }

    public void startExpiryChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (UUID uuid : new HashMap<>(expiryTimes).keySet()) {
                    Long expiry = expiryTimes.get(uuid);
                    if (expiry != null && expiry <= currentTime && expiry > 0) {
                        playerModifiers.put(uuid, DamageModifier.NORMAL);
                        expiryTimes.remove(uuid);
                        disableReasons.remove(uuid);
                        Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage(formatMessage("pvp_enabled", new HashMap<String, String>()));
                        }
                        savePlayerModifiers();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Проверка каждую секунду
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            UUID attackerId = attacker.getUniqueId();
            DamageModifier attackerModifier = playerModifiers.getOrDefault(attackerId, DamageModifier.NORMAL);

            if (attackerModifier == DamageModifier.DISABLED) {
                String reasonMsg = disableReasons.getOrDefault(attackerId, "Не указана");
                long expiry = expiryTimes.getOrDefault(attackerId, 0L);
                String timeLeft = expiry > 0 ? formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                event.setCancelled(true);
                Map<String, String> params = new HashMap<>();
                params.put("reason", reasonMsg);
                params.put("time", timeLeft);
                attacker.sendMessage(formatMessage("pvp_disabled", params));
                return;
            }
            if (attackerModifier == DamageModifier.REDUCED && event.getEntity() instanceof Player) {
                event.setDamage(event.getDamage() / 3.0); // 1/3 урона
            }
        }
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                Player attacker = (Player) shooter;
                UUID attackerId = attacker.getUniqueId();
                DamageModifier attackerModifier = playerModifiers.getOrDefault(attackerId, DamageModifier.NORMAL);

                if (attackerModifier == DamageModifier.DISABLED) {
                    String reasonMsg = disableReasons.getOrDefault(attackerId, "Не указана");
                    long expiry = expiryTimes.getOrDefault(attackerId, 0L);
                    String timeLeft = expiry > 0 ? formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                    event.setCancelled(true);
                    Map<String, String> params = new HashMap<>();
                    params.put("reason", reasonMsg);
                    params.put("time", timeLeft);
                    attacker.sendMessage(formatMessage("pvp_disabled", params));
                    return;
                }
                if (attackerModifier == DamageModifier.REDUCED && event.getEntity() instanceof Player) {
                    event.setDamage(event.getDamage() / 3.0);
                }
            }
        }
        if (event.getEntity() instanceof Player) {
            Player target = (Player) event.getEntity();
            UUID targetId = target.getUniqueId();
            DamageModifier targetModifier = playerModifiers.getOrDefault(targetId, DamageModifier.NORMAL);

            if (targetModifier == DamageModifier.AMPLIFIED && (
                    event.getDamager() instanceof Player ||
                            event.getDamager() instanceof Projectile && ((Projectile)event.getDamager()).getShooter() instanceof Player
            )) {
                event.setDamage(event.getDamage() * 4.0);
            }

        }
    }

    public String formatTime(long millis) {
        if (millis <= 0) return "0 секунд";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds %= 60;
        minutes %= 60;
        StringBuilder result = new StringBuilder();
        if (hours > 0) result.append(hours).append(" ч ");
        if (minutes > 0) result.append(minutes).append(" мин ");
        if (seconds > 0) result.append(seconds).append(" сек");
        return result.toString().trim();
    }

    public long parseTime(String timeStr) {
        try {
            String num = timeStr.replaceAll("[^0-9]", "");
            long value = Long.parseLong(num);
            if (timeStr.toLowerCase().endsWith("s")) return value * 1000;
            if (timeStr.toLowerCase().endsWith("m")) return value * 60 * 1000;
            if (timeStr.toLowerCase().endsWith("h")) return value * 3600 * 1000;
            if ("0".equals(timeStr)) return 0;
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getMessage(String key) {
        return messages.getString(key, "§c[Ошибка]: сообщение не найдено");
    }

    public String formatMessage(String key, Map<String, String> params) {
        String msg = getMessage(key);
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return msg;
    }
}