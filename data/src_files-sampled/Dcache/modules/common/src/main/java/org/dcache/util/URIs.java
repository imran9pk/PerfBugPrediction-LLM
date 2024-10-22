package org.dcache.util;

import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

public class URIs {

    private static final Map<String, Integer> TO_DEFAULT_PORT = ImmutableMap.<String, Integer>builder()
          .put("ftp", 21)
          .put("http", 80)
          .put("https", 443)
          .put("gsiftp", 2811)
          .put("gridftp", 2811)
          .put("ldap", 389)
          .put("ldaps", 636)
          .put("srm", 8443)
          .build();

    private URIs() {
        }

    public static int portWithDefault(URI uri) {
        return portWithDefault(uri, null, -1);
    }

    public static Optional<Integer> optionalPortWithDefault(URI uri) {
        int port = portWithDefault(uri, null, -1);
        return port > -1 ? Optional.of(port) : Optional.<Integer>empty();
    }

    public static int portWithDefault(URI uri, String defaultScheme, int defaultPort) {
        int port = uri.getPort();

        if (uri.getPort() == -1) {
            String scheme = uri.getScheme();

            if (scheme != null) {
                if (scheme.equals(defaultScheme)) {
                    port = defaultPort;
                } else {
                    Integer fromDefaults = TO_DEFAULT_PORT.get(scheme);

                    if (fromDefaults != null) {
                        port = fromDefaults;
                    }
                }
            }
        }

        return port;
    }

    private static boolean isDefaultPortNeeded(URI uri) {
        return uri.getPort() == -1 && uri.getHost() != null;
    }

    public static URI withDefaultPort(URI uri, String scheme, int port) {
        if (isDefaultPortNeeded(uri)) {
            int defaultPort = portWithDefault(uri, scheme, port);

            if (defaultPort != -1) {
                try {
                    return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                          defaultPort, uri.getPath(), uri.getQuery(), uri.getFragment());
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Failed to add default port: " + e.getMessage(), e);
                }
            }
        }

        return uri;
    }

    public static URI withDefaultPort(URI uri) {
        return withDefaultPort(uri, null, -1);
    }

    public static URI createWithDefaultPort(String uri, String scheme, int port)
          throws URISyntaxException {
        return withDefaultPort(new URI(uri), scheme, port);
    }

    public static URI createWithDefaultPort(String uri) throws URISyntaxException {
        return withDefaultPort(new URI(uri));
    }
}
