package org.gradle.model.internal.manage.schema.extract;

import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerContext;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

public interface NodeInitializerExtractionStrategy {
    <T> NodeInitializer extractNodeInitializer(ModelSchema<T> schema, NodeInitializerContext<T> context);

    Iterable<ModelType<?>> supportedTypes();
}
