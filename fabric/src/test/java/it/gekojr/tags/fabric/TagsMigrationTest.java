package it.gekojr.tags.fabric;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;

class TagsMigrationTest {
    @TempDir Path folder;
    TagsMod mod;
    final UUID id=UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    @BeforeEach void setup() throws Exception {
        mod=new TagsMod(); set("folder",folder); set("config",Config.load(folder.resolve("config.yml"),"/tags-default.yml"));
    }
    void set(String name,Object value) throws Exception { var field=TagsMod.class.getDeclaredField(name); field.setAccessible(true); field.set(mod,value); }
    void load() throws Exception { var method=TagsMod.class.getDeclaredMethod("loadData"); method.setAccessible(true); method.invoke(mod); }
    TagsMod.User user() { return mod.new User(id,"Example"); }
    @Test void preservesPaperOwnershipSelectionAndPlaytimeAcrossSave() throws Exception {
        Files.writeString(folder.resolve("data.yml"),"players:\n  "+id+":\n    tags: [owner, donator]\n    selected: donator\n    playtime: 36000000\n"); load();
        assertEquals(List.of("owner","donator"),mod.getTags(user()));
        assertEquals("member",mod.getPlaytimeTag(user()));
        mod.saveData(); var saved=Config.load(folder.resolve("data.yml"),null);
        assertEquals("donator",saved.getString("players."+id+".selected"));
        assertEquals(36_000_000,saved.getLong("players."+id+".playtime"));
        assertEquals(List.of("owner","donator"),saved.getStringList("players."+id+".tags"));
    }
    @Test void forcedTagRestoresPreviousOwnedSelection() throws Exception {
        load(); mod.getConfig().set("playtime.enabled",false); mod.addTag(user(),"donator");
        assertTrue(mod.forceTag(user(),"admin",60_000)); assertEquals("admin",mod.getSelectedTag(user()));
        assertTrue(mod.unforceTag(user())); assertEquals("donator",mod.getSelectedTag(user()));
    }
    @Test void playtimeResetDoesNotRemoveOwnedTags() throws Exception {
        load(); mod.addTag(user(),"owner"); mod.resetPlaytime(user()); assertTrue(mod.hasTag(user(),"owner"));
    }
    @Test void readsLegacySingleTagData() throws Exception {
        Files.writeString(folder.resolve("data.yml"),"players:\n  "+id+": owner\n");load();assertTrue(mod.hasTag(user(),"owner"));
    }
    @Test void malformedYamlDoesNotOverwriteExistingFile() throws Exception {
        Path file=folder.resolve("bad.yml");String invalid="players: [\n";Files.writeString(file,invalid);
        assertThrows(RuntimeException.class,() -> Config.load(file,null));assertEquals(invalid,Files.readString(file));
    }
}
