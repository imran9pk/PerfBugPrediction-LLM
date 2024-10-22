package org.gradle.ide.xcode.internal.xcodeproj;

import com.google.common.collect.Lists;

import java.util.List;

public abstract class PBXBuildPhase extends PBXProjectItem {
    private final List<PBXBuildFile> files;

    public PBXBuildPhase() {
        this.files = Lists.newArrayList();
    }

    public List<PBXBuildFile> getFiles() {
        return files;
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        s.addField("files", files);
    }
}
