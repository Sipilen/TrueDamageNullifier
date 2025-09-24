package com.sipilen.truedamagenullifier;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
            case "reload":
                if (!sender.hasPermission("tdn.admin")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.config = plugin.getConfig();
                plugin.playerModifiers.clear();
                plugin.expiryTimes.clear();
                plugin.disableReasons.clear();
                plugin.messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "messages.yml"));
                plugin.loadPlayerModifiers();
                sender.sendMessage(plugin.getMessage("config_reloaded"));
                return true;

            case "list":
                if (!sender.hasPermission("tdn.admin") && !sender.hasPermission("tdn.list")) {
                    sender.sendMessage(plugin.getMessage("no_permission"));
                    return true;
                }
                sender.sendMessage("§eСписок игроков с активными модификаторами:");
                for (UUID uuid : plugin.playerModifiers.keySet()) {
                    TrueDamageNullifier.DamageModifier modifier = plugin.playerModifiers.get(uuid);
                    if (modifier != TrueDamageNullifier.DamageModifier.NORMAL) {
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
                return true;

            case "clear":
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

            case "disable":
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
                break;

            case "reduce":
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
                break;

            case "amplify":
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
                break;

            case "status":
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
                break;

            default:
                sender.sendMessage(plugin.getMessage("unknown_command"));
                break;
        }

        plugin.savePlayerModifiers();
        return true;
    }
}