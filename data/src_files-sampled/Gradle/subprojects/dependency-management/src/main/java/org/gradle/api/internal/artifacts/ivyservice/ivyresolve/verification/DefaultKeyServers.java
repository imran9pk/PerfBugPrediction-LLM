package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public abstract class DefaultKeyServers {
    private final static List<URI> DEFAULT_KEYSERVERS = ImmutableList.of(
        uri("hkp://ha.pool.sks-keyservers.net"),
        uri("https://keys.fedoraproject.org"),
        uri("https://keyserver.ubuntu.com"),
        uri("https://keys.openpgp.org")
    );

    private static URI uri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static List<URI> getOrDefaults(List<URI> uris) {
        if (uris.isEmpty()) {
            return DEFAULT_KEYSERVERS;
        }
        return uris;
    }
}
