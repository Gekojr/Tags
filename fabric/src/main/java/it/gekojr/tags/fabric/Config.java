package it.gekojr.tags.fabric;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.LoaderOptions;
import java.nio.file.*;
import java.io.*;
import java.util.*;

@SuppressWarnings("unchecked")
final class Config {
    private final Map<String,Object> values;
    Config(Map<String,Object> values) { this.values=values; }
    static Config load(Path path,String resource) {
        try {
            if(!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                if(resource!=null)try(var in=Config.class.getResourceAsStream(resource)) {
                    if(in==null)throw new IOException("Missing resource "+resource); Files.copy(in,path);
                }
                else return new Config(new LinkedHashMap<>());
            }
            try(var reader=Files.newBufferedReader(path)) {
                Object loaded=new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
                if(loaded==null)return new Config(new LinkedHashMap<>());
                if(!(loaded instanceof Map))throw new IOException("Expected YAML mapping: "+path);
                return new Config((Map<String,Object>)loaded);
            }
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    Object get(String path) {
        Object current=values; for(String part:path.split("\\.")) { if(!(current instanceof Map<?,?> map))return null; current=map.get(part); } return current;
    }
    String getString(String path) { Object v=get(path); return v instanceof String s?s:null; }
    String getString(String path,String fallback) { String v=getString(path); return v==null?fallback:v; }
    boolean getBoolean(String path,boolean fallback) { Object v=get(path); return v instanceof Boolean b?b:fallback; }
    long getLong(String path,long fallback) { Object v=get(path); return v instanceof Number n?n.longValue():fallback; }
    long getLong(String path) { return getLong(path,0); }
    int getInt(String path,int fallback) { return (int)getLong(path,fallback); }
    double getDouble(String path,double fallback) { Object v=get(path); return v instanceof Number n?n.doubleValue():fallback; }
    List<String> getStringList(String path) { Object v=get(path); return v instanceof List<?> list?list.stream().filter(String.class::isInstance).map(String.class::cast).toList():List.of(); }
    Config getConfigurationSection(String path) { Object v=get(path); return v instanceof Map<?,?> map?new Config((Map)map):null; }
    boolean isConfigurationSection(String path) { return get(path) instanceof Map; }
    Set<String> getKeys(boolean deep) { return values.keySet(); }
    void set(String path,Object value) {
        String[] parts=path.split("\\."); Map<String,Object> map=values;
        for(int i=0;i<parts.length-1;i++) { Object v=map.get(parts[i]); if(!(v instanceof Map)) { v=new LinkedHashMap<String,Object>(); map.put(parts[i],v); } map=(Map<String,Object>)v; }
        if(value==null)map.remove(parts[parts.length-1]); else map.put(parts[parts.length-1],value instanceof Config c?c.values:value);
    }
    void save(Path path) {
        try { Files.createDirectories(path.getParent()); Path tmp=Files.createTempFile(path.getParent(),path.getFileName().toString(),".tmp");
            try { Files.writeString(tmp,new Yaml().dump(values)); try { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                catch(AtomicMoveNotSupportedException e) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
}
