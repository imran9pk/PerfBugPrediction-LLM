package org.gradle.api.resources;

public interface ResourceHandler {

    ReadableResource gzip(Object path);

    ReadableResource bzip2(Object path);

    TextResourceFactory getText();
}
