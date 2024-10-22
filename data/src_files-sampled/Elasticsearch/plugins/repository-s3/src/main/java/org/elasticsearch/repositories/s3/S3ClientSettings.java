package org.elasticsearch.repositories.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.common.settings.SecureSetting;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class S3ClientSettings {

    private static final String PREFIX = "s3.client.";

    private static final String PLACEHOLDER_CLIENT = "placeholder";

    static final Setting.AffixSetting<SecureString> ACCESS_KEY_SETTING = Setting.affixKeySetting(PREFIX, "access_key",
        key -> SecureSetting.secureString(key, null));

    static final Setting.AffixSetting<SecureString> SECRET_KEY_SETTING = Setting.affixKeySetting(PREFIX, "secret_key",
        key -> SecureSetting.secureString(key, null));

    static final Setting.AffixSetting<SecureString> SESSION_TOKEN_SETTING = Setting.affixKeySetting(PREFIX, "session_token",
        key -> SecureSetting.secureString(key, null));

    static final Setting.AffixSetting<String> ENDPOINT_SETTING = Setting.affixKeySetting(PREFIX, "endpoint",
        key -> new Setting<>(key, "", s -> s.toLowerCase(Locale.ROOT), Property.NodeScope));

    static final Setting.AffixSetting<Protocol> PROTOCOL_SETTING = Setting.affixKeySetting(PREFIX, "protocol",
        key -> new Setting<>(key, "https", s -> Protocol.valueOf(s.toUpperCase(Locale.ROOT)), Property.NodeScope));

    static final Setting.AffixSetting<String> PROXY_HOST_SETTING = Setting.affixKeySetting(PREFIX, "proxy.host",
        key -> Setting.simpleString(key, Property.NodeScope));

    static final Setting.AffixSetting<Integer> PROXY_PORT_SETTING = Setting.affixKeySetting(PREFIX, "proxy.port",
        key -> Setting.intSetting(key, 80, 0, 1<<16, Property.NodeScope));

    static final Setting.AffixSetting<SecureString> PROXY_USERNAME_SETTING = Setting.affixKeySetting(PREFIX, "proxy.username",
        key -> SecureSetting.secureString(key, null));

    static final Setting.AffixSetting<SecureString> PROXY_PASSWORD_SETTING = Setting.affixKeySetting(PREFIX, "proxy.password",
        key -> SecureSetting.secureString(key, null));

    static final Setting.AffixSetting<TimeValue> READ_TIMEOUT_SETTING = Setting.affixKeySetting(PREFIX, "read_timeout",
        key -> Setting.timeSetting(key, TimeValue.timeValueMillis(ClientConfiguration.DEFAULT_SOCKET_TIMEOUT), Property.NodeScope));

    static final Setting.AffixSetting<Integer> MAX_RETRIES_SETTING = Setting.affixKeySetting(PREFIX, "max_retries",
        key -> Setting.intSetting(key, ClientConfiguration.DEFAULT_RETRY_POLICY.getMaxErrorRetry(), 0, Property.NodeScope));

    static final Setting.AffixSetting<Boolean> USE_THROTTLE_RETRIES_SETTING = Setting.affixKeySetting(PREFIX, "use_throttle_retries",
        key -> Setting.boolSetting(key, ClientConfiguration.DEFAULT_THROTTLE_RETRIES, Property.NodeScope));

    final S3BasicCredentials credentials;

    final String endpoint;

    final Protocol protocol;

    final String proxyHost;

    final int proxyPort;

    final String proxyUsername;

    final String proxyPassword;

    final int readTimeoutMillis;

    final int maxRetries;

    final boolean throttleRetries;

    private S3ClientSettings(S3BasicCredentials credentials, String endpoint, Protocol protocol,
                             String proxyHost, int proxyPort, String proxyUsername, String proxyPassword,
                             int readTimeoutMillis, int maxRetries, boolean throttleRetries) {
        this.credentials = credentials;
        this.endpoint = endpoint;
        this.protocol = protocol;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxRetries = maxRetries;
        this.throttleRetries = throttleRetries;
    }

    S3ClientSettings refine(RepositoryMetaData metadata) {
        final Settings repoSettings = metadata.settings();
        final Settings normalizedSettings =
            Settings.builder().put(repoSettings).normalizePrefix(PREFIX + PLACEHOLDER_CLIENT + '.').build();
        final String newEndpoint = getRepoSettingOrDefault(ENDPOINT_SETTING, normalizedSettings, endpoint);

        final Protocol newProtocol = getRepoSettingOrDefault(PROTOCOL_SETTING, normalizedSettings, protocol);
        final String newProxyHost = getRepoSettingOrDefault(PROXY_HOST_SETTING, normalizedSettings, proxyHost);
        final int newProxyPort = getRepoSettingOrDefault(PROXY_PORT_SETTING, normalizedSettings, proxyPort);
        final int newReadTimeoutMillis = Math.toIntExact(
            getRepoSettingOrDefault(READ_TIMEOUT_SETTING, normalizedSettings, TimeValue.timeValueMillis(readTimeoutMillis)).millis());
        final int newMaxRetries = getRepoSettingOrDefault(MAX_RETRIES_SETTING, normalizedSettings, maxRetries);
        final boolean newThrottleRetries = getRepoSettingOrDefault(USE_THROTTLE_RETRIES_SETTING, normalizedSettings, throttleRetries);
        final S3BasicCredentials newCredentials;
        if (checkDeprecatedCredentials(repoSettings)) {
            newCredentials = loadDeprecatedCredentials(repoSettings);
        } else {
            newCredentials = credentials;
        }
        if (Objects.equals(endpoint, newEndpoint) && protocol == newProtocol && Objects.equals(proxyHost, newProxyHost)
            && proxyPort == newProxyPort && newReadTimeoutMillis == readTimeoutMillis && maxRetries == newMaxRetries
            && newThrottleRetries == throttleRetries && Objects.equals(credentials, newCredentials)) {
            return this;
        }
        return new S3ClientSettings(
            newCredentials,
            newEndpoint,
            newProtocol,
            newProxyHost,
            newProxyPort,
            proxyUsername,
            proxyPassword,
            newReadTimeoutMillis,
            newMaxRetries,
            newThrottleRetries
        );
    }

    static Map<String, S3ClientSettings> load(Settings settings) {
        final Set<String> clientNames = settings.getGroups(PREFIX).keySet();
        final Map<String, S3ClientSettings> clients = new HashMap<>();
        for (final String clientName : clientNames) {
            clients.put(clientName, getClientSettings(settings, clientName));
        }
        if (clients.containsKey("default") == false) {
            clients.put("default", getClientSettings(settings, "default"));
        }
        return Collections.unmodifiableMap(clients);
    }

    static boolean checkDeprecatedCredentials(Settings repositorySettings) {
        if (S3Repository.ACCESS_KEY_SETTING.exists(repositorySettings)) {
            if (S3Repository.SECRET_KEY_SETTING.exists(repositorySettings) == false) {
                throw new IllegalArgumentException("Repository setting [" + S3Repository.ACCESS_KEY_SETTING.getKey()
                        + " must be accompanied by setting [" + S3Repository.SECRET_KEY_SETTING.getKey() + "]");
            }
            return true;
        } else if (S3Repository.SECRET_KEY_SETTING.exists(repositorySettings)) {
            throw new IllegalArgumentException("Repository setting [" + S3Repository.SECRET_KEY_SETTING.getKey()
                    + " must be accompanied by setting [" + S3Repository.ACCESS_KEY_SETTING.getKey() + "]");
        }
        return false;
    }

    private static S3BasicCredentials loadDeprecatedCredentials(Settings repositorySettings) {
        assert checkDeprecatedCredentials(repositorySettings);
        try (SecureString key = S3Repository.ACCESS_KEY_SETTING.get(repositorySettings);
                SecureString secret = S3Repository.SECRET_KEY_SETTING.get(repositorySettings)) {
            return new S3BasicCredentials(key.toString(), secret.toString());
        }
    }

    private static S3BasicCredentials loadCredentials(Settings settings, String clientName) {
        try (SecureString accessKey = getConfigValue(settings, clientName, ACCESS_KEY_SETTING);
             SecureString secretKey = getConfigValue(settings, clientName, SECRET_KEY_SETTING);
             SecureString sessionToken = getConfigValue(settings, clientName, SESSION_TOKEN_SETTING)) {
            if (accessKey.length() != 0) {
                if (secretKey.length() != 0) {
                    if (sessionToken.length() != 0) {
                        return new S3BasicSessionCredentials(accessKey.toString(), secretKey.toString(), sessionToken.toString());
                    } else {
                        return new S3BasicCredentials(accessKey.toString(), secretKey.toString());
                    }
                } else {
                    throw new IllegalArgumentException("Missing secret key for s3 client [" + clientName + "]");
                }
            } else {
                if (secretKey.length() != 0) {
                    throw new IllegalArgumentException("Missing access key for s3 client [" + clientName + "]");
                }
                if (sessionToken.length() != 0) {
                    throw new IllegalArgumentException("Missing access key and secret key for s3 client [" + clientName + "]");
                }
                return null;
            }
        }
    }

    static S3ClientSettings getClientSettings(final Settings settings, final String clientName) {
        try (SecureString proxyUsername = getConfigValue(settings, clientName, PROXY_USERNAME_SETTING);
             SecureString proxyPassword = getConfigValue(settings, clientName, PROXY_PASSWORD_SETTING)) {
            return new S3ClientSettings(
                S3ClientSettings.loadCredentials(settings, clientName),
                getConfigValue(settings, clientName, ENDPOINT_SETTING),
                getConfigValue(settings, clientName, PROTOCOL_SETTING),
                getConfigValue(settings, clientName, PROXY_HOST_SETTING),
                getConfigValue(settings, clientName, PROXY_PORT_SETTING),
                proxyUsername.toString(),
                proxyPassword.toString(),
                Math.toIntExact(getConfigValue(settings, clientName, READ_TIMEOUT_SETTING).millis()),
                getConfigValue(settings, clientName, MAX_RETRIES_SETTING),
                getConfigValue(settings, clientName, USE_THROTTLE_RETRIES_SETTING)
            );
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final S3ClientSettings that = (S3ClientSettings) o;
        return proxyPort == that.proxyPort &&
            readTimeoutMillis == that.readTimeoutMillis &&
            maxRetries == that.maxRetries &&
            throttleRetries == that.throttleRetries &&
            Objects.equals(credentials, that.credentials) &&
            Objects.equals(endpoint, that.endpoint) &&
            protocol == that.protocol &&
            Objects.equals(proxyHost, that.proxyHost) &&
            Objects.equals(proxyUsername, that.proxyUsername) &&
            Objects.equals(proxyPassword, that.proxyPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentials, endpoint, protocol, proxyHost, proxyPort, proxyUsername, proxyPassword,
            readTimeoutMillis, maxRetries, throttleRetries);
    }

    private static <T> T getConfigValue(Settings settings, String clientName,
                                        Setting.AffixSetting<T> clientSetting) {
        final Setting<T> concreteSetting = clientSetting.getConcreteSettingForNamespace(clientName);
        return concreteSetting.get(settings);
    }

    private static <T> T getRepoSettingOrDefault(Setting.AffixSetting<T> setting, Settings normalizedSettings, T defaultValue) {
        if (setting.getConcreteSettingForNamespace(PLACEHOLDER_CLIENT).exists(normalizedSettings)) {
            return getConfigValue(normalizedSettings, PLACEHOLDER_CLIENT, setting);
        }
        return defaultValue;
    }
}
