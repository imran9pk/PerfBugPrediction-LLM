package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.VariantFileMetadata;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultMutableVariantFilesMetadata;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;

import java.util.List;

public class VariantFilesRules {
    private final List<VariantMetadataRules.VariantAction<? super MutableVariantFilesMetadata>> actions = Lists.newLinkedList();

    public void addFilesAction(VariantMetadataRules.VariantAction<? super MutableVariantFilesMetadata> action) {
        actions.add(action);
    }

    public <T extends ComponentVariant.File> ImmutableList<T> executeForFiles(VariantResolveMetadata variant, ImmutableList<T> declaredFiles, ModuleComponentIdentifier componentIdentifier) {
        DefaultMutableVariantFilesMetadata filesMetadata = execute(variant);
        if (filesMetadata.getFiles().isEmpty()) {
            return declaredFiles;
        }
        ImmutableList.Builder<T> builder = new ImmutableList.Builder<>();
        if (!filesMetadata.isClearExistingFiles()) {
            builder.addAll(declaredFiles);
        }
        for (VariantFileMetadata file : filesMetadata.getFiles()) {
            builder.add(Cast.<T>uncheckedNonnullCast(new AbstractMutableModuleComponentResolveMetadata.FileImpl(file.getName(), file.getUrl())));
        }
        return builder.build();
    }

    public <T extends ComponentArtifactMetadata> ImmutableList<T> executeForArtifacts(VariantResolveMetadata variant, ImmutableList<T> artifacts, ModuleComponentIdentifier componentIdentifier) {
        DefaultMutableVariantFilesMetadata filesMetadata = execute(variant);
        if (filesMetadata.getFiles().isEmpty()) {
            return artifacts;
        }
        ImmutableList.Builder<T> builder = new ImmutableList.Builder<>();
        if (!filesMetadata.isClearExistingFiles()) {
            for (T existingArtifact : artifacts) {
                if (isFilePathUnambiguous(existingArtifact)) {
                    builder.add(existingArtifact);
                }
            }
        }
        for (VariantFileMetadata file : filesMetadata.getFiles()) {
            builder.add(Cast.<T>uncheckedNonnullCast(new UrlBackedArtifactMetadata(componentIdentifier, file.getName(), file.getUrl())));
        }
        return builder.build();
    }

    private DefaultMutableVariantFilesMetadata execute(VariantResolveMetadata variant) {
        DefaultMutableVariantFilesMetadata filesMetadata = new DefaultMutableVariantFilesMetadata();
        for (VariantMetadataRules.VariantAction<? super MutableVariantFilesMetadata> action : actions) {
            action.maybeExecute(variant, filesMetadata);
        }
        return filesMetadata;
    }

    private <T extends ComponentArtifactMetadata> boolean isFilePathUnambiguous(T existingArtifact) {
        return !(existingArtifact instanceof DefaultModuleComponentArtifactMetadata) || "jar".equals(existingArtifact.getName().getExtension());
    }
}
