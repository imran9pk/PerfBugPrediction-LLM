package org.gradle.api.plugins.antlr;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;

@Deprecated
public interface AntlrSourceVirtualDirectory {
    String NAME = "antlr";

    SourceDirectorySet getAntlr();

    @SuppressWarnings("rawtypes")
    AntlrSourceVirtualDirectory antlr(Closure configureClosure);

    AntlrSourceVirtualDirectory antlr(Action<? super SourceDirectorySet> configureAction);
}
