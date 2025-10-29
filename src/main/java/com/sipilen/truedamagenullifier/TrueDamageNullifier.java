package com.sipilen.truedamagenullifier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrueDamageNullifier extends JavaPlugin implements Listener {

    public final Map<UUID, DamageModifier> playerModifiers = new HashMap<>();
    public final Map<UUID, Long> expiryTimes = new HashMap<>();
    public final Map<UUID, String> disableReasons = new HashMap<>();

    // Модификаторы для ников (lowercase) - основная настройка
    public final Map<String, DamageModifier> nickModifiers = new HashMap<>();
    public final Map<String, Long> nickExpiryTimes = new HashMap<>();
    public final Map<String, String> nickDisableReasons = new HashMap<>();

    // Привязки IP -> original nick (lowercase)
    public final Map<String, String> ipToNick = new HashMap<>();

    // Модификаторы для IP (применяются на основе nick, но хранятся для перманентности)
    public final Map<String, DamageModifier> ipModifiers = new HashMap<>();
    public final Map<String, Long> ipExpiryTimes = new HashMap<>();
    public final Map<String, String> ipDisableReasons = new HashMap<>();

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
            getCommand("tdn").setTabCompleter(new TDNTabCompleter(this));
        }
        saveDefaultConfig();
        config = getConfig();
        // Load messages.yml
        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        loadPlayerModifiers();
        loadNickModifiers(); // Загрузка для ников
        loadIpBindings(); // Загрузка привязок IP
        startExpiryChecker();
        getLogger().info("TrueDamageNullifier enabled!");
    }

    @Override
    public void onDisable() {
        savePlayerModifiers();
        saveNickModifiers(); // Сохранение для ников
        saveIpBindings(); // Сохранение привязок IP
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

    // Методы для ников (настройка модификаторов)
    public void loadNickModifiers() {
        ConfigurationSection section = config.getConfigurationSection("nick-modifiers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String nickLower = key.toLowerCase();
                DamageModifier modifier = DamageModifier.valueOf(config.getString("nick-modifiers." + key + ".modifier", "NORMAL"));
                nickModifiers.put(nickLower, modifier);
                long expiry = config.getLong("nick-modifiers." + key + ".expiry", 0);
                String reason = config.getString("nick-modifiers." + key + ".reason", "Не указана");
                boolean hasExpiry = expiry > 0 && expiry > System.currentTimeMillis();
                if (modifier != DamageModifier.NORMAL && hasExpiry) {
                    nickExpiryTimes.put(nickLower, expiry);
                    if (modifier == DamageModifier.DISABLED) {
                        nickDisableReasons.put(nickLower, reason);
                    }
                } else if (modifier != DamageModifier.NORMAL && expiry > 0) {
                    nickModifiers.put(nickLower, DamageModifier.NORMAL);
                }
            }
        }
    }

    public void saveNickModifiers() {
        for (Map.Entry<String, DamageModifier> entry : nickModifiers.entrySet()) {
            String nick = entry.getKey();
            String path = "nick-modifiers." + nick;
            config.set(path + ".modifier", entry.getValue().toString());
            if (entry.getValue() != DamageModifier.NORMAL) {
                config.set(path + ".expiry", nickExpiryTimes.getOrDefault(nick, 0L));
                if (entry.getValue() == DamageModifier.DISABLED) {
                    config.set(path + ".reason", nickDisableReasons.getOrDefault(nick, "Не указана"));
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

    // Методы для привязок IP (ipToNick и связанные mods для IP)
    public void loadIpBindings() {
        ConfigurationSection section = config.getConfigurationSection("ip-bindings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String ip = key;
                String originalNick = config.getString("ip-bindings." + key + ".nick");
                ipToNick.put(ip, originalNick.toLowerCase());
                // Загружаем mod для IP из nick или напрямую
                if (nickModifiers.containsKey(originalNick.toLowerCase())) {
                    DamageModifier mod = nickModifiers.get(originalNick.toLowerCase());
                    ipModifiers.put(ip, mod);
                    long expiry = nickExpiryTimes.getOrDefault(originalNick.toLowerCase(), 0L);
                    ipExpiryTimes.put(ip, expiry);
                    if (mod == DamageModifier.DISABLED) {
                        ipDisableReasons.put(ip, nickDisableReasons.getOrDefault(originalNick.toLowerCase(), "Не указана"));
                    }
                }
            }
        }
    }

    public void saveIpBindings() {
        for (Map.Entry<String, String> entry : ipToNick.entrySet()) {
            String ip = entry.getKey();
            String nickLower = entry.getValue();
            String path = "ip-bindings." + ip;
            config.set(path + ".nick", nickLower);
            // Сохраняем mod для IP
            DamageModifier mod = ipModifiers.get(ip);
            if (mod != DamageModifier.NORMAL) {
                config.set(path + ".modifier", mod.toString());
                config.set(path + ".expiry", ipExpiryTimes.getOrDefault(ip, 0L));
                if (mod == DamageModifier.DISABLED) {
                    config.set(path + ".reason", ipDisableReasons.getOrDefault(ip, "Не указана"));
                }
            } else {
                config.set(path + ".modifier", null);
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

                // Проверка для игроков (UUID)
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

                // Проверка для IP (и обновление связанных ников если нужно)
                for (String ip : new HashMap<>(ipExpiryTimes).keySet()) {
                    Long expiry = ipExpiryTimes.get(ip);
                    if (expiry != null && expiry <= currentTime && expiry > 0) {
                        ipModifiers.put(ip, DamageModifier.NORMAL);
                        ipExpiryTimes.remove(ip);
                        ipDisableReasons.remove(ip);
                        String originalNick = ipToNick.get(ip);
                        if (originalNick != null) {
                            nickModifiers.put(originalNick, DamageModifier.NORMAL);
                            nickExpiryTimes.remove(originalNick);
                            nickDisableReasons.remove(originalNick);
                        }
                        // Обновляем для онлайн-игроков с этим IP
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (getPlayerIp(player).equals(ip)) {
                                UUID uuid = player.getUniqueId();
                                playerModifiers.put(uuid, DamageModifier.NORMAL);
                                expiryTimes.remove(uuid);
                                disableReasons.remove(uuid);
                                player.sendMessage(formatMessage("pvp_enabled", new HashMap<String, String>()));
                            }
                        }
                        saveIpBindings();
                        saveNickModifiers();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String nameLower = name.toLowerCase();
        String ip = getPlayerIp(player);

        // Логика привязки аналогично ProxySchedule
        DamageModifier modToApply = DamageModifier.NORMAL;
        Long expiryToApply = 0L;
        String reasonToApply = "Не указана";

        if (nickModifiers.containsKey(nameLower)) {
            // Если ник имеет mod, но IP не привязан - привязываем
            if (!ipToNick.containsKey(ip)) {
                ipToNick.put(ip, nameLower);
                modToApply = nickModifiers.get(nameLower);
                expiryToApply = nickExpiryTimes.getOrDefault(nameLower, 0L);
                reasonToApply = nickDisableReasons.getOrDefault(nameLower, "Не указана");
                ipModifiers.put(ip, modToApply);
                ipExpiryTimes.put(ip, expiryToApply);
                if (modToApply == DamageModifier.DISABLED) {
                    ipDisableReasons.put(ip, reasonToApply);
                }
                saveIpBindings();
            } else {
                // IP уже привязан - применяем mod от original nick
                String originalNick = ipToNick.get(ip);
                modToApply = nickModifiers.getOrDefault(originalNick, DamageModifier.NORMAL);
                if (modToApply != DamageModifier.NORMAL) {
                    expiryToApply = nickExpiryTimes.getOrDefault(originalNick, 0L);
                    reasonToApply = nickDisableReasons.getOrDefault(originalNick, "Не указана");
                }
            }
        } else if (ipToNick.containsKey(ip)) {
            // Нет mod для current nick, но IP привязан - применяем от original
            String originalNick = ipToNick.get(ip);
            modToApply = nickModifiers.getOrDefault(originalNick, DamageModifier.NORMAL);
            if (modToApply != DamageModifier.NORMAL) {
                expiryToApply = nickExpiryTimes.getOrDefault(originalNick, 0L);
                if (modToApply == DamageModifier.DISABLED) {
                    reasonToApply = nickDisableReasons.getOrDefault(originalNick, "Не указана");
                }
            }
        }

        // Применяем mod к игроку, если не NORMAL и время не истекло
        boolean hasExpiry = expiryToApply > 0 && expiryToApply > System.currentTimeMillis();
        if (modToApply != DamageModifier.NORMAL && hasExpiry) {
            if (playerModifiers.getOrDefault(uuid, DamageModifier.NORMAL) == DamageModifier.NORMAL) {
                playerModifiers.put(uuid, modToApply);
                expiryTimes.put(uuid, expiryToApply);
                if (modToApply == DamageModifier.DISABLED) {
                    disableReasons.put(uuid, reasonToApply);
                }
            }
            // Сообщение
            if (modToApply == DamageModifier.DISABLED) {
                Map<String, String> params = new HashMap<>();
                params.put("reason", reasonToApply);
                String timeLeft = expiryToApply > 0 ? formatTime(expiryToApply - System.currentTimeMillis()) : "постоянно";
                params.put("time", timeLeft);
                player.sendMessage(formatMessage("pvp_disabled", params));
            }
        }
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
                event.setDamage(event.getDamage() / 3.0);
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

    public String getPlayerIp(Player player) {
        InetSocketAddress address = player.getAddress();
        return address.getAddress().getHostAddress();
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