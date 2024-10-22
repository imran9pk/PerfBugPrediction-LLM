package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;

import java.util.List;
import java.util.Set;

class RejectedModuleMessageBuilder {
    String buildFailureMessage(ModuleResolveState module) {
        boolean hasRejectAll = false;
        for (SelectorState candidate : module.getSelectors()) {
            ResolvedVersionConstraint versionConstraint = candidate.getVersionConstraint();
            if (versionConstraint != null) {
                hasRejectAll |= versionConstraint.isRejectAll();
            }
        }
        StringBuilder sb = new StringBuilder();
        if (hasRejectAll) {
            sb.append("Module '").append(module.getId()).append("' has been rejected:\n");
        } else {
            sb.append("Cannot find a version of '").append(module.getId()).append("' that satisfies the version constraints:\n");
        }

        Set<EdgeState> allEdges = Sets.newLinkedHashSet();
        allEdges.addAll(module.getIncomingEdges());
        allEdges.addAll(module.getUnattachedDependencies());
        renderEdges(sb, allEdges);
        return sb.toString();
    }

    private void renderEdges(StringBuilder sb, Set<EdgeState> incomingEdges) {
        for (EdgeState incomingEdge : incomingEdges) {
            SelectorState selector = incomingEdge.getSelector();
            for (String path : MessageBuilderHelper.pathTo(incomingEdge, false)) {
                sb.append("   ").append(path);
                sb.append(" --> ");
                renderSelector(sb, selector);
                renderReason(sb, selector);
                sb.append("\n");
            }
        }
    }

    private static void renderSelector(StringBuilder sb, SelectorState selectorState) {
        sb.append('\'').append(selectorState.getRequested()).append('\'');
    }

    private static void renderReason(StringBuilder sb, SelectorState selector) {
        ComponentSelectionReasonInternal selectionReason = selector.getSelectionReason();
        if (selectionReason.hasCustomDescriptions()) {
            sb.append(" because of the following reason");
            List<String> reasons = Lists.newArrayListWithExpectedSize(1);
            for (ComponentSelectionDescriptor componentSelectionDescriptor : selectionReason.getDescriptions()) {
                ComponentSelectionDescriptorInternal next = (ComponentSelectionDescriptorInternal) componentSelectionDescriptor;
                if (next.hasCustomDescription()) {
                    reasons.add(next.getDescription());
                }
            }
            if (reasons.size() == 1) {
                sb.append(": ").append(reasons.get(0));
            } else {
                sb.append("s: ");
                Joiner.on(", ").appendTo(sb, reasons);
            }
        }
    }

}
