package com.google.devtools.build.lib.exec.local;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.exec.BinTools;
import java.util.Map;

public final class WindowsLocalEnvProvider implements LocalEnvProvider {
  private final Map<String, String> clientEnv;

  public WindowsLocalEnvProvider(Map<String, String> clientEnv) {
    this.clientEnv = clientEnv;
  }

  @Override
  public ImmutableMap<String, String> rewriteLocalEnv(
      Map<String, String> env, BinTools binTools, String fallbackTmpDir) {
    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    result.putAll(Maps.filterKeys(env, k -> !k.equals("TMP") && !k.equals("TEMP")));
    String p = clientEnv.get("TMP");
    if (Strings.isNullOrEmpty(p)) {
      p = clientEnv.get("TEMP");
      if (Strings.isNullOrEmpty(p)) {
        p = fallbackTmpDir;
      }
    }
    p = p.replace('/', '\\');
    result.put("TMP", p);
    result.put("TEMP", p);
    return result.build();
  }
}
