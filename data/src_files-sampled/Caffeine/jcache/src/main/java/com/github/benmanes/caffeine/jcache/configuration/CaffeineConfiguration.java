package com.github.benmanes.caffeine.jcache.configuration;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.jcache.copy.Copier;
import com.github.benmanes.caffeine.jcache.copy.JavaSerializationCopier;

public final class CaffeineConfiguration<K, V> implements CompleteConfiguration<K, V> {
  private static final Factory<Scheduler> DISABLED_SCHEDULER = Scheduler::disabledScheduler;
  private static final Factory<Copier> JAVA_COPIER = JavaSerializationCopier::new;
  private static final Factory<Executor> COMMON_POOL = ForkJoinPool::commonPool;
  private static final Factory<Ticker> SYSTEM_TICKER = Ticker::systemTicker;
  private static final long serialVersionUID = 1L;

  private final MutableConfiguration<K, V> delegate;

  private @Nullable Factory<Weigher<K, V>> weigherFactory;
  private @Nullable Factory<Expiry<K, V>> expiryFactory;

  private Factory<Scheduler> schedulerFactory;
  private Factory<Executor> executorFactory;
  private Factory<Copier> copierFactory;
  private Factory<Ticker> tickerFactory;

  private @Nullable Long refreshAfterWriteNanos;
  private @Nullable Long expireAfterAccessNanos;
  private @Nullable Long expireAfterWriteNanos;
  private @Nullable Long maximumWeight;
  private @Nullable Long maximumSize;
  private boolean nativeStatistics;

  public CaffeineConfiguration() {
    delegate = new MutableConfiguration<>();
    delegate.setStoreByValue(false);
    schedulerFactory = DISABLED_SCHEDULER;
    tickerFactory = SYSTEM_TICKER;
    executorFactory = COMMON_POOL;
    copierFactory = JAVA_COPIER;
  }

  public CaffeineConfiguration(CompleteConfiguration<K, V> configuration) {
    delegate = new MutableConfiguration<>(configuration);
    if (configuration instanceof CaffeineConfiguration<?, ?>) {
      CaffeineConfiguration<K, V> config = (CaffeineConfiguration<K, V>) configuration;
      refreshAfterWriteNanos = config.refreshAfterWriteNanos;
      expireAfterAccessNanos = config.expireAfterAccessNanos;
      expireAfterWriteNanos = config.expireAfterWriteNanos;
      nativeStatistics = config.nativeStatistics;
      schedulerFactory = config.schedulerFactory;
      executorFactory = config.executorFactory;
      expiryFactory = config.expiryFactory;
      copierFactory = config.copierFactory;
      tickerFactory = config.tickerFactory;
      weigherFactory = config.weigherFactory;
      maximumWeight = config.maximumWeight;
      maximumSize = config.maximumSize;
    } else {
      schedulerFactory = DISABLED_SCHEDULER;
      tickerFactory = SYSTEM_TICKER;
      executorFactory = COMMON_POOL;
      copierFactory = JAVA_COPIER;
    }
  }

  @Override
  public Class<K> getKeyType() {
    return delegate.getKeyType();
  }

  @Override
  public Class<V> getValueType() {
    return delegate.getValueType();
  }

  public void setTypes(Class<K> keyType, Class<V> valueType) {
    delegate.setTypes(keyType, valueType);
  }

  @Override
  public Iterable<CacheEntryListenerConfiguration<K, V>> getCacheEntryListenerConfigurations() {
    return delegate.getCacheEntryListenerConfigurations();
  }

  public void addCacheEntryListenerConfiguration(
      CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    delegate.addCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
  }

  public void removeCacheEntryListenerConfiguration(
      CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    delegate.removeCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
  }

  @Override
  public Factory<CacheLoader<K, V>> getCacheLoaderFactory() {
    return delegate.getCacheLoaderFactory();
  }

  public void setCacheLoaderFactory(Factory<? extends CacheLoader<K, V>> factory) {
    delegate.setCacheLoaderFactory(factory);
  }

  @Override
  public Factory<CacheWriter<? super K, ? super V>> getCacheWriterFactory() {
    return delegate.getCacheWriterFactory();
  }

  public @Nullable CacheWriter<K , V> getCacheWriter() {
    if (hasCacheWriter()) {
      @SuppressWarnings("unchecked")
      CacheWriter<K , V> writer = (CacheWriter<K, V>) getCacheWriterFactory().create();
      return writer;
    }
    return null;
  }

  public boolean hasCacheWriter() {
    return getCacheWriterFactory() != null;
  }

  public void setCacheWriterFactory(Factory<? extends CacheWriter<? super K, ? super V>> factory) {
    delegate.setCacheWriterFactory(factory);
  }

  @Override
  public Factory<ExpiryPolicy> getExpiryPolicyFactory() {
    return delegate.getExpiryPolicyFactory();
  }

  public void setExpiryPolicyFactory(Factory<? extends ExpiryPolicy> factory) {
    delegate.setExpiryPolicyFactory(factory);
  }

  @Override
  public boolean isReadThrough() {
    return delegate.isReadThrough();
  }

  public void setReadThrough(boolean isReadThrough) {
    delegate.setReadThrough(isReadThrough);
  }

  @Override
  public boolean isWriteThrough() {
    return delegate.isWriteThrough();
  }

  public void setWriteThrough(boolean isWriteThrough) {
    delegate.setWriteThrough(isWriteThrough);
  }

