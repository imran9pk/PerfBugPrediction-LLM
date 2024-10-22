package org.elasticsearch.xpack.ml.process;

import java.io.IOException;
import java.io.InputStream;

public interface StateProcessor {

    void process(InputStream in) throws IOException;
}
