package it.gekojr.tags;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class NametagCommand implements CommandExecutor, TabCompleter {
    private final TagsPlugin plugin;

    public NametagCommand(TagsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tags.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add", "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /nametag add <player> <tag>");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String tag = args[2].toLowerCase();

                if (plugin.getTagConfig(tag) == null) {
                    sender.sendMessage("§cUnknown tag. Available: §f" + String.join(", ", plugin.getConfiguredTags()));
                    return true;
                }

                boolean added = plugin.addTag(target, tag);
                sender.sendMessage(added
                        ? "§aAdded §f" + plugin.displayName(tag) + "§a to §f" + args[1] + "§a."
                        : "§e" + args[1] + "§e already owns §f" + plugin.displayName(tag) + "§e.");
                return true;
            }

            case "force" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /nametag force <player> <tag> <duration>");
                    sender.sendMessage("§7Examples: §f10s§7, §f30m§7, §f2h§7, §f1d");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String tag = args[2].toLowerCase();

                if (plugin.getTagConfig(tag) == null) {
                    sender.sendMessage("§cUnknown tag. Available: §f" + String.join(", ", plugin.getConfiguredTags()));
                    return true;
                }

                long duration = parseDuration(args[3]);
                if (duration <= 0) {
                    sender.sendMessage("§cInvalid duration. Use formats like §f30s§c, §f10m§c, §f2h§c or §f1d§c.");
                    return true;
                }

                plugin.forceTag(target, tag, duration);
                sender.sendMessage("§aForced §f" + plugin.displayName(tag) + "§a on §f" + args[1]
                        + "§a for §f" + formatDuration(duration) + "§a.");
                return true;
            }

            case "unforce" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nametag unforce <player>");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (plugin.unforceTag(target)) {
                    sender.sendMessage("§aRemoved forced nametag from §f" + args[1] + "§a.");
                } else {
                    sender.sendMessage("§e" + args[1] + "§e does not have a forced nametag.");
                }
                return true;
            }

            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nametag remove <player> [tag]");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

                if (args.length >= 3) {
                    String tag = args[2].toLowerCase();
                    boolean removed = plugin.removeTag(target, tag);
                    sender.sendMessage(removed
                            ? "§aRemoved §f" + plugin.displayName(tag) + "§a from §f" + args[1] + "§a."
                            : "§e" + args[1] + "§e does not own that tag.");
                } else {
                    plugin.removeAllTags(target);
                    sender.sendMessage("§aRemoved all nametags from §f" + args[1] + "§a.");
                }
                return true;
            }

            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /nametag get <player>");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                List<String> tags = plugin.getTags(target);

                if (tags.isEmpty()) {
                    sender.sendMessage("§e" + args[1] + "§e owns no nametags.");
                } else {
                    sender.sendMessage("§a" + args[1] + "§a owns: §f" +
                            tags.stream().map(plugin::displayName).reduce((a, b) -> a + ", " + b).orElse(""));
                    String selected = plugin.getSelectedTag(target);
                    sender.sendMessage("§7Selected: §f" + (selected == null ? "none" : plugin.displayName(selected)));
                }
                return true;
            }

            case "list" -> {
                sender.sendMessage("§6Available tags: §f" + String.join(", ", plugin.getConfiguredTags()));
                return true;
            }

            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage("§aTags configuration reloaded.");
                return true;
            }

            default -> {
                help(sender);
                return true;
            }
        }
    }

    private long parseDuration(String input) {
        if (input == null || input.length() < 2) return -1;

        String valuePart = input.substring(0, input.length() - 1);
        char unit = Character.toLowerCase(input.charAt(input.length() - 1));

        try {
            long value = Long.parseLong(valuePart);
            if (value <= 0) return -1;

            return switch (unit) {
                case 's' -> Math.multiplyExact(value, 1000L);
                case 'm' -> Math.multiplyExact(value, 60_000L);
                case 'h' -> Math.multiplyExact(value, 3_600_000L);
                case 'd' -> Math.multiplyExact(value, 86_400_000L);
                default -> -1;
            };
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds % 86_400 == 0) return (seconds / 86_400) + "d";
        if (seconds % 3_600 == 0) return (seconds / 3_600) + "h";
        if (seconds % 60 == 0) return (seconds / 60) + "m";
        return seconds + "s";
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6§lTags");
        sender.sendMessage("§e/nametag add <player> <tag> §7- Give a player a nametag");
        sender.sendMessage("§e/nametag force <player> <tag> <duration> §7- Force a nametag temporarily");
        sender.sendMessage("§e/nametag unforce <player> §7- Remove a forced nametag");
        sender.sendMessage("§e/nametag remove <player> [tag] §7- Remove one or all nametags");
        sender.sendMessage("§e/nametag get <player> §7- Show owned nametags");
        sender.sendMessage("§e/nametag list §7- List available nametags");
        sender.sendMessage("§e/nametag reload §7- Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.asList("add", "set", "force", "unforce", "remove", "get", "list", "reload"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("force")
                || args[0].equalsIgnoreCase("unforce")
                || args[0].equalsIgnoreCase("remove")
                || args[0].equalsIgnoreCase("get"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return partial(names, args[1]);
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("force")
                || args[0].equalsIgnoreCase("remove"))) {
            return partial(Arrays.asList(plugin.getConfiguredTags()), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("force")) {
            return partial(Arrays.asList("10s", "30s", "1m", "5m", "10m", "30m", "1h", "6h", "1d"), args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> partial(List<String> values, String input) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) result.add(value);
        }
        return result;
    }
}
