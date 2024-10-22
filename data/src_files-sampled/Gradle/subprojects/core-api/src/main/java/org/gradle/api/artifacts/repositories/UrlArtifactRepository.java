package org.gradle.api.artifacts.repositories;

import java.net.URI;

public interface UrlArtifactRepository {

    URI getUrl();

    void setUrl(URI url);

    void setUrl(Object url);

    boolean isAllowInsecureProtocol();

    void setAllowInsecureProtocol(boolean allowInsecureProtocol);
}
