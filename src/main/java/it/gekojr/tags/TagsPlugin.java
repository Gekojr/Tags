package it.gekojr.tags;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final Map<UUID, Long> playtimeMillis = new HashMap<>();
    private final Map<UUID, Long> sessionStartMillis = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfiguredTags();
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
        getServer().getScheduler().runTaskTimer(this, this::updatePlaytimeTags, 20L * 60L, 20L * 60L);
        getServer().getScheduler().runTaskTimer(this, this::saveOnlinePlaytime, 20L * 60L, 20L * 60L);

        getLogger().info("Tags enabled. Available tags: " + String.join(", ", getConfiguredTags()));
    }

    private void ensureConfiguredTags() {
        // Add newly introduced tags to an existing server config without overwriting custom settings.
        Map<String, String[]> defaults = new java.util.LinkedHashMap<>();
        defaults.put("helper", new String[]{"HELPER", "70", "#32CD32"});
        defaults.put("discord-mod", new String[]{"DISCORD MOD", "60", "#00008B"});
        defaults.put("chad", new String[]{"CHAD", "55", "#555555"});
        defaults.put("social-media-manager", new String[]{"SOCIAL MEDIA MANAGER", "65", "#00008B"});
        defaults.put("schiavo", new String[]{"SLAVE", "30", "#AA00AA"});
        defaults.put("new", new String[]{"NEW", "16", "#AAAAAA"});
        defaults.put("member", new String[]{"MEMBER", "17", "#55FF55"});
        defaults.put("veteran", new String[]{"VETERAN", "18", "#55FFFF"});
        defaults.put("elite", new String[]{"ELITE", "19", "#AA00AA"});
        defaults.put("legend", new String[]{"LEGEND", "20", "#FFAA00"});

        boolean changed = false;
        for (Map.Entry<String, String[]> entry : defaults.entrySet()) {
            String path = "tags." + entry.getKey();
            if (!getConfig().isConfigurationSection(path)) {
                getConfig().set(path + ".display", entry.getValue()[0]);
                getConfig().set(path + ".priority", Integer.parseInt(entry.getValue()[1]));
                getConfig().set(path + ".color", entry.getValue()[2]);
                changed = true;
            }
        }

        if (changed) {
            saveConfig();
            reloadConfig();
        }
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

                long storedPlaytime = players.getLong(key + ".playtime", 0L);
                if (storedPlaytime > 0L) playtimeMillis.put(uuid, storedPlaytime);

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
        saveOnlinePlaytime();
        Set<UUID> uuids = new LinkedHashSet<>();
        uuids.addAll(playerTags.keySet());
        uuids.addAll(forcedTags.keySet());
        uuids.addAll(playtimeMillis.keySet());

        for (UUID uuid : uuids) {
            data.set("players." + uuid + ".tags", new ArrayList<>(
                    playerTags.getOrDefault(uuid, new LinkedHashSet<>())
            ));

            String selected = selectedTags.get(uuid);
            data.set("players." + uuid + ".selected", selected != null ? selected : "none");
            data.set("players." + uuid + ".playtime", getCurrentPlaytimeMillis(uuid));

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

        List<String> sorted = new ArrayList<>(tags);
        sorted.sort((a, b) -> {
            int priorityA = getTagPriority(a);
            int priorityB = getTagPriority(b);

            int priorityCompare = Integer.compare(priorityB, priorityA);
            if (priorityCompare != 0) return priorityCompare;

            return displayName(a).compareToIgnoreCase(displayName(b));
        });

        return sorted;
    }

    private int getTagPriority(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        return section == null ? 0 : section.getInt("priority", 0);
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

        String playtimeTag = getPlaytimeTag(player);
        if (playtimeTag != null) return playtimeTag;

        return selectedTags.get(uuid);
    }

    public void resetPlaytime(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();

        // Reset only playtime. Never remove or modify owned/selected nametags.
        if (player.isOnline()) {
            playtimeMillis.put(uuid, 0L);
            sessionStartMillis.put(uuid, System.currentTimeMillis());
        } else {
            playtimeMillis.remove(uuid);
            sessionStartMillis.remove(uuid);
        }

        saveData();
    }

    public void resetAllPlaytime() {
        // Reset only playtime. Never remove or modify owned/selected nametags.
        for (Player player : getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            playtimeMillis.put(uuid, 0L);
            sessionStartMillis.put(uuid, System.currentTimeMillis());
        }

        for (UUID uuid : new LinkedHashSet<>(playtimeMillis.keySet())) {
            if (getServer().getPlayer(uuid) == null) {
                playtimeMillis.remove(uuid);
                sessionStartMillis.remove(uuid);
            }
        }

        saveData();
    }

    public String getPlaytimeTag(OfflinePlayer player) {
        if (!getConfig().getBoolean("playtime.enabled", false)) return null;
        ConfigurationSection tiers = getConfig().getConfigurationSection("playtime.tiers");
        if (tiers == null) return null;

        double hours = getCurrentPlaytimeMillis(player.getUniqueId()) / 3_600_000.0;
        String bestTag = null;
        double bestHours = -1;
        for (String key : tiers.getKeys(false)) {
            double required = tiers.getDouble(key + ".hours", -1);
            String tag = tiers.getString(key + ".tag");
            if (tag != null && required >= 0 && required <= hours && required >= bestHours && getTagConfig(tag) != null) {
                bestHours = required;
                bestTag = tag.toLowerCase();
            }
        }
        return bestTag;
    }

    private long getCurrentPlaytimeMillis(UUID uuid) {
        long total = playtimeMillis.getOrDefault(uuid, 0L);
        Long start = sessionStartMillis.get(uuid);
        if (start != null) total += Math.max(0L, System.currentTimeMillis() - start);
        return total;
    }

    private void saveOnlinePlaytime() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(sessionStartMillis.keySet())) {
            long start = sessionStartMillis.get(uuid);
            playtimeMillis.merge(uuid, Math.max(0L, now - start), Long::sum);
            sessionStartMillis.put(uuid, now);
        }
        if (data != null && !sessionStartMillis.isEmpty()) saveDataWithoutPlaytimeRecursion();
    }

    private void saveDataWithoutPlaytimeRecursion() {
        for (UUID uuid : playtimeMillis.keySet()) {
            data.set("players." + uuid + ".playtime", playtimeMillis.get(uuid));
        }
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save data.yml: " + e.getMessage()); }
    }

    private void startPlaytimeSession(Player player) {
        UUID uuid = player.getUniqueId();
        // Playtime is tracked for the whole server, regardless of world.
        // Existing time is loaded from data.yml and is never reset on world changes.
        sessionStartMillis.put(uuid, System.currentTimeMillis());
        ensurePlaytimeTag(player);
    }

    private void ensurePlaytimeTag(Player player) {
        String playtimeTag = getPlaytimeTag(player);
        if (playtimeTag == null) return;

        LinkedHashSet<String> tags = playerTags.computeIfAbsent(
                player.getUniqueId(), ignored -> new LinkedHashSet<>()
        );

        if (tags.add(playtimeTag)) {
            saveData();

            String display = displayName(playtimeTag);
            player.sendMessage(Component.text("Nametag unlocked: ", TextColor.color(255, 255, 255))
                    .append(renderTag(playtimeTag))
                    .append(Component.text("!", TextColor.color(255, 255, 255))));
        }
    }

    private void updatePlaytimeTags() {
        for (Player player : getServer().getOnlinePlayers()) {
            ensurePlaytimeTag(player);
        }
    }

    private void endPlaytimeSession(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = sessionStartMillis.remove(uuid);
        if (start != null) playtimeMillis.merge(uuid, Math.max(0L, System.currentTimeMillis() - start), Long::sum);
        saveDataWithoutPlaytimeRecursion();
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        startPlaytimeSession(event.getPlayer());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        endPlaytimeSession(event.getPlayer());
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

    private boolean isBoldTag(String tag) {
        return tag != null && switch (tag.toLowerCase()) {
            case "admin", "legend" -> true;
            default -> false;
        };
    }

    public Component renderTag(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        if (section == null) return Component.empty();

        String display = section.getString("display", tag.toUpperCase());
        boolean brackets = getConfig().getBoolean("chat.tag-brackets", true);

        Component result;

        if (tag.equalsIgnoreCase("schiavo")) {
            TextColor white = TextColor.color(255, 255, 255);
            TextColor accent = TextColor.color(85, 85, 85);

            Component letters = Component.empty();
            for (int i = 0; i < display.length(); i++) {
                TextColor color = (i % 2 == 0) ? white : accent;
                letters = letters.append(Component.text(String.valueOf(display.charAt(i)), color));
            }

            result = letters;
            if (brackets) {
                result = Component.text("[", white).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .append(result)
                        .append(Component.text("]", white).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            }
        } else {
            TextColor color;

            // CHAD must always use dark gray in chat, regardless of an old config value.
            if (tag.equalsIgnoreCase("chad")) {
                color = TextColor.color(85, 85, 85);
            } else {
                color = TextColor.fromHexString(section.getString("color", "#FFFFFF"));
                if (color == null) color = TextColor.color(255, 255, 255);
            }

            String text = brackets ? "[" + display + "]" : display;
            result = isBoldTag(tag) ? Component.text(text, color).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD) : Component.text(text, color);
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
                .append(renderPlayerName(player, tag))
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
        playtimeMillis.clear();
        sessionStartMillis.clear();
        loadData();
        for (Player player : getServer().getOnlinePlayers()) startPlaytimeSession(player);
    }

    public String displayName(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        return section == null ? tag : section.getString("display", tag.toUpperCase());
    }


    private Component renderPlayerName(Player player, String tag) {
        String name = player.getName();
        Component result = Component.empty();

        if (tag.equalsIgnoreCase("schiavo")) {
            TextColor white = TextColor.color(255, 255, 255);
            TextColor gray = TextColor.color(85, 85, 85);

            for (int i = 0; i < name.length(); i++) {
                TextColor color = (i % 2 == 0) ? white : gray;
                result = result.append(Component.text(String.valueOf(name.charAt(i)), color));
            }
            return result;
        }

        TextColor nameColor = tag.equalsIgnoreCase("chad")
                ? TextColor.color(85, 85, 85)
                : getTagInfo(tag).color();

        return Component.text(name, nameColor);
    }

    public record TagInfo(String display, TextColor color) {}
}
