package com.codename1.io;

import com.codename1.util.StringUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Storage {
    private final CacheMap cache = new CacheMap();
    private static Storage INSTANCE;
    private boolean normalizeNames = true;

    public void setHardCacheSize(int size) {
        cache.setCacheSize(size);
    }

    private String fixFileName(String name) {
        if(normalizeNames) {
            name = StringUtil.replaceAll(name, "/", "_");
            name = StringUtil.replaceAll(name, "\\", "_");
            name = StringUtil.replaceAll(name, "%", "_");
            name = StringUtil.replaceAll(name, "?", "_");
            name = StringUtil.replaceAll(name, "*", "_");
            name = StringUtil.replaceAll(name, ":", "_");
            name = StringUtil.replaceAll(name, "=", "_");            
        }
        return name;
    }
    
    private static void init(Object data) {
        Util.getImplementation().setStorageData(data);
        if(INSTANCE == null) {
            INSTANCE = new Storage();
        }
    }

    public static boolean isInitialized(){
        return INSTANCE != null;
    }
    
    public static Storage getInstance() {
        if(INSTANCE == null) {
            init("cn1");
        }
        return INSTANCE;
    }
    
    public void clearCache() {
        cache.clearAllCache();
    }
    
    public void flushStorageCache() {
        Util.getImplementation().flushStorageCache();
    }

    public void deleteStorageFile(String name) {
        name = fixFileName(name);
        Util.getImplementation().deleteStorageFile(name);
        cache.delete(name);
    }

    public void clearStorage() {
        Util.getImplementation().clearStorage();
        cache.clearAllCache();
    }

    public OutputStream createOutputStream(String name) throws IOException {
        name = fixFileName(name);
        return Util.getImplementation().createStorageOutputStream(name);
    }

    public InputStream createInputStream(String name) throws IOException {
        if (!exists(name)) {
            throw new IOException("Storage key "+name+" does not exist");
        }
        name = fixFileName(name);
        return Util.getImplementation().createStorageInputStream(name);
    }

    public boolean exists(String name) {
        name = fixFileName(name);
        return Util.getImplementation().storageFileExists(name);
    }

    public String[] listEntries() {
        return Util.getImplementation().listStorageEntries();
    }

    public int entrySize(String name) {
        name = fixFileName(name);
        return Util.getImplementation().getStorageEntrySize(name);
    }
    
    public boolean writeObject(String name, Object o) {
        name = fixFileName(name);
        cache.put(name, o);
        DataOutputStream d = null;
        try {
            d = new DataOutputStream(createOutputStream(name));
            Util.writeObject(o, d);
            d.close();
            return true;
        } catch(Exception err) {
            Log.e(err);
            if(Log.isCrashBound()) {
                Log.sendLog();
            }
            Util.getImplementation().deleteStorageFile(name);
            Util.getImplementation().cleanup(d);
            return false;
        }
    }

    public Object readObject(String name) {
        name = fixFileName(name);
        Object o = cache.get(name);
        if(o != null) {
            return o;
        }
        DataInputStream d = null;
        try {
            if(!exists(name)) {
                return null;
            }
            d = new DataInputStream(createInputStream(name));
            o = Util.readObject(d);
            d.close();
            cache.put(name, o);
            return o;
        } catch(Throwable err) {
            Log.e(err);
            if(Log.isCrashBound()) {
                Log.sendLog();
            }
            Util.getImplementation().cleanup(d);
            return null;
        }
    }

    public boolean isNormalizeNames() {
        return normalizeNames;
    }

    public void setNormalizeNames(boolean normalizeNames) {
        this.normalizeNames = normalizeNames;
    }
    
    public static void setStorageInstance(Storage s) {
        INSTANCE = s;
    }
}