  @Override
  public boolean isStoreByValue() {
    return delegate.isStoreByValue();
  }

  public void setStoreByValue(boolean isStoreByValue) {
    delegate.setStoreByValue(isStoreByValue);
  }

  public boolean isNativeStatisticsEnabled() {
    return nativeStatistics;
  }

  public void setNativeStatisticsEnabled(boolean enabled) {
    this.nativeStatistics = enabled;
  }

  @Override
  public boolean isStatisticsEnabled() {
    return delegate.isStatisticsEnabled();
  }

  public void setStatisticsEnabled(boolean enabled) {
    delegate.setStatisticsEnabled(enabled);
  }

  @Override
  public boolean isManagementEnabled() {
    return delegate.isManagementEnabled();
  }

  public void setManagementEnabled(boolean enabled) {
    delegate.setManagementEnabled(enabled);
  }

  public Factory<Copier> getCopierFactory() {
    return copierFactory;
  }

  public void setCopierFactory(Factory<Copier> factory) {
    copierFactory = requireNonNull(factory);
  }

  public Factory<Scheduler> getSchedulerFactory() {
    return schedulerFactory;
  }

  public void setSchedulerFactory(Factory<Scheduler> factory) {
    schedulerFactory = requireNonNull(factory);
  }

  public Factory<Ticker> getTickerFactory() {
    return tickerFactory;
  }

  public void setTickerFactory(Factory<Ticker> factory) {
    tickerFactory = requireNonNull(factory);
  }

  public Factory<Executor> getExecutorFactory() {
    return executorFactory;
  }

  public void setExecutorFactory(Factory<Executor> factory) {
    executorFactory = requireNonNull(factory);
  }

  public OptionalLong getRefreshAfterWrite() {
    return (refreshAfterWriteNanos == null)
        ? OptionalLong.empty()
        : OptionalLong.of(refreshAfterWriteNanos);
  }

  public void setRefreshAfterWrite(OptionalLong refreshAfterWriteNanos) {
    this.refreshAfterWriteNanos = refreshAfterWriteNanos.isPresent()
        ? refreshAfterWriteNanos.getAsLong()
        : null;
  }

  public OptionalLong getExpireAfterWrite() {
    return (expireAfterWriteNanos == null)
        ? OptionalLong.empty()
        : OptionalLong.of(expireAfterWriteNanos);
  }

  public void setExpireAfterWrite(OptionalLong expireAfterWriteNanos) {
    this.expireAfterWriteNanos = expireAfterWriteNanos.isPresent()
        ? expireAfterWriteNanos.getAsLong()
        : null;
  }

  public OptionalLong getExpireAfterAccess() {
    return (expireAfterAccessNanos == null)
        ? OptionalLong.empty()
        : OptionalLong.of(expireAfterAccessNanos);
  }

  public void setExpireAfterAccess(OptionalLong expireAfterAccessNanos) {
    this.expireAfterAccessNanos = expireAfterAccessNanos.isPresent()
        ? expireAfterAccessNanos.getAsLong()
        : null;
  }

  public Optional<Factory<Expiry<K, V>>> getExpiryFactory() {
    return Optional.ofNullable(expiryFactory);
  }

  @SuppressWarnings("unchecked")
  public void setExpiryFactory(Optional<Factory<? extends Expiry<K, V>>> factory) {
    expiryFactory = (Factory<Expiry<K, V>>) factory.orElse(null);
  }

  public void setMaximumSize(OptionalLong maximumSize) {
    this.maximumSize = maximumSize.isPresent()
        ? maximumSize.getAsLong()
        : null;
  }

  public OptionalLong getMaximumSize() {
    return (maximumSize == null)
        ? OptionalLong.empty()
        : OptionalLong.of(maximumSize);
  }

  public void setMaximumWeight(OptionalLong maximumWeight) {
    this.maximumWeight = maximumWeight.isPresent()
        ? maximumWeight.getAsLong()
        : null;
  }

  public OptionalLong getMaximumWeight() {
    return (maximumWeight == null)
        ? OptionalLong.empty()
        : OptionalLong.of(maximumWeight);
  }

  public Optional<Factory<Weigher<K, V>>> getWeigherFactory() {
    return Optional.ofNullable(weigherFactory);
  }

  @SuppressWarnings("unchecked")
  public void setWeigherFactory(Optional<Factory<? extends Weigher<K, V>>> factory) {
    weigherFactory = (Factory<Weigher<K, V>>) factory.orElse(null);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof CaffeineConfiguration<?, ?>)) {
      return false;
    }
    CaffeineConfiguration<?, ?> config = (CaffeineConfiguration<?, ?>) o;
    return Objects.equals(refreshAfterWriteNanos, config.refreshAfterWriteNanos)
        && Objects.equals(expireAfterAccessNanos, config.expireAfterAccessNanos)
        && Objects.equals(expireAfterWriteNanos, config.expireAfterWriteNanos)
        && Objects.equals(executorFactory, config.executorFactory)
        && Objects.equals(copierFactory, config.copierFactory)
        && Objects.equals(tickerFactory, config.tickerFactory)
        && Objects.equals(weigherFactory, config.weigherFactory)
        && Objects.equals(maximumWeight, config.maximumWeight)
        && Objects.equals(maximumSize, config.maximumSize)
        && delegate.equals(config.delegate);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }
}
