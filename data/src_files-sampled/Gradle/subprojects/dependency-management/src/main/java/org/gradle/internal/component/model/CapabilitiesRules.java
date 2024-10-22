package org.gradle.internal.component.model;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;

import java.util.List;

public class CapabilitiesRules {
    private final List<VariantMetadataRules.VariantAction<? super MutableCapabilitiesMetadata>> actions = Lists.newLinkedList();


    public void addCapabilitiesAction(VariantMetadataRules.VariantAction<? super MutableCapabilitiesMetadata> action) {
        actions.add(action);
    }

    public CapabilitiesMetadata execute(VariantResolveMetadata variant, MutableCapabilitiesMetadata capabilities) {
        for (VariantMetadataRules.VariantAction<? super MutableCapabilitiesMetadata> action : actions) {
            action.maybeExecute(variant, capabilities);
        }
        return capabilities.asImmutable();
    }
}
