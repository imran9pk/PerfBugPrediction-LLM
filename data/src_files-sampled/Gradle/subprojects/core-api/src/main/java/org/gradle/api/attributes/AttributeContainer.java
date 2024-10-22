package org.gradle.api.attributes;

import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.Set;

@HasInternalProtocol
@UsedByScanPlugin
public interface AttributeContainer extends HasAttributes {

    Set<Attribute<?>> keySet();

    <T> AttributeContainer attribute(Attribute<T> key, T value);

    @Nullable
    <T> T getAttribute(Attribute<T> key);

    boolean isEmpty();

    boolean contains(Attribute<?> key);

}
