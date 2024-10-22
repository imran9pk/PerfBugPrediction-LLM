package org.gradle.api.internal.tasks.compile.processing;

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

public class NonIncrementalProcessor extends DelegatingProcessor {

    private final NonIncrementalProcessingStrategy strategy;

    public NonIncrementalProcessor(Processor delegate, AnnotationProcessorResult result) {
        super(delegate);
        this.strategy = new NonIncrementalProcessingStrategy(delegate.getClass().getName(), result);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        strategy.recordProcessingInputs(getSupportedAnnotationTypes(), annotations, roundEnv);
        return super.process(annotations, roundEnv);
    }
}
