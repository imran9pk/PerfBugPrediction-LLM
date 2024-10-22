package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Set;

final class DefaultModuleSetExclude implements ModuleSetExclude {
    private final Set<String> modules;
    private final int hashCode;

    DefaultModuleSetExclude(Set<String> modules) {
        this.modules = modules;
        this.hashCode = modules.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleSetExclude that = (DefaultModuleSetExclude) o;

        return modules.equals(that.modules);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public Set<String> getModules() {
        return modules;
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return modules.contains(module.getName());
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        return false;
    }

    @Override
    public boolean mayExcludeArtifacts() {
        return false;
    }

    @Override
    public String toString() {
        return "{ \"module names\" : [" + ExcludeJsonHelper.toJson(modules) + "]}";
    }
}
