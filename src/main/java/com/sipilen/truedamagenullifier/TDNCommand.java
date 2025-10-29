package com.sipilen.truedamagenullifier;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                plugin.uuidToNick.clear();
                plugin.ipToAlts.clear();
                plugin.messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "messages.yml"));
                plugin.loadPlayerModifiers();
                plugin.loadNickModifiers();
                plugin.loadIpBindings();
                plugin.loadUuidBindings();
                plugin.loadIpAlts();
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
                        // ALTS: Показать альты
                        StringBuilder altList = new StringBuilder();
                        for (String altIp : plugin.ipToAlts.keySet()) {
                            if (plugin.ipToNick.get(altIp) != null && plugin.ipToNick.get(altIp).equals(nickLower)) {
                                List<String> alts = plugin.ipToAlts.get(altIp);
                                for (String alt : alts) {
                                    if (!alt.equals(nickLower)) {
                                        if (altList.length() > 0) altList.append(", ");
                                        altList.append(alt);
                                    }
                                }
                            }
                        }
                        String altsStr = altList.length() > 0 ? "§7, §dАльты: " + altList.toString() : "";
                        sender.sendMessage("§a" + nickLower + "§7: §b" + modifier +
                                "§7, §eОсталось: " + timeLeft +
                                "§7, §cIP: " + ipList + altsStr +
                                (modifier == TrueDamageNullifier.DamageModifier.DISABLED ? ("§7, §cПричина: " + reason) : ""));
                    }
                }
                if (!hasNickMods) {
                    sender.sendMessage("§7Нет активных модификаторов для ников.");
                }
                return true;
            }

            case "alts": {
                // ALTS: Показать альты по нику
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn alts <nick>");
                    return true;
                }
                if (!sender.hasPermission("tdn.alts")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                String searchNick = args[1].toLowerCase();
                String ip = null;
                // Ищем IP по bound (UUID или IP)
                UUID searchUuid = null;
                for (UUID u : plugin.uuidToNick.keySet()) {
                    if (plugin.uuidToNick.get(u).equals(searchNick)) {
                        searchUuid = u;
                        break;
                    }
                }
                if (searchUuid != null) {
                    Player p = plugin.getServer().getPlayer(searchUuid);
                    if (p != null) ip = plugin.getPlayerIp(p);
                } else {
                    for (Map.Entry<String, String> e : plugin.ipToNick.entrySet()) {
                        if (e.getValue().equals(searchNick)) {
                            ip = e.getKey();
                            break;
                        }
                    }
                }
                if (ip == null) {
                    sender.sendMessage("§cНик §e" + searchNick + " §cне bound к IP. Нет альтов.");
                    return true;
                }
                List<String> alts = plugin.ipToAlts.getOrDefault(ip, new ArrayList<>());
                if (alts.isEmpty()) {
                    sender.sendMessage("§cДля IP §a" + ip + " §c(ник §e" + searchNick + "§c) альтов нет.");
                    return true;
                }
                sender.sendMessage("§eАльт-аккаунты для §a" + ip + " §e(основной: §b" + searchNick + "§e):");
                for (String alt : alts) {
                    sender.sendMessage("§a• §e" + alt);
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
                // Применяем NORMAL к онлайн-игрокам с bound к этому nick
                for (String ip : new HashMap<>(plugin.ipToNick).keySet()) {
                    if (plugin.ipToNick.get(ip).equals(nickClearLower)) {
                        plugin.applyNickModifierToIpPlayers(ip, TrueDamageNullifier.DamageModifier.NORMAL, 0L, "");
                    }
                }
                plugin.saveNickModifiers();
                sender.sendMessage("§aМодификаторы для ника §e" + nickClearLower + " §aи связанных IP очищены.");
                return true;
            }

            case "ipclear": {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipclear <nick>");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipclear")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                String ip = plugin.getPlayerIp(target);
                String nickLower = target.getName().toLowerCase();
                String originalNick = plugin.ipToNick.get(ip);
                if (originalNick == null) {
                    plugin.ipToNick.put(ip, nickLower);
                    plugin.saveIpBindings();
                    originalNick = nickLower;
                }
                plugin.nickModifiers.put(originalNick, TrueDamageNullifier.DamageModifier.NORMAL);
                plugin.nickExpiryTimes.remove(originalNick);
                plugin.nickDisableReasons.remove(originalNick);
                plugin.saveNickModifiers();
                plugin.applyNickModifierToIpPlayers(ip, TrueDamageNullifier.DamageModifier.NORMAL, 0L, "");
                sender.sendMessage("§aМодификаторы для ника §e" + originalNick + " §a(IP: " + ip + ") очищены.");
                return true;
            }

            case "disable": {
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
                // FIXED: Правильный парсинг reason (от args[3] до конца)
                String disableReason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "Не указана";
                long expiry = (duration > 0) ? System.currentTimeMillis() + duration : 0L;
                TrueDamageNullifier.DamageModifier mod = TrueDamageNullifier.DamageModifier.DISABLED;
                plugin.playerModifiers.put(targetDisableId, mod);
                if (duration > 0) {
                    plugin.expiryTimes.put(targetDisableId, expiry);
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
                // UUID-BIND: Bind UUID к current nick если mod != NORMAL
                String nickLower = targetDisable.getName().toLowerCase();
                if (!plugin.uuidToNick.containsKey(targetDisableId)) {
                    plugin.uuidToNick.put(targetDisableId, nickLower);
                    plugin.saveUuidBindings();
                    plugin.getLogger().info("UUID-bound " + targetDisable.getName() + " to nick " + nickLower);
                }
                plugin.applyNickModifierToUuidPlayer(targetDisableId, mod, expiry, disableReason);
                return true;
            }

            case "ipdisable": {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipdisable <nick> [time] [reason...]");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipdisable")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                String timeStr = (args.length > 2) ? args[2] : "0";
                String reason = (args.length > 3) ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "Не указана";
                long duration = plugin.parseTime(timeStr);
                if (duration <= 0 && !"0".equals(timeStr)) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                long expiry = (duration > 0) ? System.currentTimeMillis() + duration : 0L;

                String ip = plugin.getPlayerIp(target);
                String nickLower = target.getName().toLowerCase();
                String originalNick = plugin.ipToNick.get(ip);
                if (originalNick == null) {
                    // Авто-bind к current nick
                    plugin.ipToNick.put(ip, nickLower);
                    plugin.saveIpBindings();
                    sender.sendMessage("§aIP " + ip + " bound к нику " + nickLower);
                    originalNick = nickLower;
                }

                // Устанавливаем mod для original nick
                plugin.nickModifiers.put(originalNick, TrueDamageNullifier.DamageModifier.DISABLED);
                plugin.nickExpiryTimes.put(originalNick, expiry);
                plugin.nickDisableReasons.put(originalNick, reason);
                plugin.saveNickModifiers();

                // Немедленно применяем к онлайн с IP (включая target)
                plugin.applyNickModifierToIpPlayers(ip, TrueDamageNullifier.DamageModifier.DISABLED, expiry, reason);

                // UUID-BIND: Bind UUID target'а к originalNick
                UUID targetUuid = target.getUniqueId();
                if (!plugin.uuidToNick.containsKey(targetUuid)) {
                    plugin.uuidToNick.put(targetUuid, originalNick);
                    plugin.saveUuidBindings();
                    plugin.getLogger().info("UUID-bound " + target.getName() + " to nick " + originalNick);
                }

                Map<String, String> params = new HashMap<>();
                params.put("nick", originalNick);
                params.put("ip", ip);
                params.put("time", (duration > 0 ? plugin.formatTime(duration) : "постоянно"));
                params.put("reason", reason);
                sender.sendMessage("§aPvP отключено для ника §e" + originalNick + " §a(IP: " + ip + ") на " + params.get("time") + ". Причина: " + reason);
                plugin.getLogger().info("IP-disabled damage for nick " + originalNick + " (IP " + ip + ") for " + (duration > 0 ? plugin.formatTime(duration) : "permanently") + ". Reason: " + reason);
                return true;
            }

            case "reduce": {
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
                long expiryReduce = (reduceDuration > 0) ? System.currentTimeMillis() + reduceDuration : 0L;
                TrueDamageNullifier.DamageModifier mod = TrueDamageNullifier.DamageModifier.REDUCED;
                plugin.playerModifiers.put(targetReduceId, mod);
                if (reduceDuration > 0) {
                    plugin.expiryTimes.put(targetReduceId, expiryReduce);
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
                // UUID-BIND: Bind если mod != NORMAL
                String nickLower = targetReduce.getName().toLowerCase();
                if (!plugin.uuidToNick.containsKey(targetReduceId)) {
                    plugin.uuidToNick.put(targetReduceId, nickLower);
                    plugin.saveUuidBindings();
                    plugin.getLogger().info("UUID-bound " + targetReduce.getName() + " to nick " + nickLower);
                }
                plugin.applyNickModifierToUuidPlayer(targetReduceId, mod, expiryReduce, "");
                return true;
            }

            case "ipreduce": {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipreduce <nick> [time]");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipreduce")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                String timeStrReduce = (args.length > 2) ? args[2] : "0";
                long reduceDuration = plugin.parseTime(timeStrReduce);
                if (reduceDuration <= 0 && !"0".equals(timeStrReduce)) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                long expiryReduce = (reduceDuration > 0) ? System.currentTimeMillis() + reduceDuration : 0L;

                String ip = plugin.getPlayerIp(target);
                String nickLower = target.getName().toLowerCase();
                String originalNick = plugin.ipToNick.get(ip);
                if (originalNick == null) {
                    plugin.ipToNick.put(ip, nickLower);
                    plugin.saveIpBindings();
                    sender.sendMessage("§aIP " + ip + " bound к нику " + nickLower);
                    originalNick = nickLower;
                }

                plugin.nickModifiers.put(originalNick, TrueDamageNullifier.DamageModifier.REDUCED);
                plugin.nickExpiryTimes.put(originalNick, expiryReduce);
                plugin.nickDisableReasons.remove(originalNick);
                plugin.saveNickModifiers();

                plugin.applyNickModifierToIpPlayers(ip, TrueDamageNullifier.DamageModifier.REDUCED, expiryReduce, "");

                // UUID-BIND: Bind UUID target'а к originalNick
                UUID targetUuid = target.getUniqueId();
                if (!plugin.uuidToNick.containsKey(targetUuid)) {
                    plugin.uuidToNick.put(targetUuid, originalNick);
                    plugin.saveUuidBindings();
                    plugin.getLogger().info("UUID-bound " + target.getName() + " to nick " + originalNick);
                }

                Map<String, String> paramsReduceIp = new HashMap<>();
                paramsReduceIp.put("nick", originalNick);
                paramsReduceIp.put("ip", ip);
                paramsReduceIp.put("time", (reduceDuration > 0 ? plugin.formatTime(reduceDuration) : "постоянно"));
                sender.sendMessage("§aУрон уменьшен для ника §e" + originalNick + " §a(IP: " + ip + ") на " + paramsReduceIp.get("time"));
                plugin.getLogger().info("IP-reduced outgoing damage for nick " + originalNick + " (IP " + ip + ")");
                return true;
            }

            case "amplify": {
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
                long expiryAmplify = (amplifyDuration > 0) ? System.currentTimeMillis() + amplifyDuration : 0L;
                TrueDamageNullifier.DamageModifier mod = TrueDamageNullifier.DamageModifier.AMPLIFIED;
                plugin.playerModifiers.put(targetAmplifyId, mod);
                if (amplifyDuration > 0) {
                    plugin.expiryTimes.put(targetAmplifyId, expiryAmplify);
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
                // UUID-BIND: Bind если mod != NORMAL
                String nickLower = targetAmplify.getName().toLowerCase();
                if (!plugin.uuidToNick.containsKey(targetAmplifyId)) {
                    plugin.uuidToNick.put(targetAmplifyId, nickLower);
                    plugin.saveUuidBindings();
                    plugin.getLogger().info("UUID-bound " + targetAmplify.getName() + " to nick " + nickLower);
                }
                plugin.applyNickModifierToUuidPlayer(targetAmplifyId, mod, expiryAmplify, "");
                return true;
            }

            case "ipamplify": {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /tdn ipamplify <nick> [time]");
                    return true;
                }
                if (!sender.hasPermission("tdn.ipamplify")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.getMessage("player_not_found"));
                    return true;
                }
                String timeStrAmplify = (args.length > 2) ? args[2] : "0";
                long amplifyDuration = plugin.parseTime(timeStrAmplify);
                if (amplifyDuration <= 0 && !"0".equals(timeStrAmplify)) {
                    sender.sendMessage(plugin.getMessage("incorrect_time_format"));
                    return true;
                }
                long expiryAmplify = (amplifyDuration > 0) ? System.currentTimeMillis() + amplifyDuration : 0L;

                String ip = plugin.getPlayerIp(target);
                String nickLower = target.getName().toLowerCase();
                String originalNick = plugin.ipToNick.get(ip);
                if (originalNick == null) {
                    plugin.ipToNick.put(ip, nickLower);
                    plugin.saveIpBindings();
                    sender.sendMessage("§aIP " + ip + " bound к нику " + nickLower);
                    originalNick = nickLower;
                }

                plugin.nickModifiers.put(originalNick, TrueDamageNullifier.DamageModifier.AMPLIFIED);
                plugin.nickExpiryTimes.put(originalNick, expiryAmplify);
                plugin.nickDisableReasons.remove(originalNick);
                plugin.saveNickModifiers();

                plugin.applyNickModifierToIpPlayers(ip, TrueDamageNullifier.DamageModifier.AMPLIFIED, expiryAmplify, "");

                // UUID-BIND: Bind UUID target'а к originalNick
                UUID targetUuid = target.getUniqueId();
                if (!plugin.uuidToNick.containsKey(targetUuid)) {
                    plugin.uuidToNick.put(targetUuid, originalNick);
                    plugin.saveUuidBindings();
                    plugin.getLogger().info("UUID-bound " + target.getName() + " to nick " + originalNick);
                }

                Map<String, String> paramsAmplifyIp = new HashMap<>();
                paramsAmplifyIp.put("nick", originalNick);
                paramsAmplifyIp.put("ip", ip);
                paramsAmplifyIp.put("time", (amplifyDuration > 0 ? plugin.formatTime(amplifyDuration) : "постоянно"));
                sender.sendMessage("§aУрон усилен для ника §e" + originalNick + " §a(IP: " + ip + ") на " + paramsAmplifyIp.get("time"));
                plugin.getLogger().info("IP-amplified incoming damage for nick " + originalNick + " (IP " + ip + ")");
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