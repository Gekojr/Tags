package it.gekojr.tags.fabric.mixin;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.mojang.brigadier.tree.CommandNode;
@Mixin(value=CommandNode.class,remap=false)
public interface CommandNodeAccess {
    @Accessor("children") Map<String,?> tags$children();
    @Accessor("literals") Map<String,?> tags$literals();
    @Accessor("arguments") Map<String,?> tags$arguments();
}
