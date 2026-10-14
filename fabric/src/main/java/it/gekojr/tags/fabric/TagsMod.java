package it.gekojr.tags.fabric;
import java.io.File;
import java.util.*;
public final class TagsMod implements net.fabricmc.api.ModInitializer {
    private final Map<UUID, LinkedHashSet<String>> playerTags = new HashMap<>();
    private final Map<UUID, String> selectedTags = new HashMap<>();
    private final Map<UUID, String> forcedTags = new HashMap<>();
    private final Map<UUID, Long> forcedTagExpiry = new HashMap<>();
    private final Map<UUID, String> forcedTagPrevious = new HashMap<>();
    private final Map<UUID, Long> playtimeMillis = new HashMap<>();
    private final Map<UUID, Long> sessionStartMillis = new HashMap<>();
    private File dataFile;
    private Config data;

    private net.minecraft.server.MinecraftServer server;
    private Config config;
    private java.nio.file.Path folder;
    private int ticks;
    @Override public void onInitialize() {
        folder = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("Tags");
        config = Config.load(folder.resolve("config.yml"), "/tags-default.yml");
        ensureConfiguredTags(); loadData(); removeDeletedTag("schiavo");
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> { server=s; });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(s -> saveData());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,s) -> startPlaytimeSession(new User(handler.player)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,s) -> endPlaytimeSession(new User(handler.player)));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++ticks % 20 == 0) expireForcedTags();
            if (ticks % 1200 == 0) { updatePlaytimeTags(); saveOnlinePlaytime(); }
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) -> {
            var vanilla=dispatcher.getRoot().getChild("tag");
            if(vanilla!=null) {
                dispatcher.register(net.minecraft.commands.Commands.literal("minecraft:tag").requires(vanilla.getRequirement()).redirect(vanilla));
                var access=(it.gekojr.tags.fabric.mixin.CommandNodeAccess)(Object)dispatcher.getRoot();
                access.tags$children().remove("tag"); access.tags$literals().remove("tag"); access.tags$arguments().remove("tag");
            }
            var tag=net.minecraft.commands.Commands.literal("tag").executes(ctx -> {
                var player=ctx.getSource().getPlayerOrException(); new TagMenu(this).open(player,0); return 1;
            }); var tagNode=dispatcher.register(tag); dispatcher.register(net.minecraft.commands.Commands.literal("tags").executes(tagNode.getCommand()).redirect(tagNode));
            var cmd=net.minecraft.commands.Commands.literal("nametag").requires(src -> Access.allowed(src,"tags","admin",false));
            cmd.executes(ctx -> { executeCommand(new Sender(ctx.getSource()),new String[0]); return 1; });
            cmd.then(net.minecraft.commands.Commands.argument("arguments",com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .suggests((ctx,builder) -> { String[] args=builder.getRemaining().split("\\s+",-1); int offset=builder.getInput().length()-args[args.length-1].length(); var out=builder.createOffset(offset); for(String v:new NametagCommand(this).suggest(args))out.suggest(v); return out.buildFuture(); })
                .executes(ctx -> { executeCommand(new Sender(ctx.getSource()),com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"arguments").split("\\s+")); return 1; }));
            dispatcher.register(cmd);
        });
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,player,bound) -> {
            var user=new User(player); String tag=getSelectedTag(user); if(tag==null || getTagConfig(tag)==null)return true;
            var info=getTagInfo(tag);
            var prefix=renderTag(tag).append(net.minecraft.network.chat.Component.literal(" "+user.getName()).withStyle(style -> style.withColor(info.color())))
                .append(net.minecraft.network.chat.Component.literal(getConfig().getString("chat.separator"," » ")).withStyle(net.minecraft.ChatFormatting.WHITE));
            var text=prefix.copy().append(net.minecraft.network.chat.Component.literal(message.signedContent()).withStyle(net.minecraft.ChatFormatting.WHITE));
            server.getPlayerList().broadcastSystemMessage(text,recipient -> {
                if(recipient.getChatVisibility()!=net.minecraft.world.entity.player.ChatVisiblity.FULL)return null;
                String body=player.shouldFilterMessageTo(recipient)?message.filterMask().apply(message.signedContent()):message.signedContent();
                return body==null?null:prefix.copy().append(net.minecraft.network.chat.Component.literal(body).withStyle(net.minecraft.ChatFormatting.WHITE));
            },false); return false;
        });
    }
    private boolean executeCommand(Sender sender,String[] args) {
        try { return new NametagCommand(this).execute(sender,args); }
        catch(IllegalArgumentException e) { sender.sendMessage(e.getMessage()); return false; }
    }
    void defer(Runnable action) { server.execute(action); }
    Config getConfig() { return config; }
    void saveConfig() { config.save(folder.resolve("config.yml")); }
    void reloadConfig() { config=Config.load(folder.resolve("config.yml"),"/tags-default.yml"); }
    java.io.File getDataFolder() { return folder.toFile(); }
    java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger("Tags"); }
    java.util.List<User> online() { return server.getPlayerList().getPlayers().stream().map(User::new).toList(); }
    User resolve(String name) {
        var player=server.getPlayerList().getPlayerByName(name); if(player!=null)return new User(player);
        var profile=server.services().nameToIdCache().get(name); if(profile.isPresent()) return new User(profile.get().id(),name);
        throw new IllegalArgumentException("Unknown player: "+name+". The player must join the server first.");
    }
    final class User {
        final UUID uuid; final String name;
        User(net.minecraft.server.level.ServerPlayer p) { this(p.getUUID(),p.getName().getString()); }
        User(UUID uuid,String name) { this.uuid=uuid; this.name=name; }
        UUID getUniqueId() { return uuid; } String getName() { return name; }
        boolean isOnline() { return server!=null && server.getPlayerList().getPlayer(uuid)!=null; }
        void sendMessage(net.minecraft.network.chat.Component text) { var p=server.getPlayerList().getPlayer(uuid); if(p!=null)p.sendSystemMessage(text); }
    }
    record Sender(net.minecraft.commands.CommandSourceStack source) {
        boolean hasPermission(String node) { return Access.allowed(source,"tags","admin",false); }
        boolean isOp() { return hasPermission("tags.admin"); }
        void sendMessage(String text) { source.sendSystemMessage(Text.legacy(text)); }
    }

    private void removeDeletedTag(String tag) {
        String normalized = tag.toLowerCase();
        boolean changed = false;
        for (LinkedHashSet<String> tags : playerTags.values()) {
            if (tags.remove(normalized)) changed = true;
        }
        selectedTags.entrySet().removeIf(entry -> normalized.equals(entry.getValue()));
        if (changed) saveData();
    }

    private void ensureConfiguredTags() {
        // Add newly introduced tags to an existing server config without overwriting custom settings.
        Map<String, String[]> defaults = new java.util.LinkedHashMap<>();
        defaults.put("helper", new String[]{"HELPER", "70", "#32CD32"});
        defaults.put("discord-mod", new String[]{"DISCORD MOD", "60", "#00008B"});
        defaults.put("chad", new String[]{"CHAD", "55", "#555555"});
        defaults.put("social-media-manager", new String[]{"SOCIAL MEDIA MANAGER", "65", "#32145F"});
                defaults.put("new", new String[]{"NEW", "16", "#FFFFFF"});
        defaults.put("member", new String[]{"MEMBER", "17", "#55FF55"});
        defaults.put("veteran", new String[]{"VETERAN", "18", "#55FFFF"});
        defaults.put("elite", new String[]{"ELITE", "19", "#AA00AA"});
        defaults.put("legend", new String[]{"LEGEND", "20", "#FFAA00"});

        boolean changed = false;

        // Update these colors on existing server configs too.
        if (getConfig().isConfigurationSection("tags.social-media-manager")
                && !"#32145F".equalsIgnoreCase(getConfig().getString("tags.social-media-manager.color", ""))) {
            getConfig().set("tags.social-media-manager.color", "#32145F");
            changed = true;
        }
        if (getConfig().isConfigurationSection("tags.new")
                && !"#FFFFFF".equalsIgnoreCase(getConfig().getString("tags.new.color", ""))) {
            getConfig().set("tags.new.color", "#FFFFFF");
            changed = true;
        }

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

    public void onDisable() {
        saveData();
    }

    private void loadData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }

        dataFile = new File(getDataFolder(), "data.yml");
        data = Config.load(dataFile.toPath(), null);

        Config players = data.getConfigurationSection("players");
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

        data.save(dataFile.toPath());
    }

    public boolean addTag(User player, String tag) {
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

    public boolean removeTag(User player, String tag) {
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

    public void removeAllTags(User player) {
        playerTags.remove(player.getUniqueId());
        selectedTags.remove(player.getUniqueId());
        saveData();
    }

    public boolean hasTag(User player, String tag) {
        return playerTags.getOrDefault(player.getUniqueId(), new LinkedHashSet<>())
                .contains(tag.toLowerCase());
    }

    public List<String> getTags(User player) {
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
        Config section = getTagConfig(tag);
        return section == null ? 0 : section.getInt("priority", 0);
    }

    public String getSelectedTag(User player) {
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

    public void resetPlaytime(User player) {
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
        for (User player : online()) {
            UUID uuid = player.getUniqueId();
            playtimeMillis.put(uuid, 0L);
            sessionStartMillis.put(uuid, System.currentTimeMillis());
        }

        for (UUID uuid : new LinkedHashSet<>(playtimeMillis.keySet())) {
            if (!new User(uuid, "").isOnline()) {
                playtimeMillis.remove(uuid);
                sessionStartMillis.remove(uuid);
            }
        }

        saveData();
    }

    public String getPlaytimeTag(User player) {
        if (!getConfig().getBoolean("playtime.enabled", false)) return null;
        Object value=getConfig().get("playtime.tiers");
        java.util.List<Config> tiers=new java.util.ArrayList<>();
        if(value instanceof java.util.List<?> list) {
            for(Object entry:list) if(entry instanceof java.util.Map<?,?> map)tiers.add(new Config((java.util.Map)map));
        } 
        if(value instanceof java.util.Map<?,?> map)for(Object entry:map.values())if(entry instanceof java.util.Map<?,?> row)tiers.add(new Config((java.util.Map)row));
        double hours=getCurrentPlaytimeMillis(player.getUniqueId())/3_600_000.0;
        String bestTag=null; double bestHours=-1;
        for(Config tier:tiers) { double required=tier.getDouble("hours",-1); String tag=tier.getString("tag");
            if(tag!=null && required>=0 && required<=hours && required>=bestHours && getTagConfig(tag)!=null) { bestHours=required; bestTag=tag.toLowerCase(); }
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
        data.save(dataFile.toPath());
    }

    private void startPlaytimeSession(User player) {
        UUID uuid = player.getUniqueId();
        // Playtime is tracked for the whole server, regardless of world.
        // Existing time is loaded from data.yml and is never reset on world changes.
        sessionStartMillis.put(uuid, System.currentTimeMillis());
        ensurePlaytimeTag(player);
    }

    private void ensurePlaytimeTag(User player) {
        String playtimeTag = getPlaytimeTag(player);
        if (playtimeTag == null) return;

        LinkedHashSet<String> tags = playerTags.computeIfAbsent(
                player.getUniqueId(), ignored -> new LinkedHashSet<>()
        );

        if (tags.add(playtimeTag)) {
            saveData();

            player.sendMessage(net.minecraft.network.chat.Component.literal("Nametag unlocked: ").append(renderTag(playtimeTag)).append("!"));
        }
    }

    private void updatePlaytimeTags() {
        for (User player : online()) {
            ensurePlaytimeTag(player);
        }
    }

    private void endPlaytimeSession(User player) {
        UUID uuid = player.getUniqueId();
        Long start = sessionStartMillis.remove(uuid);
        if (start != null) playtimeMillis.merge(uuid, Math.max(0L, System.currentTimeMillis() - start), Long::sum);
        saveDataWithoutPlaytimeRecursion();
    }

    public void selectTag(User player, String tag) {
        if (isTagForced(player)) return;
        if (!hasTag(player, tag)) return;
        selectedTags.put(player.getUniqueId(), tag.toLowerCase());
        saveData();
    }

    public void disableTag(User player) {
        if (isTagForced(player)) return;
        selectedTags.remove(player.getUniqueId());
        saveData();
    }

    public boolean isTagForced(User player) {
        UUID uuid = player.getUniqueId();
        Long expiry = forcedTagExpiry.get(uuid);
        if (!forcedTags.containsKey(uuid)) return false;
        if (expiry != null && expiry > System.currentTimeMillis()) return true;

        expireForcedTag(uuid);
        return false;
    }

    public boolean forceTag(User player, String tag, long durationMillis) {
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

    public boolean unforceTag(User player) {
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

    public long getForcedTagRemainingMillis(User player) {
        if (!isTagForced(player)) return 0L;
        return Math.max(0L, forcedTagExpiry.get(player.getUniqueId()) - System.currentTimeMillis());
    }

    public Config getTagConfig(String tag) {
        if (tag == null) return null;
        return getConfig().getConfigurationSection("tags." + tag.toLowerCase());
    }

    public String[] getConfiguredTags() {
        Config section = getConfig().getConfigurationSection("tags");
        return section == null ? new String[0] : section.getKeys(false).toArray(new String[0]);
    }

    public TagInfo getTagInfo(String tag) {
        Config section=getTagConfig(tag); if(section==null)return new TagInfo(tag,0xFFFFFF);
        int color=0xFFFFFF; try { color=Integer.parseInt(section.getString("color","#FFFFFF").replace("#",""),16); } catch(NumberFormatException ignored) {}
        if("chad".equalsIgnoreCase(tag))color=0x555555;
        return new TagInfo(section.getString("display",tag.toUpperCase()),color);
    }
    public net.minecraft.network.chat.MutableComponent renderTag(String tag) {
        var info=getTagInfo(tag); String title=getConfig().getBoolean("chat.tag-brackets",true)?"["+info.display()+"]":info.display();
        boolean bold=java.util.Set.of("owner","co-owner","admin","helper","social-media-manager","discord-mod","legend","chad").contains(tag);
        return net.minecraft.network.chat.Component.literal(title).withStyle(style -> style.withColor(info.color()).withBold(bold));
    }

    public void reloadPlugin() {
        saveData();
        reloadConfig();
        playerTags.clear();
        selectedTags.clear();
        forcedTags.clear();
        forcedTagExpiry.clear();
        forcedTagPrevious.clear();
        playtimeMillis.clear();
        sessionStartMillis.clear();
        loadData();
        for (User player : online()) startPlaytimeSession(player);
    }

    public String displayName(String tag) {
        Config section = getTagConfig(tag);
        return section == null ? tag : section.getString("display", tag.toUpperCase());
    }


    public record TagInfo(String display, int color) {}
}
