package org.elasticsearch.common.lucene.store;

import org.apache.lucene.store.IndexOutput;

import java.io.IOException;

public class FilterIndexOutput extends IndexOutput {

    protected final IndexOutput out;

    public FilterIndexOutput(String resourceDescription, IndexOutput out) {
        super(resourceDescription, out.getName());
        this.out = out;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public long getFilePointer() {
        return out.getFilePointer();
    }

    @Override
    public long getChecksum() throws IOException {
        return out.getChecksum();
    }

    @Override
    public void writeByte(byte b) throws IOException {
        out.writeByte(b);
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        out.writeBytes(b, offset, length);
    }
}
