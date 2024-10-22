package org.gradle.api.publish.maven;

import org.gradle.api.provider.Property;

public interface MavenPomCiManagement {

    Property<String> getSystem();

    Property<String> getUrl();

}
