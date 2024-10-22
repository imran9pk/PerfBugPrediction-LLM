package com.google.devtools.build.lib.rules.cpp;

import com.google.common.collect.Interner;
import com.google.devtools.build.lib.concurrent.BlazeInterners;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionName;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import java.util.Objects;

@AutoCodec
@Immutable
public class CcSkyframeFdoSupportValue implements SkyValue {
  public static final SkyFunctionName SKYFUNCTION = SkyFunctionName.createHermetic("FDO_SUPPORT");

  @Immutable
  @AutoCodec
  public static class Key implements SkyKey {
    private static final Interner<Key> interner = BlazeInterners.newWeakInterner();

    private final PathFragment fdoZipPath;

    private Key(PathFragment fdoZipPath) {
      this.fdoZipPath = fdoZipPath;
    }

    @AutoCodec.Instantiator
    @AutoCodec.VisibleForSerialization
    static Key of(PathFragment fdoZipPath) {
      return interner.intern(new Key(fdoZipPath));
    }

    public PathFragment getFdoZipPath() {
      return fdoZipPath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Key)) {
        return false;
      }
      Key key = (Key) o;
      return Objects.equals(fdoZipPath, key.fdoZipPath);
    }

    @Override
    public int hashCode() {

      return Objects.hash(fdoZipPath);
    }

    @Override
    public SkyFunctionName functionName() {
      return SKYFUNCTION;
    }
  }

  private final Path fdoZipPath;

  CcSkyframeFdoSupportValue(Path fdoZipPath) {
    this.fdoZipPath = fdoZipPath;
  }

  public Path getFdoZipPath() {
    return fdoZipPath;
  }

  public static SkyKey key(PathFragment fdoZipPath) {
    return Key.of(fdoZipPath);
  }
}
