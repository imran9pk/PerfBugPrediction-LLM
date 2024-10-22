package com.codename1.properties;

public class ByteProperty<K> extends NumericProperty<Byte, K>{

    public ByteProperty(String name) {
        super(name, Byte.class);
    }

    public ByteProperty(String name, Byte value) {
        super(name, Byte.class, value);
    }    
    
    public byte getByte() {
        return get().byteValue();
    }
}
