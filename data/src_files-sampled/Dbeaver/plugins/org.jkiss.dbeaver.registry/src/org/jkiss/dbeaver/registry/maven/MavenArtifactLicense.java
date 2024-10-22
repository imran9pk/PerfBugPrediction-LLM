package org.jkiss.dbeaver.registry.maven;

public class MavenArtifactLicense
{
    private String name;
    private String url;

    public MavenArtifactLicense(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name;
    }

}
