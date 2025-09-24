package com.sipilen.truedamagenullifier;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;

public class TDNCommand implements CommandExecutor {
    private final TrueDamageNullifier plugin;

    public TDNCommand(TrueDamageNullifier plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /tdn <disable|reduce|amplify|status|reload|list|clear> [игрок] [время] [причина]");
            return true;
        }
        String action = args[0].toLowerCase();

        if (action.equals("reload")) {
            if (!sender.hasPermission("tdn.admin")) {
                sender.sendMessage("§cУ вас нет прав для этой команды!");
                return true;
            }
            plugin.reloadConfig();
            plugin.config = plugin.getConfig();
            plugin.playerModifiers.clear();
            plugin.expiryTimes.clear();
            plugin.disableReasons.clear();
            plugin.loadPlayerModifiers();
            sender.sendMessage("§aКонфигурация перезагружена!");
            return true;
        }

        if (action.equals("list")) {
            if (!sender.hasPermission("tdn.admin") && !sender.hasPermission("tdn.list")) {
                sender.sendMessage("§cУ вас нет прав для этой команды!");
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
        }

        if (action.equals("clear")) {
            if (args.length < 2) {
                sender.sendMessage("§cИспользование: /tdn clear <игрок>");
                return true;
            }
            if (!sender.hasPermission("tdn.clear")) {
                sender.sendMessage("§cУ вас нет прав для этой команды!");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cИгрок не найден!");
                return true;
            }
            UUID targetId = target.getUniqueId();
            plugin.playerModifiers.put(targetId, TrueDamageNullifier.DamageModifier.NORMAL);
            plugin.expiryTimes.remove(targetId);
            plugin.disableReasons.remove(targetId);
            plugin.savePlayerModifiers();
            sender.sendMessage("§aВсе модификаторы урона для " + target.getName() + " сняты. PvP работает стандартно.");
            target.sendMessage("§aВаши модификаторы урона сняты. PvP работает стандартно.");
            return true;
        }

        if (args.length < 2 && !action.equals("list")) {
            sender.sendMessage("§cУкажите имя игрока!");
            return true;
        }

        if (action.equals("list")) return true;

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден!");
            return true;
        }

        UUID targetId = target.getUniqueId();

        switch (action) {
            case "disable":
                if (!sender.hasPermission("tdn.disable")) {
                    sender.sendMessage("§cУ вас нет прав для этой команды!");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§cИспользование: /tdn disable <игрок> <время> <причина>");
                    return true;
                }
                long duration = plugin.parseTime(args[2]);
                if (duration <= 0 && !"0".equals(args[2])) {
                    sender.sendMessage("§cНеверный формат времени! Используйте, например, 30s, 5m, 1h или 0 для постоянного.");
                    return true;
                }
                String disableReason = String.join(" ", args).substring(args[0].length() + args[1].length() + args[2].length() + 3).trim();
                plugin.playerModifiers.put(targetId, TrueDamageNullifier.DamageModifier.DISABLED);
                if (duration > 0) {
                    plugin.expiryTimes.put(targetId, System.currentTimeMillis() + duration);
                } else {
                    plugin.expiryTimes.remove(targetId);
                }
                plugin.disableReasons.put(targetId, disableReason);
                sender.sendMessage("§aУрон для " + target.getName() + " отключен. Причина: " + disableReason + ". Время: " + (duration > 0 ? plugin.formatTime(duration) : "постоянно"));
                target.sendMessage("§cВаш PvP отключен. Причина: " + disableReason + ". Время: " + (duration > 0 ? plugin.formatTime(duration) : "постоянно"));
                plugin.getLogger().info("Disabled damage for " + target.getName() + " for " + (duration > 0 ? plugin.formatTime(duration) : "permanently") + ". Reason: " + disableReason);
                break;

            case "reduce":
                if (!sender.hasPermission("tdn.reduce")) {
                    sender.sendMessage("§cУ вас нет прав для этой команды!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /tdn reduce <игрок> <время>");
                    return true;
                }
                long reduceDuration = plugin.parseTime(args[2]);
                if (reduceDuration <= 0 && !"0".equals(args[2])) {
                    sender.sendMessage("§cНеверный формат времени! Используйте, например, 30s, 5m, 1h или 0 для постоянного.");
                    return true;
                }
                plugin.playerModifiers.put(targetId, TrueDamageNullifier.DamageModifier.REDUCED);
                if (reduceDuration > 0) {
                    plugin.expiryTimes.put(targetId, System.currentTimeMillis() + reduceDuration);
                } else {
                    plugin.expiryTimes.remove(targetId);
                }
                plugin.disableReasons.remove(targetId);
                sender.sendMessage("§aУрон, наносимый " + target.getName() + ", уменьшен до 1/3. Время: " + (reduceDuration > 0 ? plugin.formatTime(reduceDuration) : "постоянно"));
                plugin.getLogger().info("Reduced outgoing damage for " + target.getName());
                break;

            case "amplify":
                if (!sender.hasPermission("tdn.amplify")) {
                    sender.sendMessage("§cУ вас нет прав для этой команды!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /tdn amplify <игрок> <время>");
                    return true;
                }
                long amplifyDuration = plugin.parseTime(args[2]);
                if (amplifyDuration <= 0 && !"0".equals(args[2])) {
                    sender.sendMessage("§cНеверный формат времени! Используйте, например, 30s, 5m, 1h или 0 для постоянного.");
                    return true;
                }
                plugin.playerModifiers.put(targetId, TrueDamageNullifier.DamageModifier.AMPLIFIED);
                if (amplifyDuration > 0) {
                    plugin.expiryTimes.put(targetId, System.currentTimeMillis() + amplifyDuration);
                } else {
                    plugin.expiryTimes.remove(targetId);
                }
                plugin.disableReasons.remove(targetId);
                sender.sendMessage("§aУрон по " + target.getName() + " от игроков увеличен в 4 раза. Время: " + (amplifyDuration > 0 ? plugin.formatTime(amplifyDuration) : "постоянно"));
                plugin.getLogger().info("Amplified incoming damage for " + target.getName());
                break;

            case "status":
                if (!sender.hasPermission("tdn.status")) {
                    sender.sendMessage("§cУ вас нет прав для этой команды!");
                    return true;
                }
                TrueDamageNullifier.DamageModifier modifier = plugin.playerModifiers.getOrDefault(targetId, TrueDamageNullifier.DamageModifier.NORMAL);
                String statusMessage = "§aСтатус урона для " + target.getName() + ": " + modifier.toString();
                if (modifier != TrueDamageNullifier.DamageModifier.NORMAL) {
                    long expiry = plugin.expiryTimes.getOrDefault(targetId, 0L);
                    String timeLeft = expiry > 0 ? plugin.formatTime(expiry - System.currentTimeMillis()) : "постоянно";
                    statusMessage += ". Время: " + timeLeft;
                    if (modifier == TrueDamageNullifier.DamageModifier.DISABLED) {
                        String storedReason = plugin.disableReasons.getOrDefault(targetId, "Не указана");
                        statusMessage += ". Причина: " + storedReason;
                    }
                }
                sender.sendMessage(statusMessage);
                break;

            default:
                sender.sendMessage("§cНеизвестная команда! Используйте: disable, reduce, amplify, status, reload, list, clear");
                break;
        }

        plugin.savePlayerModifiers();
        return true;
    }
}