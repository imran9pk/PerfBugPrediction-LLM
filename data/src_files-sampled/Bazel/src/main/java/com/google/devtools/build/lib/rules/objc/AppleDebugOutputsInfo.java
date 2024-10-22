package com.google.devtools.build.lib.rules.objc;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.BuiltinProvider;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.starlarkbuildapi.apple.AppleDebugOutputsApi;
import java.util.HashMap;
import java.util.Map;
import net.starlark.java.eval.Dict;

@Immutable
public final class AppleDebugOutputsInfo extends NativeInfo
    implements AppleDebugOutputsApi<Artifact> {

  enum OutputType {

    BITCODE_SYMBOLS,

    DSYM_BINARY,

    LINKMAP;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  public static final String STARLARK_NAME = "AppleDebugOutputs";

  public static final BuiltinProvider<AppleDebugOutputsInfo> STARLARK_CONSTRUCTOR =
      new BuiltinProvider<AppleDebugOutputsInfo>(STARLARK_NAME, AppleDebugOutputsInfo.class) {};

  private final ImmutableMap<String, Dict<String, Artifact>> outputsMap;

  private AppleDebugOutputsInfo(ImmutableMap<String, Dict<String, Artifact>> map) {
    this.outputsMap = map;
  }

  @Override
  public BuiltinProvider<AppleDebugOutputsInfo> getProvider() {
    return STARLARK_CONSTRUCTOR;
  }

  @Override
  public ImmutableMap<String, Dict<String, Artifact>> getOutputsMap() {
    return outputsMap;
  }

  public static class Builder {
    private final HashMap<String, HashMap<String, Artifact>> outputsByArch = Maps.newHashMap();

    private Builder() {}

    public static Builder create() {
      return new Builder();
    }

    public Builder addOutput(String arch, OutputType outputType, Artifact artifact) {
      outputsByArch.computeIfAbsent(arch, k -> new HashMap<String, Artifact>());

      outputsByArch.get(arch).put(outputType.toString(), artifact);
      return this;
    }

    public AppleDebugOutputsInfo build() {
      ImmutableMap.Builder<String, Dict<String, Artifact>> builder = ImmutableMap.builder();

      for (Map.Entry<String, HashMap<String, Artifact>> e : outputsByArch.entrySet()) {
        builder.put(e.getKey(), Dict.immutableCopyOf(e.getValue()));
      }

      return new AppleDebugOutputsInfo(builder.build());
    }
  }
}
