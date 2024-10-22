package com.google.devtools.build.lib.skyframe.serialization;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skyframe.serialization.Memoizer.Deserializer;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodec.MemoizationStrategy;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodecRegistry.CodecDescriptor;
import com.google.protobuf.CodedInputStream;
import java.io.IOException;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

public class DeserializationContext {
  private final ObjectCodecRegistry registry;
  private final ImmutableMap<Class<?>, Object> dependencies;
  private final Memoizer.Deserializer deserializer;

  private DeserializationContext(
      ObjectCodecRegistry registry,
      ImmutableMap<Class<?>, Object> dependencies,
      Deserializer deserializer) {
    this.registry = registry;
    this.dependencies = dependencies;
    this.deserializer = deserializer;
  }

  @VisibleForTesting
  public DeserializationContext(
      ObjectCodecRegistry registry, ImmutableMap<Class<?>, Object> dependencies) {
    this(registry, dependencies, null);
  }

  @VisibleForTesting
  public DeserializationContext(ImmutableMap<Class<?>, Object> dependencies) {
    this(AutoRegistry.get(), dependencies);
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T deserialize(CodedInputStream codedIn) throws IOException, SerializationException {
    return deserializeInternal(codedIn, null);
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T deserializeWithAdHocMemoizationStrategy(
      CodedInputStream codedIn, MemoizationStrategy memoizationStrategy)
      throws IOException, SerializationException {
    return deserializeInternal(codedIn, memoizationStrategy);
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  private <T> T deserializeInternal(
      CodedInputStream codedIn, @Nullable MemoizationStrategy customMemoizationStrategy)
      throws IOException, SerializationException {
    int tag = codedIn.readSInt32();
    if (tag == 0) {
      return null;
    }
    if (tag < 0) {
      return (T) deserializer.getMemoized(-tag - 1); }
    T constant = (T) registry.maybeGetConstantByTag(tag);
    if (constant != null) {
      return constant;
    }
    CodecDescriptor codecDescriptor = registry.getCodecDescriptorByTag(tag);
    if (deserializer == null) {
      return (T) codecDescriptor.deserialize(this, codedIn); } else {
      @SuppressWarnings("unchecked")
      ObjectCodec<T> castCodec = (ObjectCodec<T>) codecDescriptor.getCodec();
      MemoizationStrategy memoizationStrategy =
          customMemoizationStrategy != null ? customMemoizationStrategy : castCodec.getStrategy();
      return deserializer.deserialize(this, castCodec, memoizationStrategy, codedIn);
    }
  }


  public <T> void registerInitialValue(T initialValue) {
    if (deserializer == null) {
      return;
    }
    deserializer.registerInitialValue(initialValue);
  }

  public <T> T getDependency(Class<T> type) {
    Preconditions.checkNotNull(type);
    return type.cast(dependencies.get(type));
  }

  @CheckReturnValue
  public DeserializationContext getMemoizingContext() {
    if (deserializer != null) {
      return this;
    }
    return getNewMemoizingContext();
  }

  public DeserializationContext getNewMemoizingContext() {
    return new DeserializationContext(this.registry, this.dependencies, new Deserializer());
  }

  public DeserializationContext getNewNonMemoizingContext() {
    return new DeserializationContext(this.registry, this.dependencies, null);
  }
}
