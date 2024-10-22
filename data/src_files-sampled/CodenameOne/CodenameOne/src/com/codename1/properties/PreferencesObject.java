package com.codename1.properties;

import com.codename1.io.Preferences;

public class PreferencesObject {
    private PropertyBusinessObject bo;
    private String prefix;
    private boolean bound;
    
    private PreferencesObject() {}
    
    public static PreferencesObject create(PropertyBusinessObject bo) {
        PreferencesObject po = new PreferencesObject();
        po.bo = bo;
        po.prefix = bo.getPropertyIndex().getName() + ".";
        return po;
    }
    
    public PreferencesObject bind() {
        for(PropertyBase pb : bo.getPropertyIndex()) {
            String name = (String)pb.getClientProperty("cn1-po-name");
            if(name == null) {
                name = pb.getName();
            }
            Class type = pb.getGenericType();
            String n = prefix + name;
            if(type == String.class || type == null) {
                ((Property)pb).set(Preferences.getAndSet(n, (String)((Property)pb).get()));
                bindChangeListener((Property)pb, n, type);
                continue;
            }
            Object obj = ((Property)pb).get();
            if(type == Boolean.class) {
                ((Property)pb).set(Preferences.getAndSet(n, obj == null ? false : (Boolean)obj));
                bindChangeListener((Property)pb, n, type);
                continue;
            }
            if(type == Double.class) {
                ((Property)pb).set(Preferences.getAndSet(n, obj == null ? 0.0 : (Double)obj));
                bindChangeListener((Property)pb, n, type);
                continue;
            }
            if(type == Float.class) {
                ((Property)pb).set(Preferences.getAndSet(n, obj == null ? 0.0f : (Float)obj));
                bindChangeListener((Property)pb, n, type);
                continue;
            }
            if(type == Integer.class) {
                ((Property)pb).set(Preferences.getAndSet(n, obj == null ? 0 : (Integer)obj));
                bindChangeListener((Property)pb, n, type);
                continue;
            }
            if(type == Long.class) {
                ((Property)pb).set(Preferences.getAndSet(n, obj == null ? (long)0 : (Long)obj));
                bindChangeListener((Property)pb, n, type);
                continue;
            }
            throw new IllegalStateException("Unsupported property type in preferences: " + type.getName());
        }
        bound = true;
        return this;
    }
    
    private void bindChangeListener(final Property pb, final String n, final Class type) {
        pb.addChangeListener(new PropertyChangeListener() {
            public void propertyChanged(PropertyBase p) {
                if(type == String.class || type == null) {
                    Preferences.set(n, (String)((Property)pb).get());
                    return;
                }
                if(type == Boolean.class) {
                    Preferences.set(n, (Boolean)((Property)pb).get());
                    return;
                }
                if(type == Double.class) {
                    Preferences.set(n, (Double)((Property)pb).get());
                    return;
                }
                if(type == Float.class) {
                    Preferences.set(n, (Float)((Property)pb).get());
                    return;
                }
                if(type == Integer.class) {
                    Preferences.set(n, (Integer)((Property)pb).get());
                    return;
                }
                if(type == Long.class) {
                    Preferences.set(n, (Long)((Property)pb).get());
                }
            }
        });
    }
    
    private void checkBind() {
        if(bound) {
            throw new IllegalStateException("Method can't be invoked after binding");
        }
    }
    
    public PreferencesObject setPrefix(String prefix) {
        checkBind();
        
        this.prefix = prefix.intern();
        return this;
    }

    public PreferencesObject setName(PropertyBase pb, String name) {
        checkBind();
        
        pb.putClientProperty("cn1-po-name", name);
        return this;
    }
}
