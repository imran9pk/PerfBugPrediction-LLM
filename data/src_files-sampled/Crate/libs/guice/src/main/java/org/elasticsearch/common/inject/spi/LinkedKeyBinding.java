package org.elasticsearch.common.inject.spi;

import org.elasticsearch.common.inject.Binding;
import org.elasticsearch.common.inject.Key;

public interface LinkedKeyBinding<T> extends Binding<T> {

    Key<? extends T> getLinkedKey();

}