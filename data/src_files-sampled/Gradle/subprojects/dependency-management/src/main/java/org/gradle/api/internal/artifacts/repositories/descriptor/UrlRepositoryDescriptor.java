package org.gradle.api.internal.artifacts.repositories.descriptor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.net.URI;
import java.util.List;

abstract class UrlRepositoryDescriptor extends RepositoryDescriptor {

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    public enum Property {
        URL,
        METADATA_SOURCES,
        AUTHENTICATED,
        AUTHENTICATION_SCHEMES,
    }

    public final URI url;
    public final ImmutableList<String> metadataSources;
    public final boolean authenticated;
    public final ImmutableList<String> authenticationSchemes;

    protected UrlRepositoryDescriptor(
        String name,
        URI url,
        ImmutableList<String> metadataSources,
        boolean authenticated,
        ImmutableList<String> authenticationSchemes
    ) {
        super(name);
        this.url = url;
        this.metadataSources = metadataSources;
        this.authenticated = authenticated;
        this.authenticationSchemes = authenticationSchemes;
    }

    @Override
    protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
        if (url != null) {
            builder.put(Property.URL.name(), url);
        }
        builder.put(Property.METADATA_SOURCES.name(), metadataSources);
        builder.put(Property.AUTHENTICATED.name(), authenticated);
        builder.put(Property.AUTHENTICATION_SCHEMES.name(), authenticationSchemes);
    }

    static abstract class Builder<T extends Builder<T>> {

        final String name;
        final URI url;

        ImmutableList<String> metadataSources;
        Boolean authenticated;
        ImmutableList<String> authenticationSchemes;

        Builder(String name, URI url) {
            this.name = name;
            this.url = url;
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T setMetadataSources(List<String> metadataSources) {
            this.metadataSources = ImmutableList.copyOf(metadataSources);
            return self();
        }

        public T setAuthenticated(boolean authenticated) {
            this.authenticated = authenticated;
            return self();
        }

        public T setAuthenticationSchemes(List<String> authenticationSchemes) {
            this.authenticationSchemes = ImmutableList.copyOf(authenticationSchemes);
            return self();
        }

    }
}
