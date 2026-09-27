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
                    sender.sendMessage("§7Selected: §f" + plugin.displayName(plugin.getSelectedTag(target)));
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

    private void help(CommandSender sender) {
        sender.sendMessage("§6§lTags");
        sender.sendMessage("§e/nametag add <player> <tag> §7- Give a player a nametag");
        sender.sendMessage("§e/nametag remove <player> [tag] §7- Remove one or all nametags");
        sender.sendMessage("§e/nametag get <player> §7- Show owned nametags");
        sender.sendMessage("§e/nametag list §7- List available nametags");
        sender.sendMessage("§e/nametag reload §7- Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.asList("add", "set", "remove", "get", "list", "reload"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("remove")
                || args[0].equalsIgnoreCase("get"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return partial(names, args[1]);
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("remove"))) {
            return partial(Arrays.asList(plugin.getConfiguredTags()), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> partial(List<String> values, String input) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(input.toLowerCase())) result.add(value);
        }
        return result;
    }
}
