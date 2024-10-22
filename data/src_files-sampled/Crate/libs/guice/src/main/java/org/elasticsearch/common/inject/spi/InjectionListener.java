package org.elasticsearch.common.inject.spi;

public interface InjectionListener<I> {

    void afterInjection(I injectee);
}
