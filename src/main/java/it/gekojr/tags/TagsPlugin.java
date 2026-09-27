package it.gekojr.tags;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TagsPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, String> playerTags = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        NametagCommand command = new NametagCommand(this);
        getCommand("nametag").setExecutor(command);
        getCommand("nametag").setTabCompleter(command);
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
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String tag = section.getString(key);
                if (tag != null && getTagConfig(tag) != null) {
                    playerTags.put(uuid, tag.toLowerCase());
                }
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Invalid UUID in data.yml: " + key);
            }
        }
    }

    public void saveData() {
        if (data == null) return;
        data.set("players", null);
        for (Map.Entry<UUID, String> entry : playerTags.entrySet()) {
            data.set("players." + entry.getKey(), entry.getValue());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public boolean setTag(OfflinePlayer player, String tag) {
        if (getTagConfig(tag) == null) return false;
        playerTags.put(player.getUniqueId(), tag.toLowerCase());
        saveData();
        return true;
    }

    public boolean removeTag(OfflinePlayer player) {
        return playerTags.remove(player.getUniqueId()) != null;
    }

    public String getTag(OfflinePlayer player) {
        return playerTags.get(player.getUniqueId());
    }

    public ConfigurationSection getTagConfig(String tag) {
        if (tag == null) return null;
        ConfigurationSection section = getConfig().getConfigurationSection("tags." + tag.toLowerCase());
        return section;
    }

    public String[] getConfiguredTags() {
        ConfigurationSection section = getConfig().getConfigurationSection("tags");
        return section == null ? new String[0] : section.getKeys(false).toArray(new String[0]);
    }

    public Component renderTag(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        if (section == null) return Component.empty();

        String display = section.getString("display", tag.toUpperCase());
        String colorString = section.getString("color", "#FFFFFF");
        TextColor color = TextColor.fromHexString(colorString);
        if (color == null) color = TextColor.color(255, 255, 255);

        boolean brackets = getConfig().getBoolean("chat.tag-brackets", true);
        String text = brackets ? "[" + display + "]" : display;
        return Component.text(text, color);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String tag = getTag(player);
        if (tag == null || getTagConfig(tag) == null) return;

        String separator = getConfig().getString("chat.separator", " » ");
        Component prefix = renderTag(tag)
                .append(Component.space())
                .append(Component.text(player.getName()))
                .append(Component.text(separator));

        event.renderer((source, sourceDisplayName, message, viewer) ->
                prefix.append(message));
    }

    public void reloadPlugin() {
        reloadConfig();
        loadData();
    }

    public String displayName(String tag) {
        ConfigurationSection section = getTagConfig(tag);
        return section == null ? tag : section.getString("display", tag.toUpperCase());
    }
}
