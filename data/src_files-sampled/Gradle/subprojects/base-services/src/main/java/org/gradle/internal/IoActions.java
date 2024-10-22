package org.gradle.internal;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

public abstract class IoActions {

    public static void writeTextFile(File output, String encoding, Action<? super BufferedWriter> action) {
        createTextFileWriteAction(output, encoding).execute(action);
    }

    public static void writeTextFile(File output, Action<? super BufferedWriter> action) {
        writeTextFile(output, Charset.defaultCharset().name(), action);
    }

    public static Action<Action<? super BufferedWriter>> createTextFileWriteAction(File output, String encoding) {
        return new TextFileWriterIoAction(output, encoding);
    }

    public static <T extends Closeable> void withResource(T resource, Action<? super T> action) {
        try {
            action.execute(resource);
        } catch (Throwable t) {
            closeQuietly(resource);
            throw UncheckedException.throwAsUncheckedException(t);
        }
        uncheckedClose(resource);
    }

    public static <T extends Closeable, R> R withResource(T resource, Transformer<R, ? super T> action) {
        R result;
        try {
            result = action.transform(resource);
        } catch (Throwable t) {
            closeQuietly(resource);
            throw UncheckedException.throwAsUncheckedException(t);
        }
        uncheckedClose(resource);
        return result;
    }

    public static void uncheckedClose(@Nullable Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void closeQuietly(@Nullable Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (IOException e) {
            }
    }

    private static class TextFileWriterIoAction implements Action<Action<? super BufferedWriter>> {
        private final File file;
        private final String encoding;

        private TextFileWriterIoAction(File file, String encoding) {
            this.file = file;
            this.encoding = encoding;
        }

        @Override
        public void execute(Action<? super BufferedWriter> action) {
            try {
                File parentFile = file.getParentFile();
                if (parentFile != null) {
                    if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                        throw new IOException(String.format("Unable to create directory '%s'", parentFile));
                    }
                }
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
                try {
                    action.execute(writer);
                } finally {
                    writer.close();
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Could not write to file '%s'.", file), e);
            }
        }
    }

}
