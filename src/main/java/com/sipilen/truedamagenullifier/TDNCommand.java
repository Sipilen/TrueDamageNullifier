package com.sipilen.truedamagenullifier;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TDNCommand implements CommandExecutor {
    private final TrueDamageNullifier plugin;

    public TDNCommand(TrueDamageNullifier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessage("unknown_command"));
            return true;
        }
        String action = args[0].toLowerCase();

        switch (action) {
            case "reload": {
                if (!sender.hasPermission("tdn.admin")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.config = plugin.getConfig();
                plugin.playerModifiers.clear();
                plugin.expiryTimes.clear();
                plugin.disableReasons.clear();
                plugin.nickModifiers.clear();
                plugin.nickExpiryTimes.clear();
                plugin.nickDisableReasons.clear();
                plugin.ipToNick.clear();
                plugin.ipModifiers.clear();
                plugin.ipExpiryTimes.clear();
                plugin.ipDisableReasons.clear();
                plugin.messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "messages.yml"));
                plugin.loadPlayerModifiers();
                plugin.loadNickModifiers();
                plugin.loadIpBindings();
                sender.sendMessage(plugin.getMessage("config_reloaded"));
                return true;
            }

            case "list": {
                if (!sender.hasPermission("tdn.admin") && !sender.hasPermission("tdn.list")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                sender.sendMessage("§eСписок игроков с активными модификаторами:");
                boolean hasPlayerMods = false;
                for (UUID uuid : plugin.playerModifiers.keySet()) {
                    TrueDamageNullifier.DamageModifier modifier = plugin.playerModifiers.get(uuid);
                    if (modifier != TrueDamageNullifier.DamageModifier.NORMAL) {
                        hasPlayerMods = true;
                        Player player = plugin.getServer().getPlayer(uuid);
                        String name = player != null ? player.getName() : uuid.toString();
                        long expiry = plugin.expiryTimes.getOrDefault(uuid, 0L);
                        String timeLeft = expiry > 0 ? plugin.formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                        String reason = modifier == TrueDamageNullifier.DamageModifier.DISABLED
                                ? plugin.disableReasons.getOrDefault(uuid, "Не указана")
                                : "-";
                        sender.sendMessage("§a" + name + "§7: §b" + modifier +
                                "§7, §eОсталось: " + timeLeft +
                                (modifier == TrueDamageNullifier.DamageModifier.DISABLED ? ("§7, §cПричина: " + reason) : ""));
                    }
                }
                if (!hasPlayerMods) {
                    sender.sendMessage("§7Нет активных модификаторов для игроков.");
                }
                return true;
            }

            case "nicklist": {
                if (!sender.hasPermission("tdn.admin") && !sender.hasPermission("tdn.nicklist")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                if (plugin.nickModifiers.isEmpty()) {
                    sender.sendMessage("§7Нет активных модификаторов для ников.");
                    return true;
                }
                sender.sendMessage("§eСписок ников с активными модификаторами (привязки IP):");
                boolean hasNickMods = false;
                for (String nickLower : plugin.nickModifiers.keySet()) {
                    TrueDamageNullifier.DamageModifier modifier = plugin.nickModifiers.get(nickLower);
                    if (modifier != TrueDamageNullifier.DamageModifier.NORMAL) {
                        hasNickMods = true;
                        // Находим связанные IP
                        StringBuilder ips = new StringBuilder();
                        for (Map.Entry<String, String> entry : plugin.ipToNick.entrySet()) {
                            if (entry.getValue().equals(nickLower)) {
                                if (ips.length() > 0) ips.append(", ");
                                ips.append(entry.getKey());
                            }
                        }
                        String ipList = ips.length() > 0 ? ips.toString() : "нет привязок";
                        long expiry = plugin.nickExpiryTimes.getOrDefault(nickLower, 0L);
                        String timeLeft = expiry > 0 ? plugin.formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                        String reason = modifier == TrueDamageNullifier.DamageModifier.DISABLED
                                ? plugin.nickDisableReasons.getOrDefault(nickLower, "Не указана")
                                : "-";
                        sender.sendMessage("§a" + nickLower + "§7: §b" + modifier +
                                "§7, §eОсталось: " + timeLeft +
                                "§7, §cIP: " + ipList +
                                (modifier == TrueDamageNullifier.DamageModifier.DISABLED ? ("§7, §cПричина: " + reason) : ""));
                    }
                }
                if (!hasNickMods) {
                    sender.sendMessage("§7Нет активных модификаторов для ников.");
                }
                return true;
            }

            case "clear": {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("usage_clear"));
                    return true;
                }
                if (!sender.hasPermission("tdn.clear")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                Player targetClear = plugin.getServer().getPlayer(args[1]);
                if (targetClear == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                UUID targetClearId = targetClear.getUniqueId();
                plugin.playerModifiers.put(targetClearId, TrueDamageNullifier.DamageModifier.NORMAL);
                plugin.expiryTimes.remove(targetClearId);
                plugin.disableReasons.remove(targetClearId);
                plugin.savePlayerModifiers();
                Map<String,String> paramsClear = new HashMap<>();
                paramsClear.put("player", targetClear.getName());
                sender.sendMessage(plugin.formatMessage("modifiers_cleared", paramsClear));
                targetClear.sendMessage(plugin.getMessage("modifiers_cleared_self"));
                return true;
            }

            case "nickclear": {
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage("§cЭта команда доступна только из консоли!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn nickclear <nick>");
                    return true;
                }
                if (!sender.hasPermission("tdn.nickclear")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                String nickClearLower = args[1].toLowerCase();
                plugin.nickModifiers.put(nickClearLower, TrueDamageNullifier.DamageModifier.NORMAL);
                plugin.nickExpiryTimes.remove(nickClearLower);
                plugin.nickDisableReasons.remove(nickClearLower);
                // Сбрасываем связанные IP
                for (String ip : new HashMap<>(plugin.ipToNick).keySet()) {
                    if (plugin.ipToNick.get(ip).equals(nickClearLower)) {
                        plugin.ipModifiers.put(ip, TrueDamageNullifier.DamageModifier.NORMAL);
                        plugin.ipExpiryTimes.remove(ip);
                        plugin.ipDisableReasons.remove(ip);
                        // Обновляем онлайн-игроков
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (plugin.getPlayerIp(p).equals(ip)) {
                                UUID uuid = p.getUniqueId();
                                plugin.playerModifiers.put(uuid, TrueDamageNullifier.DamageModifier.NORMAL);
                                plugin.expiryTimes.remove(uuid);
                                plugin.disableReasons.remove(uuid);
                                p.sendMessage(plugin.getMessage("pvp_enabled"));
                            }
                        }
                    }
                }
                plugin.saveNickModifiers();
                plugin.saveIpBindings();
                sender.sendMessage("§aМодификаторы для ника §e" + nickClearLower + " §aи связанных IP очищены.");
                return true;
            }

            case "disable": {
                // Обычная disable для онлайн-игрока (не IP)
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("specify_player"));
                    return true;
                }
                if (!sender.hasPermission("tdn.disable")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessage("usage_disable"));
                    return true;
                }
                Player targetDisable = plugin.getServer().getPlayer(args[1]);
                if (targetDisable == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                UUID targetDisableId = targetDisable.getUniqueId();
                long duration = plugin.parseTime(args[2]);
                if (duration <= 0 && !"0".equals(args[2])) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                String disableReason = String.join(" ", args).substring(args[0].length() + args[1].length() + args[2].length() + 3).trim();
                plugin.playerModifiers.put(targetDisableId, TrueDamageNullifier.DamageModifier.DISABLED);
                if (duration > 0) {
                    plugin.expiryTimes.put(targetDisableId, System.currentTimeMillis() + duration);
                } else {
                    plugin.expiryTimes.remove(targetDisableId);
                }
                plugin.disableReasons.put(targetDisableId, disableReason);
                Map<String,String> paramsDisable = new HashMap<>();
                paramsDisable.put("player", targetDisable.getName());
                paramsDisable.put("reason", disableReason);
                paramsDisable.put("time", (duration > 0 ? plugin.formatTime(duration) : "постоянно"));
                sender.sendMessage(plugin.formatMessage("damage_disabled", paramsDisable));
                Map<String, String> paramsDisableSelf = new HashMap<>();
                paramsDisableSelf.put("reason", disableReason);
                paramsDisableSelf.put("time", (duration > 0 ? plugin.formatTime(duration) : "постоянно"));
                targetDisable.sendMessage(plugin.formatMessage("pvp_disabled", paramsDisableSelf));
                plugin.getLogger().info("Disabled damage for " + targetDisable.getName() + " for " + (duration > 0 ? plugin.formatTime(duration) : "permanently") + ". Reason: " + disableReason);
                plugin.savePlayerModifiers();
                return true;
            }

            case "ipdisable": {
                // Теперь для ника: /tdn ipdisable <nick> [time] [reason...]
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage("§cЭта команда доступна только из консоли!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipdisable <nick> [time] [reason...]");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipdisable")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                String nickDisableLower = args[1].toLowerCase();
                String timeStr = (args.length > 2) ? args[2] : "0";
                String reason = (args.length > 3) ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "Не указана";
                long duration = plugin.parseTime(timeStr);
                if (duration <= 0 && !"0".equals(timeStr)) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                long expiry = (duration > 0) ? System.currentTimeMillis() + duration : 0;
                plugin.nickModifiers.put(nickDisableLower, TrueDamageNullifier.DamageModifier.DISABLED);
                plugin.nickExpiryTimes.put(nickDisableLower, expiry);
                plugin.nickDisableReasons.put(nickDisableLower, reason);
                // Применяем сразу к связанным IP и онлайн-игрокам
                for (String ip : new HashMap<>(plugin.ipToNick).keySet()) {
                    if (plugin.ipToNick.get(ip).equals(nickDisableLower)) {
                        plugin.ipModifiers.put(ip, TrueDamageNullifier.DamageModifier.DISABLED);
                        plugin.ipExpiryTimes.put(ip, expiry);
                        plugin.ipDisableReasons.put(ip, reason);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (plugin.getPlayerIp(p).equals(ip)) {
                                UUID uuid = p.getUniqueId();
                                plugin.playerModifiers.put(uuid, TrueDamageNullifier.DamageModifier.DISABLED);
                                plugin.expiryTimes.put(uuid, expiry);
                                plugin.disableReasons.put(uuid, reason);
                                Map<String, String> paramsSelf = new HashMap<>();
                                paramsSelf.put("reason", reason);
                                String timeLeft = (expiry > 0) ? plugin.formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                                paramsSelf.put("time", timeLeft);
                                p.sendMessage(plugin.formatMessage("pvp_disabled", paramsSelf));
                            }
                        }
                    }
                }
                plugin.saveNickModifiers();
                plugin.saveIpBindings();
                Map<String, String> params = new HashMap<>();
                params.put("nick", nickDisableLower);
                params.put("time", (duration > 0 ? plugin.formatTime(duration) : "постоянно"));
                params.put("reason", reason);
                sender.sendMessage(plugin.formatMessage("nick_damage_disabled", params));
                plugin.getLogger().info("IP-disabled damage for nick " + nickDisableLower + " for " + (duration > 0 ? plugin.formatTime(duration) : "permanently") + ". Reason: " + reason);
                return true;
            }

            case "reduce": {
                // Обычная reduce для онлайн-игрока
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("specify_player"));
                    return true;
                }
                if (!sender.hasPermission("tdn.reduce")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessage("usage_reduce"));
                    return true;
                }
                Player targetReduce = plugin.getServer().getPlayer(args[1]);
                if (targetReduce == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                UUID targetReduceId = targetReduce.getUniqueId();
                long reduceDuration = plugin.parseTime(args[2]);
                if (reduceDuration <= 0 && !"0".equals(args[2])) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                plugin.playerModifiers.put(targetReduceId, TrueDamageNullifier.DamageModifier.REDUCED);
                if (reduceDuration > 0) {
                    plugin.expiryTimes.put(targetReduceId, System.currentTimeMillis() + reduceDuration);
                } else {
                    plugin.expiryTimes.remove(targetReduceId);
                }
                plugin.disableReasons.remove(targetReduceId);
                Map<String,String> paramsReduce = new HashMap<>();
                paramsReduce.put("player", targetReduce.getName());
                paramsReduce.put("time", (reduceDuration > 0 ? plugin.formatTime(reduceDuration) : "постоянно"));
                sender.sendMessage(plugin.formatMessage("damage_reduced", paramsReduce));
                plugin.getLogger().info("Reduced outgoing damage for " + targetReduce.getName());
                plugin.savePlayerModifiers();
                return true;
            }

            case "ipreduce": {
                // Теперь для ника
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage("§cЭта команда доступна только из консоли!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipreduce <nick> [time]");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipreduce")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                String nickReduceLower = args[1].toLowerCase();
                String timeStrReduce = (args.length > 2) ? args[2] : "0";
                long reduceDuration = plugin.parseTime(timeStrReduce);
                if (reduceDuration <= 0 && !"0".equals(timeStrReduce)) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                long expiryReduce = (reduceDuration > 0) ? System.currentTimeMillis() + reduceDuration : 0;
                plugin.nickModifiers.put(nickReduceLower, TrueDamageNullifier.DamageModifier.REDUCED);
                plugin.nickExpiryTimes.put(nickReduceLower, expiryReduce);
                plugin.nickDisableReasons.remove(nickReduceLower);
                // Применяем к связанным IP и игрокам
                for (String ip : new HashMap<>(plugin.ipToNick).keySet()) {
                    if (plugin.ipToNick.get(ip).equals(nickReduceLower)) {
                        plugin.ipModifiers.put(ip, TrueDamageNullifier.DamageModifier.REDUCED);
                        plugin.ipExpiryTimes.put(ip, expiryReduce);
                        plugin.ipDisableReasons.remove(ip);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (plugin.getPlayerIp(p).equals(ip)) {
                                UUID uuid = p.getUniqueId();
                                plugin.playerModifiers.put(uuid, TrueDamageNullifier.DamageModifier.REDUCED);
                                plugin.expiryTimes.put(uuid, expiryReduce);
                                plugin.disableReasons.remove(uuid);
                            }
                        }
                    }
                }
                plugin.saveNickModifiers();
                plugin.saveIpBindings();
                Map<String, String> paramsReduceIp = new HashMap<>();
                paramsReduceIp.put("nick", nickReduceLower);
                paramsReduceIp.put("time", (reduceDuration > 0 ? plugin.formatTime(reduceDuration) : "постоянно"));
                sender.sendMessage(plugin.formatMessage("nick_damage_reduced", paramsReduceIp));
                plugin.getLogger().info("IP-reduced outgoing damage for nick " + nickReduceLower);
                return true;
            }

            case "amplify": {
                // Обычная amplify для онлайн-игрока
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("specify_player"));
                    return true;
                }
                if (!sender.hasPermission("tdn.amplify")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessage("usage_amplify"));
                    return true;
                }
                Player targetAmplify = plugin.getServer().getPlayer(args[1]);
                if (targetAmplify == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                UUID targetAmplifyId = targetAmplify.getUniqueId();
                long amplifyDuration = plugin.parseTime(args[2]);
                if (amplifyDuration <= 0 && !"0".equals(args[2])) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                plugin.playerModifiers.put(targetAmplifyId, TrueDamageNullifier.DamageModifier.AMPLIFIED);
                if (amplifyDuration > 0) {
                    plugin.expiryTimes.put(targetAmplifyId, System.currentTimeMillis() + amplifyDuration);
                } else {
                    plugin.expiryTimes.remove(targetAmplifyId);
                }
                plugin.disableReasons.remove(targetAmplifyId);
                Map<String,String> paramsAmplify = new HashMap<>();
                paramsAmplify.put("player", targetAmplify.getName());
                paramsAmplify.put("time", (amplifyDuration > 0 ? plugin.formatTime(amplifyDuration) : "постоянно"));
                sender.sendMessage(plugin.formatMessage("damage_amplified", paramsAmplify));
                plugin.getLogger().info("Amplified incoming damage for " + targetAmplify.getName());
                plugin.savePlayerModifiers();
                return true;
            }

            case "ipamplify": {
                // Теперь для ника
                if (!(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage("§cЭта команда доступна только из консоли!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipamplify <nick> [time]");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipamplify")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                String nickAmplifyLower = args[1].toLowerCase();
                String timeStrAmplify = (args.length > 2) ? args[2] : "0";
                long amplifyDuration = plugin.parseTime(timeStrAmplify);
                if (amplifyDuration <= 0 && !"0".equals(timeStrAmplify)) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                long expiryAmplify = (amplifyDuration > 0) ? System.currentTimeMillis() + amplifyDuration : 0;
                plugin.nickModifiers.put(nickAmplifyLower, TrueDamageNullifier.DamageModifier.AMPLIFIED);
                plugin.nickExpiryTimes.put(nickAmplifyLower, expiryAmplify);
                plugin.nickDisableReasons.remove(nickAmplifyLower);
                // Применяем к связанным IP и игрокам
                for (String ip : new HashMap<>(plugin.ipToNick).keySet()) {
                    if (plugin.ipToNick.get(ip).equals(nickAmplifyLower)) {
                        plugin.ipModifiers.put(ip, TrueDamageNullifier.DamageModifier.AMPLIFIED);
                        plugin.ipExpiryTimes.put(ip, expiryAmplify);
                        plugin.ipDisableReasons.remove(ip);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (plugin.getPlayerIp(p).equals(ip)) {
                                UUID uuid = p.getUniqueId();
                                plugin.playerModifiers.put(uuid, TrueDamageNullifier.DamageModifier.AMPLIFIED);
                                plugin.expiryTimes.put(uuid, expiryAmplify);
                                plugin.disableReasons.remove(uuid);
                            }
                        }
                    }
                }
                plugin.saveNickModifiers();
                plugin.saveIpBindings();
                Map<String, String> paramsAmplifyIp = new HashMap<>();
                paramsAmplifyIp.put("nick", nickAmplifyLower);
                paramsAmplifyIp.put("time", (amplifyDuration > 0 ? plugin.formatTime(amplifyDuration) : "постоянно"));
                sender.sendMessage(plugin.formatMessage("nick_damage_amplified", paramsAmplifyIp));
                plugin.getLogger().info("IP-amplified incoming damage for nick " + nickAmplifyLower);
                return true;
            }

            case "status": {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessage("specify_player"));
                    return true;
                }
                if (!sender.hasPermission("tdn.status")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                Player targetStatus = plugin.getServer().getPlayer(args[1]);
                if (targetStatus == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                UUID targetStatusId = targetStatus.getUniqueId();
                TrueDamageNullifier.DamageModifier modifier = plugin.playerModifiers.getOrDefault(targetStatusId, TrueDamageNullifier.DamageModifier.NORMAL);
                String statusMessage = "§aСтатус урона для " + targetStatus.getName() + ": " + modifier.toString();
                if (modifier != TrueDamageNullifier.DamageModifier.NORMAL) {
                    long expiry = plugin.expiryTimes.getOrDefault(targetStatusId, 0L);
                    String timeLeft = expiry > 0 ? plugin.formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                    statusMessage += ". Время: " + timeLeft;
                    if (modifier == TrueDamageNullifier.DamageModifier.DISABLED) {
                        String storedReason = plugin.disableReasons.getOrDefault(targetStatusId, "Не указана");
                        statusMessage += ". Причина: " + storedReason;
                    }
                }
                sender.sendMessage(statusMessage);
                return true;
            }

            default:
                sender.sendMessage(plugin.getMessage("unknown_command"));
                return true;
        }
    }
}