package io.crate.metadata.settings.session;

import io.crate.planner.optimizer.LoadedRules;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.multibindings.Multibinder;

public class SessionSettingModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SessionSettingRegistry.class).asEagerSingleton();
        var sessionSettingProviderBinder = Multibinder.newSetBinder(binder(), SessionSettingProvider.class);
        sessionSettingProviderBinder.addBinding().to(LoadedRules.class);
    }
}
