package it.gekojr.tags.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

final class TagMenu {
    private final TagsMod mod;
    TagMenu(TagsMod mod) { this.mod=mod; }
    void open(ServerPlayer player,int requested) {
        var user=mod.new User(player); List<String> tags=mod.getTags(user); boolean multi=tags.size()>26;
        int pageSize=multi?44:26, rows=multi?6:3, pages=Math.max(1,(tags.size()+pageSize-1)/pageSize);
        int page=Math.clamp(requested,0,pages-1); var container=new SimpleContainer(rows*9);
        container.setItem(0,item(Items.BARRIER,Component.literal("Disable Nametag"),"Click to disable"));
        for(int i=page*pageSize;i<Math.min(tags.size(),(page+1)*pageSize);i++) {
            String tag=tags.get(i); Component title=mod.renderTag(tag);
            if(tag.equals(mod.getSelectedTag(user)))title=title.copy().append(" ✓");
            container.setItem(i-page*pageSize+1,item(Items.NAME_TAG,title,"Click to select"));
        }
        if(tags.isEmpty())container.setItem(13,item(Items.BARRIER,Component.literal("No nametags available"),"You don't own any nametags yet."));
        if(multi) {
            if(page>0)container.setItem(45,item(Items.ARROW,Component.literal("Previous Page"),""));
            container.setItem(49,item(Items.PAPER,Component.literal("Page "+(page+1)+" / "+pages),""));
            if(page+1<pages)container.setItem(53,item(Items.ARROW,Component.literal("Next Page"),""));
        }
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new ChestMenu(rows==6?MenuType.GENERIC_9x6:MenuType.GENERIC_9x3,id,inventory,container,rows) {
            @Override public void clicked(int slot,int button,ContainerInput input,Player actor) {
                if(input!=ContainerInput.PICKUP || button!=0 || actor!=player)return;
                if(multi && slot==45 && page>0) { mod.defer(() -> open(player,page-1)); return; }
                if(multi && slot==53 && page+1<pages) { mod.defer(() -> open(player,page+1)); return; }
                if(mod.isTagForced(user)) { player.sendSystemMessage(Component.literal("Your nametag is temporarily forced.")); return; }
                if(slot==0) { mod.disableTag(user); mod.defer(player::closeContainer); return; }
                int index=page*pageSize+slot-1;
                if(slot<1 || slot>pageSize || index>=tags.size())return;
                mod.selectTag(user,tags.get(index)); mod.defer(player::closeContainer);
            }
            @Override public ItemStack quickMoveStack(Player p,int slot) { return ItemStack.EMPTY; }
            @Override public boolean canDragTo(Slot slot) { return false; }
            @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) { return false; }
        },Component.literal("Your Nametags")));
    }
    private ItemStack item(Item item,Component name,String hint) { ItemStack stack=new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME,name); stack.set(DataComponents.LORE,new ItemLore(List.of(Component.literal(hint)))); return stack; }
}
