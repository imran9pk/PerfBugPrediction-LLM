package org.gradle.platform.base.internal.toolchain;

import org.gradle.internal.logging.text.DiagnosticsVisitor;

public class ComponentFound<T> implements SearchResult<T> {
    private final T component;

    public ComponentFound(T component) {
        this.component = component;
    }

    @Override
    public T getComponent() {
        return component;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void explain(DiagnosticsVisitor visitor) {
    }
}
