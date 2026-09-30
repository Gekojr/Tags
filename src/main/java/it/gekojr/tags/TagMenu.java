package it.gekojr.tags;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    private static final int SINGLE_PAGE_SIZE = 27;
    private static final int SINGLE_PAGE_TAG_SLOTS = 26;
    private static final int MULTI_PAGE_SIZE = 54;
    private static final int MULTI_PAGE_TAG_SLOTS = 44;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final TagsPlugin plugin;

    public TagMenu(TagsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        List<String> tags = plugin.getTags(player);
        boolean multiPage = tags.size() > SINGLE_PAGE_TAG_SLOTS;
        int tagSlots = multiPage ? MULTI_PAGE_TAG_SLOTS : SINGLE_PAGE_TAG_SLOTS;
        int inventorySize = multiPage ? MULTI_PAGE_SIZE : SINGLE_PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) tagSlots));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        TagInventory holder = new TagInventory(page, multiPage);
        Inventory inventory = Bukkit.createInventory(holder, inventorySize, Component.text(TITLE));
        holder.setInventory(inventory);

        String selected = plugin.getSelectedTag(player);

        ItemStack disableItem = new ItemStack(Material.BARRIER);
        ItemMeta disableMeta = disableItem.getItemMeta();
        boolean disabled = selected == null;
        disableMeta.displayName(Component.text("Disable Nametag", TextColor.color(255, 255, 255)));
        disableMeta.lore(List.of(
                Component.text(""),
                disabled
                        ? Component.text("Currently selected", TextColor.color(85, 255, 85))
                        : Component.text("Left-click to disable your nametag", TextColor.color(255, 255, 85))
        ));
        disableItem.setItemMeta(disableMeta);
        inventory.setItem(0, disableItem);

        int start = page * tagSlots;
        int end = Math.min(start + tagSlots, tags.size());

        for (int i = start; i < end; i++) {
            String tag = tags.get(i);
            int slot = i - start + 1;

            TagsPlugin.TagInfo info = plugin.getTagInfo(tag);
            ItemStack item = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();

            TextColor menuColor = tag.equalsIgnoreCase("chad") ? TextColor.color(85, 85, 85) : info.color();
            Component display = Component.text(info.display(), menuColor).decorate(TextDecoration.BOLD);
            if (tag.equalsIgnoreCase(selected)) {
                display = display.append(Component.text("  ✓", TextColor.color(85, 255, 85)));
            }

            meta.displayName(display);
            meta.lore(List.of(
                    Component.text(""),
                    Component.text("Title: ", TextColor.color(170, 170, 170))
                            .append(Component.text(info.display(), menuColor).decorate(TextDecoration.BOLD)),
                    Component.text(""),
                    tag.equalsIgnoreCase(selected)
                            ? Component.text("Currently selected", TextColor.color(85, 255, 85))
                            : Component.text("Left-click to select", TextColor.color(255, 255, 85))
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }

        if (tags.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text("No nametags available", TextColor.color(255, 85, 85)));
            meta.lore(List.of(Component.text("You don't own any nametags yet.", TextColor.color(170, 170, 170))));
            empty.setItemMeta(meta);
            inventory.setItem(13, empty);
        }

        if (multiPage) {
            if (page > 0) {
                ItemStack previous = new ItemStack(Material.ARROW);
                ItemMeta meta = previous.getItemMeta();
                meta.displayName(Component.text("Previous Page", TextColor.color(255, 255, 255)));
                previous.setItemMeta(meta);
                inventory.setItem(PREVIOUS_SLOT, previous);
            }

            ItemStack pageItem = new ItemStack(Material.PAPER);
            ItemMeta pageMeta = pageItem.getItemMeta();
            pageMeta.displayName(Component.text("Page " + (page + 1) + " / " + totalPages, TextColor.color(255, 255, 255)));
            pageMeta.lore(List.of(Component.text("Your nametags", TextColor.color(170, 170, 170))));
            pageItem.setItemMeta(pageMeta);
            inventory.setItem(PAGE_SLOT, pageItem);

            if (page < totalPages - 1) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta meta = next.getItemMeta();
                meta.displayName(Component.text("Next Page", TextColor.color(255, 255, 255)));
                next.setItemMeta(meta);
                inventory.setItem(NEXT_SLOT, next);
            }
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof TagInventory holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!event.getClick().isLeftClick()) return;

        int slot = event.getRawSlot();

        if (holder.multiPage && slot == PREVIOUS_SLOT && holder.page > 0) {
            open(player, holder.page - 1);
            return;
        }

        List<String> tags = plugin.getTags(player);
        int tagSlots = holder.multiPage ? MULTI_PAGE_TAG_SLOTS : SINGLE_PAGE_TAG_SLOTS;
        int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) tagSlots));

        if (holder.multiPage && slot == NEXT_SLOT && holder.page < totalPages - 1) {
            open(player, holder.page + 1);
            return;
        }

        if (slot == 0) {
            plugin.disableTag(player);
            player.closeInventory();
            player.sendMessage(Component.text("Nametag disabled.", TextColor.color(255, 255, 255)));
            return;
        }

        if (holder.multiPage && slot == PAGE_SLOT) return;
        if (!holder.multiPage && slot >= SINGLE_PAGE_SIZE) return;

        int tagIndex;
        if (holder.multiPage) {
            if (slot < 1 || slot > MULTI_PAGE_TAG_SLOTS) return;
            tagIndex = holder.page * MULTI_PAGE_TAG_SLOTS + (slot - 1);
        } else {
            if (slot < 1 || slot >= SINGLE_PAGE_SIZE) return;
            tagIndex = slot - 1;
        }

        if (tagIndex < 0 || tagIndex >= tags.size()) return;

        String tag = tags.get(tagIndex);
        plugin.selectTag(player, tag);
        player.closeInventory();
        player.sendMessage(Component.text("Nametag selected: " + plugin.displayName(tag), TextColor.color(255, 255, 255)));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TagInventory) {
            event.setCancelled(true);
        }
    }

    private static final class TagInventory implements InventoryHolder {
        private final int page;
        private final boolean multiPage;
        private Inventory inventory;

        TagInventory(int page, boolean multiPage) {
            this.page = page;
            this.multiPage = multiPage;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
