package org.gradle.platform.base.internal;

import org.gradle.platform.base.Platform;

public interface PlatformResolver<T extends Platform> {
    Class<T> getType();
    T resolve(PlatformRequirement platformRequirement);
}
