package it.gekojr.tags.fabric;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
final class Access {
    static boolean allowed(CommandSourceStack source,String namespace,String name,boolean everyone) {
        boolean fallback=everyone || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return ((PermissionContextOwner)(Object)source).checkPermission(Identifier.fromNamespaceAndPath(namespace,name),fallback);
    }
}
