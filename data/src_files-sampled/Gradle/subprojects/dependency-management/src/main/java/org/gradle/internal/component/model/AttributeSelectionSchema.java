package org.gradle.internal.component.model;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Set;

public interface AttributeSelectionSchema {
    boolean hasAttribute(Attribute<?> attribute);

    Set<Object> disambiguate(Attribute<?> attribute, @Nullable Object requested, Set<Object> candidates);

    boolean matchValue(Attribute<?> attribute, Object requested, Object candidate);

    @Nullable
    Attribute<?> getAttribute(String name);

    Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidates, ImmutableAttributes requested);
}
