package org.gradle.internal.logging.console;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.operations.OperationIdentifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkInProgressRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final ProgressOperations operations = new ProgressOperations();
    private final BuildProgressArea progressArea;
    private final DefaultWorkInProgressFormatter labelFormatter;
    private final ConsoleLayoutCalculator consoleLayoutCalculator;

    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();

    private final Deque<StyledLabel> unusedProgressLabels;

    private final Map<OperationIdentifier, AssociationLabel> operationIdToAssignedLabels = new HashMap<OperationIdentifier, AssociationLabel>();

    private final Deque<ProgressOperation> unassignedProgressOperations = new ArrayDeque<ProgressOperation>();

    public WorkInProgressRenderer(OutputEventListener listener, BuildProgressArea progressArea, DefaultWorkInProgressFormatter labelFormatter, ConsoleLayoutCalculator consoleLayoutCalculator) {
        this.listener = listener;
        this.progressArea = progressArea;
        this.labelFormatter = labelFormatter;
        this.consoleLayoutCalculator = consoleLayoutCalculator;
        this.unusedProgressLabels = new ArrayDeque<StyledLabel>(progressArea.getBuildProgressLabels());
    }

    @Override
    public void onOutput(OutputEvent event) {
        queue.add(event);

        if (event instanceof UpdateNowEvent) {
            renderNow();
        } else if (event instanceof EndOutputEvent) {
            progressArea.setVisible(false);
        }

        listener.onOutput(event);
    }

    private Set<OperationIdentifier> toOperationIdSet(Iterable<ProgressCompleteEvent> events) {
        return Sets.newHashSet(Iterables.transform(events, new Function<ProgressCompleteEvent, OperationIdentifier>() {
            @Override
            public OperationIdentifier apply(ProgressCompleteEvent event) {
                return event.getProgressOperationId();
            }
        }));
    }

    private void resizeTo(int newBuildProgressLabelCount) {
        int previousBuildProgressLabelCount = progressArea.getBuildProgressLabels().size();
        newBuildProgressLabelCount = consoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(newBuildProgressLabelCount);
        if (previousBuildProgressLabelCount >= newBuildProgressLabelCount) {
            return;
        }

        progressArea.resizeBuildProgressTo(newBuildProgressLabelCount);

        for (int i = newBuildProgressLabelCount - 1; i >= previousBuildProgressLabelCount; --i) {
            unusedProgressLabels.push(progressArea.getBuildProgressLabels().get(i));
        }
    }

    private void attach(ProgressOperation operation) {
        if (operation.hasChildren() || !isRenderable(operation)) {
            return;
        }

        if (operation.getParent() != null) {
            unshow(operation.getParent());
        }

        if (unusedProgressLabels.isEmpty()) {
            int newValue = operationIdToAssignedLabels.size() + 1;
            resizeTo(newValue);
            }

        if (unusedProgressLabels.isEmpty()) {
            unassignedProgressOperations.add(operation);
        } else {
            attach(operation, unusedProgressLabels.pop());
        }
    }

    private void attach(ProgressOperation operation, StyledLabel label) {
        AssociationLabel association = new AssociationLabel(operation, label);
        operationIdToAssignedLabels.put(operation.getOperationId(), association);
    }

    private void detach(ProgressOperation operation) {
        if (!isRenderable(operation)) {
            return;
        }

        unshow(operation);

        if (operation.getParent() != null && isRenderable(operation.getParent())) {
            attach(operation.getParent());
        } else if (!unassignedProgressOperations.isEmpty()) {
            attach(unassignedProgressOperations.pop());
        }
    }

    private void unshow(ProgressOperation operation) {
        OperationIdentifier operationId = operation.getOperationId();
        AssociationLabel association = operationIdToAssignedLabels.remove(operationId);
        if (association != null) {
            unusedProgressLabels.push(association.label);
        }
        unassignedProgressOperations.remove(operation);
    }

    private boolean isRenderable(ProgressOperation operation) {
        for (ProgressOperation current = operation; current != null; current = current.getParent()) {
            if (current.getMessage() != null) {
                return true;
            }
        }

        return false;
    }

    private void renderNow() {
        if (queue.isEmpty()) {
            return;
        }

        Set<OperationIdentifier> completeEventOperationIds = toOperationIdSet(Iterables.filter(queue, ProgressCompleteEvent.class));
        Set<OperationIdentifier> operationIdsToSkip = new HashSet<OperationIdentifier>();

        for (OutputEvent event : queue) {
            if (event instanceof ProgressStartEvent) {
                progressArea.setVisible(true);
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                if (completeEventOperationIds.contains(startEvent.getProgressOperationId())) {
                    operationIdsToSkip.add(startEvent.getProgressOperationId());
                    } else {
                    attach(operations.start(startEvent.getStatus(), startEvent.getCategory(), startEvent.getProgressOperationId(), startEvent.getParentProgressOperationId()));
                }
            } else if (event instanceof ProgressCompleteEvent) {
                ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
                if (!operationIdsToSkip.contains(completeEvent.getProgressOperationId())) {
                    detach(operations.complete(completeEvent.getProgressOperationId()));
                }
            } else if (event instanceof ProgressEvent) {
                ProgressEvent progressEvent = (ProgressEvent) event;
                if (!operationIdsToSkip.contains(progressEvent.getProgressOperationId())) {
                    operations.progress(progressEvent.getStatus(), progressEvent.getProgressOperationId());
                }
            }
        }
        queue.clear();

        for (AssociationLabel associatedLabel : operationIdToAssignedLabels.values()) {
            associatedLabel.renderNow();
        }
        for (StyledLabel emptyLabel : unusedProgressLabels) {
            emptyLabel.setText(labelFormatter.format());
        }
    }

    private class AssociationLabel {
        final ProgressOperation operation;
        final StyledLabel label;

        AssociationLabel(ProgressOperation operation, StyledLabel label) {
            this.operation = operation;
            this.label = label;
        }

        void renderNow() {
            label.setText(labelFormatter.format(operation));
        }
    }
}
