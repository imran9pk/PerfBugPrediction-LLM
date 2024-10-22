package org.elasticsearch.client.security;

import org.elasticsearch.client.Validatable;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.util.CollectionUtils;

public final class DeletePrivilegesRequest implements Validatable {

    private final String application;
    private final String[] privileges;
    private final RefreshPolicy refreshPolicy;

    public DeletePrivilegesRequest(String application, String... privileges) {
        this(application, privileges, null);
    }

    public DeletePrivilegesRequest(String application, String[] privileges, @Nullable RefreshPolicy refreshPolicy) {
        if (Strings.hasText(application) == false) {
            throw new IllegalArgumentException("application name is required");
        }
        if (CollectionUtils.isEmpty(privileges)) {
            throw new IllegalArgumentException("privileges are required");
        }
        this.application = application;
        this.privileges = privileges;
        this.refreshPolicy = (refreshPolicy == null) ? RefreshPolicy.getDefault() : refreshPolicy;
    }

    public String getApplication() {
        return application;
    }

    public String[] getPrivileges() {
        return privileges;
    }

    public RefreshPolicy getRefreshPolicy() {
        return refreshPolicy;
    }
}
