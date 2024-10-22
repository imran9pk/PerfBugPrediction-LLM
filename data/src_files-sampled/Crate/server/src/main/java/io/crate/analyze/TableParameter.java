package io.crate.analyze;

import org.elasticsearch.common.settings.Settings;

import java.util.Map;

public class TableParameter {

    private final Settings.Builder settingsBuilder;
    private final Settings.Builder mappingsBuilder;

    private Settings settings;
    private Map<String, Object> mappings;

    public TableParameter() {
        settingsBuilder = Settings.builder();
        mappingsBuilder = Settings.builder();
    }

    public Settings.Builder settingsBuilder() {
        return settingsBuilder;
    }

    public Settings settings() {
        if (settings == null) {
            settings = settingsBuilder.build();
        }
        return settings;
    }

    public Settings.Builder mappingsBuilder() {
        return mappingsBuilder;
    }

    public Map<String, Object> mappings() {
        if (mappings == null) {
            mappings = mappingsBuilder.build().getAsStructuredMap();
        }
        return mappings;
    }
}
