package it.gekojr.tags;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TagsPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, LinkedHashSet<String>> playerTags = new HashMap<>();
    private final Map<UUID, String> selectedTags = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new TagMenu(this), this);

        NametagCommand command = new NametagCommand(this);
        getCommand("nametag").setExecutor(command);
        getCommand("nametag").setTabCompleter(command);

        getCommand("tag").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /tag.");
                return true;
            }
            if (args.length != 0) {
                player.sendMessage("§cUse /tag to open your nametag menu.");
                return true;
            }
            new TagMenu(this).open(player);
            return true;
        });

        getLogger().info("Tags enabled. Available tags: " + String.join(", ", getConfiguredTags()));
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }

        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);

                List<String> tags = players.getStringList(key + ".tags");
                if (tags.isEmpty()) {
                    String oldTag = players.getString(key);
                    if (oldTag != null && getTagConfig(oldTag) != null) {
                        tags = List.of(oldTag);
                    }
                }

                LinkedHashSet<String> owned = new LinkedHashSet<>();
                for (String tag : tags) {
                    if (getTagConfig(tag) != null) owned.add(tag.toLowerCase());
                }

                if (!owned.isEmpty()) {
                    playerTags.put(uuid, owned);

                    String selected = players.getString(key + ".selected");
                    if (selected != null && owned.contains(selected.toLowerCase())) {
                        selectedTags.put(uuid, selected.toLowerCase());
                    } else {
                        selectedTags.put(uuid, owned.iterator().next());
                    }
                }
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Invalid UUID in data.yml: " + key);
            }
        }
    }

    public void saveData() {
        if (data == null) return;

        data.set("players", null);
        for (Map.Entry<UUID, LinkedHashSet<String>> entry : playerTags.entrySet()) {
            UUID uuid = entry.getKey();
            data.set("players." + uuid + ".tags", new ArrayList<>(entry.getValue()));

            String selected = selectedTags.get(uuid);
            if (selected != null) {
                data.set("players." + uuid + ".selected", selected);
            } else {
                data.set("players." + uuid + ".selected", "none");
            }
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public boolean addTag(OfflinePlayer player, String tag) {
        if (getTagConfig(tag) == null) return false;

        String normalized = tag.toLowerCase();
        LinkedHashSet<String> tags = playerTags.computeIfAbsent(
                player.getUniqueId(), ignored -> new LinkedHashSet<>()
        );

        boolean added = tags.add(normalized);
        selectedTags.putIfAbsent(player.getUniqueId(), normalized);
        saveData();
        return added;
    }

    public boolean removeTag(OfflinePlayer player, String tag) {
        LinkedHashSet<String> tags = playerTags.get(player.getUniqueId());
        if (tags == null) return false;

        boolean removed = tags.remove(tag.toLowerCase());

        if (tags.isEmpty()) {
            playerTags.remove(player.getUniqueId());
            selectedTags.remove(player.getUniqueId());
        } else if (tag.equalsIgnoreCase(selectedTags.get(player.getUniqueId()))) {
            selectedTags.put(player.getUniqueId(), tags.iterator().next());
        }

        saveData();
        return removed;
    }

    public void removeAllTags(OfflinePlayer player) {
        playerTags.remove(player.getUniqueId());
        selectedTags.remove(player.getUniqueId());
        saveData();
    }

    public boolean hasTag(OfflinePlayer player, String tag) {
        return playerTags.getOrDefault(player.getUniqueId(), new LinkedHashSet<>())
                .contains(tag.toLowerCase());
    }

    public List<String> getTags(OfflinePlayer player) {
        Set<String> tags = playerTags.get(player.getUniqueId());
        if (tags == null) return Collections.emptyList();
        return List.copyOf(tags);
    }

    public String getSelectedTag(OfflinePlayer player) {
        return selectedTags.get(player.getUniqueId());
    }

    public void selectTag(Player player, String tag) {
        if (!hasTag(player, tag)) return;
        selectedTags.put(player.getUniqueId(), tag.toLowerCase());
        saveData();
    }

    public void disableTag(Player player) {
        selectedTags.remove(player.getUniqueId());
        saveData();
    }

    public ConfigurationSection getTagConfig(String tag) {
        if (tag == null) return null;
        return getConfig().getConfigurationSection("tags." + tag.toLowerCase());
    }

    public String[] getConfiguredTags() {
        ConfigurationSection section = getConfig().getConfigurationSection("tags");
        return section == null ? new String[0] : section.getKeys(false).toArray(new String[0]);
    }

    public TagInfo getTagInfo(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        if (section == null) {
            return new TagInfo(tag, TextColor.color(255, 255, 255));
        }

        String display = section.getString("display", tag.toUpperCase());
        TextColor color = TextColor.fromHexString(section.getString("color", "#FFFFFF"));
        if (color == null) color = TextColor.color(255, 255, 255);

        return new TagInfo(display, color);
    }

    public Component renderTag(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        if (section == null) return Component.empty();

        String display = section.getString("display", tag.toUpperCase());
        boolean brackets = getConfig().getBoolean("chat.tag-brackets", true);

        Component result;

        if (tag.equalsIgnoreCase("schiavo")) {
            TextColor white = TextColor.color(255, 255, 255);
            TextColor black = TextColor.color(0, 0, 0);

            Component letters = Component.empty();
            for (int i = 0; i < display.length(); i++) {
                TextColor color = (i % 2 == 0) ? white : black;
                letters = letters.append(Component.text(String.valueOf(display.charAt(i)), color));
            }

            result = letters;
            if (brackets) {
                result = Component.text("[", white)
                        .append(result)
                        .append(Component.text("]", white));
            }
        } else {
            TextColor color = TextColor.fromHexString(section.getString("color", "#FFFFFF"));
            if (color == null) color = TextColor.color(255, 255, 255);

            String text = brackets ? "[" + display + "]" : display;
            result = Component.text(text, color);
        }

        return result;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String tag = getSelectedTag(player);

        if (tag == null || getTagConfig(tag) == null) return;

        String separator = getConfig().getString("chat.separator", " » ");
        TextColor white = TextColor.color(255, 255, 255);

        Component prefix = renderTag(tag)
                .append(Component.space())
                .append(Component.text(player.getName(), white))
                .append(Component.text(separator, white));

        event.renderer((source, sourceDisplayName, message, viewer) ->
                prefix.append(Component.text().color(white).append(message))
        );
    }

    public void reloadPlugin() {
        reloadConfig();
        playerTags.clear();
        selectedTags.clear();
        loadData();
    }

    public String displayName(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        return section == null ? tag : section.getString("display", tag.toUpperCase());
    }

    public record TagInfo(String display, TextColor color) {}
}
