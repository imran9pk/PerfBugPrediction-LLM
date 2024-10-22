package org.elasticsearch.common.inject;

import org.elasticsearch.common.inject.spi.BindingScopingVisitor;
import org.elasticsearch.common.inject.spi.BindingTargetVisitor;
import org.elasticsearch.common.inject.spi.Element;

public interface Binding<T> extends Element {

    Key<T> getKey();

    Provider<T> getProvider();

    <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor);

    <V> V acceptScopingVisitor(BindingScopingVisitor<V> visitor);
}
