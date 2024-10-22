package org.gradle.internal.hash;

public interface PrimitiveHasher {
    void putBytes(byte[] bytes);

    void putBytes(byte[] bytes, int off, int len);

    void putByte(byte value);

    void putInt(int value);

    void putLong(long value);

    void putDouble(double value);

    void putBoolean(boolean value);

    void putString(CharSequence value);

    void putHash(HashCode hashCode);

    HashCode hash();
}
