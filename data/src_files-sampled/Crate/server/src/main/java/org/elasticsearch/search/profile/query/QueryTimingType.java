package org.elasticsearch.search.profile.query;

import java.util.Locale;

public enum QueryTimingType {
    CREATE_WEIGHT,
    BUILD_SCORER,
    NEXT_DOC,
    ADVANCE,
    MATCH,
    SCORE,
    SHALLOW_ADVANCE,
    COMPUTE_MAX_SCORE;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
