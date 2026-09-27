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
    private final Map<UUID, String> forcedTags = new HashMap<>();
    private final Map<UUID, Long> forcedTagExpiry = new HashMap<>();
    private final Map<UUID, String> forcedTagPrevious = new HashMap<>();
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

        getServer().getScheduler().runTaskTimer(this, this::expireForcedTags, 20L, 20L);

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
                    if (selected != null && !"none".equalsIgnoreCase(selected) && owned.contains(selected.toLowerCase())) {
                        selectedTags.put(uuid, selected.toLowerCase());
                    } else if (selected == null) {
                        selectedTags.put(uuid, owned.iterator().next());
                    }
                }

                String forced = players.getString(key + ".forced");
                long expiry = players.getLong(key + ".forced-expiry", 0L);
                if (forced != null && getTagConfig(forced) != null && expiry > System.currentTimeMillis()) {
                    forcedTags.put(uuid, forced.toLowerCase());
                    forcedTagExpiry.put(uuid, expiry);

                    String previous = players.getString(key + ".forced-previous");
                    if (previous != null && !"none".equalsIgnoreCase(previous)) {
                        forcedTagPrevious.put(uuid, previous.toLowerCase());
                    }
                }
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Invalid UUID in data.yml: " + key);
            }
        }

        expireForcedTags();
    }

    public void saveData() {
        if (data == null) return;

        data.set("players", null);
        Set<UUID> uuids = new LinkedHashSet<>();
        uuids.addAll(playerTags.keySet());
        uuids.addAll(forcedTags.keySet());

        for (UUID uuid : uuids) {
            data.set("players." + uuid + ".tags", new ArrayList<>(
                    playerTags.getOrDefault(uuid, new LinkedHashSet<>())
            ));

            String selected = selectedTags.get(uuid);
            data.set("players." + uuid + ".selected", selected != null ? selected : "none");

            String forced = forcedTags.get(uuid);
            if (forced != null) {
                data.set("players." + uuid + ".forced", forced);
                data.set("players." + uuid + ".forced-expiry", forcedTagExpiry.getOrDefault(uuid, 0L));
                data.set("players." + uuid + ".forced-previous",
                        forcedTagPrevious.getOrDefault(uuid, "none"));
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
        UUID uuid = player.getUniqueId();
        String forced = forcedTags.get(uuid);

        if (forced != null) {
            long expiry = forcedTagExpiry.getOrDefault(uuid, 0L);
            if (expiry > System.currentTimeMillis()) {
                return forced;
            }
            expireForcedTag(uuid);
        }

        return selectedTags.get(uuid);
    }

    public void selectTag(Player player, String tag) {
        if (isTagForced(player)) return;
        if (!hasTag(player, tag)) return;
        selectedTags.put(player.getUniqueId(), tag.toLowerCase());
        saveData();
    }

    public void disableTag(Player player) {
        if (isTagForced(player)) return;
        selectedTags.remove(player.getUniqueId());
        saveData();
    }

    public boolean isTagForced(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        Long expiry = forcedTagExpiry.get(uuid);
        if (!forcedTags.containsKey(uuid)) return false;
        if (expiry != null && expiry > System.currentTimeMillis()) return true;

        expireForcedTag(uuid);
        return false;
    }

    public boolean forceTag(OfflinePlayer player, String tag, long durationMillis) {
        if (getTagConfig(tag) == null || durationMillis <= 0) return false;

        UUID uuid = player.getUniqueId();
        String previous = getSelectedTag(player);
        if (forcedTags.containsKey(uuid)) {
            previous = forcedTagPrevious.get(uuid);
        }

        if (previous != null && getTagConfig(previous) != null) {
            forcedTagPrevious.put(uuid, previous.toLowerCase());
        } else {
            forcedTagPrevious.remove(uuid);
        }

        forcedTags.put(uuid, tag.toLowerCase());
        forcedTagExpiry.put(uuid, System.currentTimeMillis() + durationMillis);
        saveData();
        return true;
    }

    public boolean unforceTag(OfflinePlayer player) {
        if (!forcedTags.containsKey(player.getUniqueId())) return false;
        expireForcedTag(player.getUniqueId());
        return true;
    }

    private void expireForcedTags() {
        List<UUID> expired = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : forcedTagExpiry.entrySet()) {
            if (entry.getValue() <= now) expired.add(entry.getKey());
        }

        if (!expired.isEmpty()) {
            for (UUID uuid : expired) expireForcedTag(uuid);
            saveData();
        }
    }

    private void expireForcedTag(UUID uuid) {
        String previous = forcedTagPrevious.remove(uuid);
        forcedTags.remove(uuid);
        forcedTagExpiry.remove(uuid);

        if (previous != null && getTagConfig(previous) != null
                && playerTags.getOrDefault(uuid, new LinkedHashSet<>()).contains(previous)) {
            selectedTags.put(uuid, previous);
        } else {
            selectedTags.remove(uuid);
        }
    }

    public long getForcedTagRemainingMillis(OfflinePlayer player) {
        if (!isTagForced(player)) return 0L;
        return Math.max(0L, forcedTagExpiry.get(player.getUniqueId()) - System.currentTimeMillis());
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
        TextColor nameColor = getTagInfo(tag).color();

        Component prefix = renderTag(tag)
                .append(Component.space())
                .append(Component.text(player.getName(), nameColor))
                .append(Component.text(separator, white));

        event.renderer((source, sourceDisplayName, message, viewer) ->
                prefix.append(Component.text().color(white).append(message))
        );
    }

    public void reloadPlugin() {
        reloadConfig();
        playerTags.clear();
        selectedTags.clear();
        forcedTags.clear();
        forcedTagExpiry.clear();
        forcedTagPrevious.clear();
        loadData();
    }

    public String displayName(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        return section == null ? tag : section.getString("display", tag.toUpperCase());
    }

    public record TagInfo(String display, TextColor color) {}
}
