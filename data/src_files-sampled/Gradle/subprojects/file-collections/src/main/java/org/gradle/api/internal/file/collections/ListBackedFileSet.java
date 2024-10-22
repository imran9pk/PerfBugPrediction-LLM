package org.gradle.api.internal.file.collections;

import com.google.common.collect.ImmutableSet;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Set;

public class ListBackedFileSet implements MinimalFileSet {
    private final ImmutableSet<File> files;

    public ListBackedFileSet(ImmutableSet<File> files) {
        this.files = files;
    }

    @Override
    public String getDisplayName() {
        switch (files.size()) {
            case 0:
                return "empty file collection";
            case 1:
                return String.format("file '%s'", files.iterator().next());
            default:
                return String.format("files %s", GUtil.toString(files));
        }
    }

    @Override
    public Set<File> getFiles() {
        return files;
    }
}
