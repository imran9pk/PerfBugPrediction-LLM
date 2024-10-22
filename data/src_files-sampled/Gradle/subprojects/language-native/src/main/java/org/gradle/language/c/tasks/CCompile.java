package org.gradle.language.c.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.language.c.internal.DefaultCCompileSpec;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

@Incubating
@CacheableTask
public class CCompile extends AbstractNativeSourceCompileTask {
    @Override
    protected NativeCompileSpec createCompileSpec() {
        return new DefaultCCompileSpec();
    }
}
