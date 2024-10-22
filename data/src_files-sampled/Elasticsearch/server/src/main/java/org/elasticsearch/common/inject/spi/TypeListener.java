package org.elasticsearch.common.inject.spi;

import org.elasticsearch.common.inject.TypeLiteral;

public interface TypeListener {

    <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter);

}
