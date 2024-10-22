package org.elasticsearch.client.security;

import org.elasticsearch.client.security.user.User;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.optionalConstructorArg;

public final class AuthenticateResponse {

    static final ParseField USERNAME = new ParseField("username");
    static final ParseField ROLES = new ParseField("roles");
    static final ParseField METADATA = new ParseField("metadata");
    static final ParseField FULL_NAME = new ParseField("full_name");
    static final ParseField EMAIL = new ParseField("email");
    static final ParseField ENABLED = new ParseField("enabled");
    static final ParseField AUTHENTICATION_REALM = new ParseField("authentication_realm");
    static final ParseField LOOKUP_REALM = new ParseField("lookup_realm");
    static final ParseField REALM_NAME = new ParseField("name");
    static final ParseField REALM_TYPE = new ParseField("type");

    @SuppressWarnings("unchecked")
    private static final ConstructingObjectParser<AuthenticateResponse, Void> PARSER = new ConstructingObjectParser<>(
            "client_security_authenticate_response", true,
            a -> new AuthenticateResponse(new User((String) a[0], ((List<String>) a[1]), (Map<String, Object>) a[2],
                (String) a[3], (String) a[4]), (Boolean) a[5], (RealmInfo) a[6], (RealmInfo) a[7]));
    static {
        final ConstructingObjectParser<RealmInfo, Void> realmInfoParser = new ConstructingObjectParser<>("realm_info", true,
            a -> new RealmInfo((String) a[0], (String) a[1]));
        realmInfoParser.declareString(constructorArg(), REALM_NAME);
        realmInfoParser.declareString(constructorArg(), REALM_TYPE);
        PARSER.declareString(constructorArg(), USERNAME);
        PARSER.declareStringArray(constructorArg(), ROLES);
        PARSER.<Map<String, Object>>declareObject(constructorArg(), (parser, c) -> parser.map(), METADATA);
        PARSER.declareStringOrNull(optionalConstructorArg(), FULL_NAME);
        PARSER.declareStringOrNull(optionalConstructorArg(), EMAIL);
        PARSER.declareBoolean(constructorArg(), ENABLED);
        PARSER.declareObject(constructorArg(), realmInfoParser, AUTHENTICATION_REALM);
        PARSER.declareObject(constructorArg(), realmInfoParser, LOOKUP_REALM);
    }

    private final User user;
    private final boolean enabled;
    private final RealmInfo authenticationRealm;
    private final RealmInfo lookupRealm;


    public AuthenticateResponse(User user, boolean enabled, RealmInfo authenticationRealm,
                                RealmInfo lookupRealm) {
        this.user = user;
        this.enabled = enabled;
        this.authenticationRealm = authenticationRealm;
        this.lookupRealm = lookupRealm;
    }

    public User getUser() {
        return user;
    }

    public boolean enabled() {
        return enabled;
    }

    public RealmInfo getAuthenticationRealm() {
        return authenticationRealm;
    }

    public RealmInfo getLookupRealm() {
        return lookupRealm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticateResponse that = (AuthenticateResponse) o;
        return enabled == that.enabled &&
            Objects.equals(user, that.user) &&
            Objects.equals(authenticationRealm, that.authenticationRealm) &&
            Objects.equals(lookupRealm, that.lookupRealm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, enabled, authenticationRealm, lookupRealm);
    }

    public static AuthenticateResponse fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    public static class RealmInfo {
        private String name;
        private String type;

        RealmInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RealmInfo realmInfo = (RealmInfo) o;
            return Objects.equals(name, realmInfo.name) &&
                Objects.equals(type, realmInfo.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type);
        }
    }
}
