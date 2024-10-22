package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasConfigurableAttributes;

import javax.annotation.Nullable;

public interface DependencyConstraint extends ModuleVersionSelector, HasConfigurableAttributes<DependencyConstraint> {

    void version(Action<? super MutableVersionConstraint> configureAction);

    @Nullable
    String getReason();

    void because(@Nullable String reason);

    @Override
    AttributeContainer getAttributes();

    @Override
    DependencyConstraint attributes(Action<? super AttributeContainer> configureAction);

    VersionConstraint getVersionConstraint();
}
