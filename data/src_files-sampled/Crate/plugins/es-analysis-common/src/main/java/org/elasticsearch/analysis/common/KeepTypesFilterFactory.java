package org.elasticsearch.analysis.common;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.TypeTokenFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.TokenFilterFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class KeepTypesFilterFactory extends AbstractTokenFilterFactory {
    private final Set<String> keepTypes;
    private final KeepTypesMode includeMode;
    static final String KEEP_TYPES_KEY = "types";
    static final String KEEP_TYPES_MODE_KEY = "mode";

    enum KeepTypesMode {
        INCLUDE, EXCLUDE;

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        private static KeepTypesMode fromString(String modeString) {
            String lc = modeString.toLowerCase(Locale.ROOT);
            if (lc.equals("include")) {
                return INCLUDE;
            } else if (lc.equals("exclude")) {
                return EXCLUDE;
            } else {
                throw new IllegalArgumentException("`keep_types` tokenfilter mode can only be [" + KeepTypesMode.INCLUDE + "] or ["
                        + KeepTypesMode.EXCLUDE + "] but was [" + modeString + "].");
            }
        }
    }

    KeepTypesFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        final List<String> arrayKeepTypes = settings.getAsList(KEEP_TYPES_KEY, null);
        if ((arrayKeepTypes == null)) {
            throw new IllegalArgumentException("keep_types requires `" + KEEP_TYPES_KEY + "` to be configured");
        }
        this.includeMode = KeepTypesMode.fromString(settings.get(KEEP_TYPES_MODE_KEY, "include"));
        this.keepTypes = new HashSet<>(arrayKeepTypes);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new TypeTokenFilter(tokenStream, keepTypes, includeMode == KeepTypesMode.INCLUDE);
    }
}
