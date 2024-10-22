package org.elasticsearch.common.inject.binder;

public interface ConstantBindingBuilder {

    void to(String value);

    void to(int value);

    void to(long value);

    void to(boolean value);

    void to(double value);

    void to(float value);

    void to(short value);

    void to(char value);

    void to(Class<?> value);

    <E extends Enum<E>> void to(E value);
}