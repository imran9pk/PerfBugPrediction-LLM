package com.google.devtools.build.lib.analysis;

import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.PackageSpecification.PackageGroupContents;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;

@Immutable
@AutoCodec
public final class VisibilityProviderImpl implements VisibilityProvider {
  private final NestedSet<PackageGroupContents> visibility;

  public VisibilityProviderImpl(NestedSet<PackageGroupContents> visibility) {
    this.visibility = visibility;
  }

  @Override
  public NestedSet<PackageGroupContents> getVisibility() {
    return visibility;
  }
}
