package com.github.benmanes.caffeine.cache.local;

import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.Modifier;

import com.github.benmanes.caffeine.cache.Feature;
import com.google.common.collect.Sets;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class LocalCacheContext {
  public final boolean isFinal;
  public final TypeName superClass;
  public final TypeSpec.Builder cache;
  public final Set<Feature> parentFeatures;
  public final Set<Feature> generateFeatures;
  public final MethodSpec.Builder constructor;
  public final Set<String> suppressedWarnings;

  public LocalCacheContext(TypeName superClass, String className, boolean isFinal,
      Set<Feature> parentFeatures, Set<Feature> generateFeatures) {
    this.isFinal = isFinal;
    this.superClass = superClass;
    this.suppressedWarnings = new TreeSet<>();
    this.cache = TypeSpec.classBuilder(className);
    this.constructor = MethodSpec.constructorBuilder();
    this.parentFeatures = Sets.immutableEnumSet(parentFeatures);
    this.generateFeatures = Sets.immutableEnumSet(generateFeatures);
  }

  public Modifier[] publicFinalModifiers() {
    return isFinal
        ? new Modifier[] { Modifier.PUBLIC }
        : new Modifier[] { Modifier.PUBLIC, Modifier.FINAL };
  }

  public Modifier[] protectedFinalModifiers() {
    return isFinal
        ? new Modifier[] { Modifier.PROTECTED }
        : new Modifier[] { Modifier.PROTECTED, Modifier.FINAL };
  }
}
