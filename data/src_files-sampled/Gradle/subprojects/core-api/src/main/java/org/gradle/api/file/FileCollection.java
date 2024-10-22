package org.gradle.api.file;

import groovy.lang.Closure;
import org.gradle.api.Buildable;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.internal.HasInternalProtocol;

import java.io.File;
import java.util.Set;

@HasInternalProtocol
public interface FileCollection extends Iterable<File>, AntBuilderAware, Buildable {
    File getSingleFile() throws IllegalStateException;

    Set<File> getFiles();

    boolean contains(File file);

    String getAsPath();

    FileCollection plus(FileCollection collection);

    FileCollection minus(FileCollection collection);

    FileCollection filter(Closure filterClosure);

    FileCollection filter(Spec<? super File> filterSpec);

    boolean isEmpty();

    FileTree getAsFileTree();

    Provider<Set<FileSystemLocation>> getElements();

    enum AntType {
        MatchingTask, FileSet, ResourceCollection
    }

    void addToAntBuilder(Object builder, String nodeName, AntType type);

    @Override
    Object addToAntBuilder(Object builder, String nodeName);
}
