package org.gradle.language.assembler.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;

@Incubating
public class AssemblerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        project.getPluginManager().apply(AssemblerLangPlugin.class);
    }

}
