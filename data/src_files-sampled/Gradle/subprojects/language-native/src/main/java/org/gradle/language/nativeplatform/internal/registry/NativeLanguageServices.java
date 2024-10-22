package org.gradle.language.nativeplatform.internal.registry;

import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.language.cpp.internal.NativeDependencyCache;
import org.gradle.language.internal.DefaultNativeComponentFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultCompilationStateCacheFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultIncrementalCompilerBuilder;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CachingCSourceParser;
import org.gradle.language.nativeplatform.internal.toolchains.DefaultToolChainSelector;

public class NativeLanguageServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.add(DefaultCompilationStateCacheFactory.class);
        registration.add(CachingCSourceParser.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(NativeDependencyCache.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.add(DefaultIncrementalCompilerBuilder.class);
        registration.add(DefaultToolChainSelector.class);
        registration.add(DefaultNativeComponentFactory.class);
    }
}
