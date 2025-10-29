package com.sipilen.truedamagenullifier;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TDNTabCompleter implements TabCompleter {

    private final TrueDamageNullifier plugin;

    public TDNTabCompleter(TrueDamageNullifier plugin) {
        this.plugin = plugin;
    }

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "disable", "reduce", "amplify", "status", "reload", "list", "clear",
            "ipdisable", "ipreduce", "ipamplify", "nickclear", "nicklist", "ipclear", "alts"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return MAIN_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && Arrays.asList("disable", "reduce", "amplify", "status", "clear").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && Arrays.asList("ipdisable", "ipreduce", "ipamplify", "nickclear", "ipclear", "alts").contains(args[0].toLowerCase())) {
            // Для ников - предлагаем онлайн-игроков (оригинальный регистр) + исторические ники из привязок IP (lowercase)
            List<String> suggestions = new ArrayList<>();
            String partial = args[1].toLowerCase();

            // Сначала онлайн-игроки (оригинальный регистр)
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                String lowerName = name.toLowerCase();
                if (lowerName.startsWith(partial)) {
                    suggestions.add(name);
                }
            }

            // Затем исторические ники (lowercase), если не совпадают с онлайн (по lower)
            for (String histNick : plugin.ipToNick.values()) {
                if (histNick.startsWith(partial) &&
                        suggestions.stream().noneMatch(s -> s.toLowerCase().equals(histNick))) {
                    suggestions.add(histNick);
                }
            }

            return suggestions;
        }
        if (args.length == 3 && Arrays.asList("disable", "reduce", "amplify", "ipdisable", "ipreduce", "ipamplify").contains(args[0].toLowerCase())) {
            return Arrays.asList("30s", "1m", "5m", "1h", "0");
        }
        if (args.length >= 4 && (args[0].equalsIgnoreCase("disable") || args[0].equalsIgnoreCase("ipdisable"))) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}