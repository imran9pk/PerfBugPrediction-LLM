package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPomCiManagement;
import org.gradle.api.publish.maven.MavenPomIssueManagement;

public class DefaultMavenPomProjectManagement implements MavenPomCiManagement, MavenPomIssueManagement {

    private final Property<String> system;
    private final Property<String> url;

    public DefaultMavenPomProjectManagement(ObjectFactory objectFactory) {
        system = objectFactory.property(String.class);
        url = objectFactory.property(String.class);
    }

    @Override
    public Property<String> getSystem() {
        return system;
    }

    @Override
    public Property<String> getUrl() {
        return url;
    }

}
