package com.palantir.atlasdb.autobatch;

import java.util.Map;
import java.util.Set;

public interface CoalescingRequestFunction<REQUEST, RESPONSE> {
    Map<REQUEST, RESPONSE> apply(Set<REQUEST> request);
}
