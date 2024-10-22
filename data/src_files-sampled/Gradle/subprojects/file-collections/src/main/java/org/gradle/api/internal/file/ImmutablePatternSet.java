package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;

public class ImmutablePatternSet extends PatternSet {

    public static ImmutablePatternSet of(PatternSet source) {
        if (source instanceof ImmutablePatternSet) {
            return (ImmutablePatternSet) source;
        } else {
            return new ImmutablePatternSet(source);
        }
    }

    private ImmutablePatternSet(PatternSet source) {
        super(source);
        copyFrom(source);
    }

    @Override
    public PatternSet setIncludes(Iterable<String> includes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(String... includes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(Iterable includes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(Spec<FileTreeElement> spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCaseSensitive(boolean caseSensitive) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet setExcludes(Iterable<String> excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet includeSpecs(Iterable<Spec<FileTreeElement>> includeSpecs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(Closure closure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(String... excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(Iterable excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet excludeSpecs(Iterable<Spec<FileTreeElement>> excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(Spec<FileTreeElement> spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(Closure closure) {
        throw new UnsupportedOperationException();
    }
}
