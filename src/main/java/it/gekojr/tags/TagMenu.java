package it.gekojr.tags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class TagMenu implements Listener {
    private static final String TITLE = "Your Nametags";
    private final TagsPlugin plugin;

    public TagMenu(TagsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        TagInventory holder = new TagInventory();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(TITLE));
        holder.setInventory(inventory);

        List<String> tags = plugin.getTags(player);
        String selected = plugin.getSelectedTag(player);
        int slot = 0;

        // Always available: option to disable the active nametag.
        ItemStack disableItem = new ItemStack(Material.BARRIER);
        ItemMeta disableMeta = disableItem.getItemMeta();
        boolean disabled = selected == null;
        disableMeta.displayName(Component.text("Disable Nametag", TextColor.color(255, 255, 255)));
        disableMeta.lore(List.of(
                Component.text(""),
                disabled
                        ? Component.text("Currently selected", TextColor.color(85, 255, 85))
                        : Component.text("Right-click to disable your nametag", TextColor.color(255, 255, 85))
        ));
        disableItem.setItemMeta(disableMeta);
        inventory.setItem(slot++, disableItem);

        for (String tag : tags) {
            if (slot >= 27) break;

            TagsPlugin.TagInfo info = plugin.getTagInfo(tag);
            ItemStack item = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();

            Component display = Component.text(info.display(), info.color());
            if (tag.equalsIgnoreCase(selected)) {
                display = display.append(Component.text("  ✓", TextColor.color(85, 255, 85)));
            }

            meta.displayName(display);
            meta.lore(List.of(
                    Component.text(""),
                    Component.text("Title: ", TextColor.color(170, 170, 170))
                            .append(Component.text(info.display(), info.color())),
                    Component.text(""),
                    tag.equalsIgnoreCase(selected)
                            ? Component.text("Currently selected", TextColor.color(85, 255, 85))
                            : Component.text("Right-click to select", TextColor.color(255, 255, 85))
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        if (tags.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text("No nametags available", TextColor.color(255, 85, 85)));
            meta.lore(List.of(Component.text("You don't own any nametags yet.", TextColor.color(170, 170, 170))));
            empty.setItemMeta(meta);
            inventory.setItem(13, empty);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof TagInventory)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!event.getClick().isRightClick()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) {
            plugin.disableTag(player);
            player.closeInventory();
            player.sendMessage(Component.text("Nametag disabled.", TextColor.color(255, 255, 255)));
            return;
        }

        if (clicked.getType() != Material.NAME_TAG) return;

        String selectedDisplay = PlainTextComponentSerializer.plainText()
                .serialize(clicked.getItemMeta().displayName());

        for (String tag : plugin.getTags(player)) {
            if (selectedDisplay.startsWith(plugin.displayName(tag))) {
                plugin.selectTag(player, tag);
                player.closeInventory();
                player.sendMessage(Component.text("Nametag selected: " + plugin.displayName(tag), TextColor.color(255, 255, 255)));
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TagInventory) {
            event.setCancelled(true);
        }
    }

    private static final class TagInventory implements InventoryHolder {
        private Inventory inventory;

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
