package org.gradle.internal.buildoption;

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import javax.annotation.Nullable;
import java.util.Map;

public interface BuildOption<T> {

    @Nullable
    String getGradleProperty();

    void applyFromProperty(Map<String, String> properties, T settings);

    void configure(CommandLineParser parser);

    void applyFromCommandLine(ParsedCommandLine options, T settings);

    abstract class Value<T> {
        public abstract boolean isExplicit();

        public abstract T get();

        public static <T> Value<T> defaultValue(final T value) {
            return new Value<T>() {
                @Override
                public boolean isExplicit() {
                    return false;
                }

                @Override
                public T get() {
                    return value;
                }
            };
        }

        public static <T> Value<T> value(final T value) {
            return new Value<T>() {
                @Override
                public boolean isExplicit() {
                    return true;
                }

                @Override
                public T get() {
                    return value;
                }
            };
        }
    }

}
