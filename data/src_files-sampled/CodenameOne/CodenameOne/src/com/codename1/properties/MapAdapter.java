package com.codename1.properties;

import java.util.HashMap;
import java.util.Map;

public abstract class MapAdapter {
    private final Class type;
    private final static Map<String, MapAdapter> lookup = new HashMap<String, MapAdapter>();
    
    protected MapAdapter(Class type) {
        this.type = type;
        lookup.put(type.getName(), this);
    }
    
    static MapAdapter checkInstance(PropertyBase b) {
        if(b.getGenericType() != null) {
            MapAdapter a = lookup.get(b.getGenericType().getName());
            if(a != null && a.useAdapterFor(b)) {
                return a;
            }
        }
        return null;
    }
    
    public boolean useAdapterFor(PropertyBase b) {
        return b.getGenericType() == type;
    }
    
    public void placeInMap(PropertyBase b, Map m) {
        m.put(b.getName(), b.get());
    }
    
    public void setFromMap(PropertyBase b, Map m){
        b.setImpl(m.get(b.getName()));
    }
}
