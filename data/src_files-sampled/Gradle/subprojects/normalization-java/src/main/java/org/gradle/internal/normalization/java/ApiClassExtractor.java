package org.gradle.internal.normalization.java;

import org.gradle.internal.hash.Hasher;
import org.gradle.internal.normalization.java.impl.ApiMemberSelector;
import org.gradle.internal.normalization.java.impl.ApiMemberWriter;
import org.gradle.internal.normalization.java.impl.MethodStubbingApiMemberAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class ApiClassExtractor {

    private static final Pattern LOCAL_CLASS_PATTERN = Pattern.compile(".+\\$[0-9]+(?:[\\p{Alnum}_$]+)?$");

    private final Set<String> exportedPackages;
    private final boolean apiIncludesPackagePrivateMembers;
    private final ApiMemberWriterFactory apiMemberWriterFactory;

    public ApiClassExtractor(Set<String> exportedPackages) {
        this(exportedPackages, classWriter -> new ApiMemberWriter(new MethodStubbingApiMemberAdapter(classWriter)));
    }

    public ApiClassExtractor(Set<String> exportedPackages, ApiMemberWriterFactory apiMemberWriterFactory) {
        this.exportedPackages = exportedPackages.isEmpty() ? null : exportedPackages;
        this.apiIncludesPackagePrivateMembers = exportedPackages.isEmpty();
        this.apiMemberWriterFactory = apiMemberWriterFactory;
    }

    private boolean shouldExtractApiClassFrom(ClassReader originalClassReader) {
        if (!ApiMemberSelector.isCandidateApiMember(originalClassReader.getAccess(), apiIncludesPackagePrivateMembers)) {
            return false;
        }
        String originalClassName = originalClassReader.getClassName();
        if (isLocalClass(originalClassName)) {
            return false;
        }
        return exportedPackages == null
            || exportedPackages.contains(packageNameOf(originalClassName));
    }

    public Optional<byte[]> extractApiClassFrom(ClassReader originalClassReader) {
        if (!shouldExtractApiClassFrom(originalClassReader)) {
            return Optional.empty();
        }
        ClassWriter apiClassWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ApiMemberSelector visitor = new ApiMemberSelector(originalClassReader.getClassName(), apiMemberWriterFactory.makeApiMemberWriter(apiClassWriter), apiIncludesPackagePrivateMembers);
        originalClassReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (visitor.isPrivateInnerClass()) {
            return Optional.empty();
        }
        return Optional.of(apiClassWriter.toByteArray());
    }

    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        if (exportedPackages != null) {
            exportedPackages.forEach(hasher::putString);
        }
    }

    private static String packageNameOf(String internalClassName) {
        int packageSeparatorIndex = internalClassName.lastIndexOf('/');
        return packageSeparatorIndex > 0
            ? internalClassName.substring(0, packageSeparatorIndex).replace('/', '.')
            : "";
    }

    private static boolean isLocalClass(String className) {
        return LOCAL_CLASS_PATTERN.matcher(className).matches();
    }
}
