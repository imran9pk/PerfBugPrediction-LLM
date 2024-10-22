package org.dcache.chimera;

import java.io.Closeable;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface DirectoryStreamB<T> extends Closeable, Iterable<T> {

    interface Filter<T> {

        boolean accept(T entry);
    }

    @Override
    Iterator<T> iterator();

    default Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}