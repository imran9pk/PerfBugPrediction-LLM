package org.gradle.internal.component;

import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatConfiguration;

public class IncompatibleConfigurationSelectionException extends StyledException {
    public IncompatibleConfigurationSelectionException(
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentResolveMetadata targetComponent,
        String targetConfiguration,
        boolean variantAware,
        AttributeDescriber describer) {
        super(generateMessage(fromConfigurationAttributes, attributeMatcher, targetComponent, targetConfiguration, variantAware, describer));
    }

    private static String generateMessage(AttributeContainerInternal fromConfigurationAttributes,
                                          AttributeMatcher attributeMatcher,
                                          ComponentResolveMetadata targetComponent,
                                          String targetConfiguration,
                                          boolean variantAware,
                                          AttributeDescriber describer) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node((variantAware ? "Variant '" : "Configuration '") + targetConfiguration + "' in " + style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()) + " does not match the consumer attributes");
        formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, targetComponent.getConfiguration(targetConfiguration), variantAware, false, describer);
        return formatter.toString();
    }

}
