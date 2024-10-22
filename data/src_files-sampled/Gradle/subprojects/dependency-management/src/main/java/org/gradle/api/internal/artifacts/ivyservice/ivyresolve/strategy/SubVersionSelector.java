package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

public class SubVersionSelector extends AbstractStringVersionSelector {
    private final String prefix;

    public SubVersionSelector(String selector) {
        super(selector);
        prefix = selector.substring(0, selector.length() - 1);
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public boolean requiresMetadata() {
        return false;
    }

    @Override
    public boolean matchesUniqueVersion() {
        return false;
    }

    @Override
    public boolean accept(String candidate) {
        return candidate.startsWith(prefix);
    }

    @Override
    public boolean canShortCircuitWhenVersionAlreadyPreselected() {
        return false;
    }

}
