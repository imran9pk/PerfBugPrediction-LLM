package org.gradle.caching.internal.tasks;

import org.iq80.snappy.SnappyFramedInputStream;
import org.iq80.snappy.SnappyFramedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class SnappyDainPacker implements Packer {
    private final Packer delegate;

    public SnappyDainPacker(Packer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void pack(List<DataSource> inputs, DataTarget output) throws IOException {
        delegate.pack(inputs, new DelegatingDataTarget(output) {
            @Override
            public OutputStream openOutput() throws IOException {
                return new SnappyFramedOutputStream(super.openOutput());
            }
        });
    }

    @Override
    public void unpack(DataSource input, DataTargetFactory targetFactory) throws IOException {
        delegate.unpack(new DelegatingDataSource(input) {
            @Override
            public InputStream openInput() throws IOException {
                return new SnappyFramedInputStream(super.openInput(), true);
            }
        }, targetFactory);
    }
}
