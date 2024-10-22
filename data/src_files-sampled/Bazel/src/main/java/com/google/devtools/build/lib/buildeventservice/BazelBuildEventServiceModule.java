package com.google.devtools.build.lib.buildeventservice;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.authandtls.AuthAndTLSOptions;
import com.google.devtools.build.lib.authandtls.GoogleAuthUtils;
import com.google.devtools.build.lib.buildeventservice.client.BuildEventServiceClient;
import com.google.devtools.build.lib.buildeventservice.client.ManagedBuildEventServiceGrpcClient;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public class BazelBuildEventServiceModule
    extends BuildEventServiceModule<BuildEventServiceOptions> {

  @AutoValue
  abstract static class BackendConfig {
    abstract String besBackend();

    abstract AuthAndTLSOptions authAndTLSOptions();
  }

  private BuildEventServiceClient client;
  private BackendConfig config;

  @Override
  protected Class<BuildEventServiceOptions> optionsClass() {
    return BuildEventServiceOptions.class;
  }

  @Override
  protected BuildEventServiceClient getBesClient(
      BuildEventServiceOptions besOptions, AuthAndTLSOptions authAndTLSOptions) throws IOException {
    BackendConfig newConfig =
        new AutoValue_BazelBuildEventServiceModule_BackendConfig(
            besOptions.besBackend, authAndTLSOptions);
    if (client == null || !Objects.equals(config, newConfig)) {
      clearBesClient();
      config = newConfig;
      client =
          new ManagedBuildEventServiceGrpcClient(
              newGrpcChannel(besOptions, authAndTLSOptions),
              GoogleAuthUtils.newCallCredentials(authAndTLSOptions));
    }
    return client;
  }

  @VisibleForTesting
  protected ManagedChannel newGrpcChannel(
      BuildEventServiceOptions besOptions, AuthAndTLSOptions authAndTLSOptions) throws IOException {
    return GoogleAuthUtils.newChannel(
        besOptions.besBackend, besOptions.besProxy, authAndTLSOptions, null);
  }

  @Override
  protected void clearBesClient() {
    if (client != null) {
      client.shutdown();
    }
    this.client = null;
    this.config = null;
  }

  private static final ImmutableSet<String> WHITELISTED_COMMANDS =
      ImmutableSet.of(
          "fetch",
          "build",
          "test",
          "run",
          "query",
          "aquery",
          "cquery",
          "coverage",
          "mobile-install");

  @Override
  protected Set<String> whitelistedCommands(BuildEventServiceOptions besOptions) {
    return WHITELISTED_COMMANDS;
  }

  @Override
  protected String getInvocationIdPrefix() {
    if (Strings.isNullOrEmpty(besOptions.besResultsUrl)) {
      return "";
    }
    return besOptions.besResultsUrl.endsWith("/")
        ? besOptions.besResultsUrl
        : besOptions.besResultsUrl + "/";
  }

  @Override
  protected String getBuildRequestIdPrefix() {
    return "";
  }
}
