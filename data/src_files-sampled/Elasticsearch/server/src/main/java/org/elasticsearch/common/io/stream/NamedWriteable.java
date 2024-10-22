package org.elasticsearch.common.io.stream;

public interface NamedWriteable extends Writeable {

    String getWriteableName();
}
