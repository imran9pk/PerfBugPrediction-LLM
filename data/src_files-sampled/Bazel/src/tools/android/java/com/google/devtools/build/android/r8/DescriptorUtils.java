package com.google.devtools.build.android.r8;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.devtools.build.android.r8.FileUtils.CLASS_EXTENSION;

import org.objectweb.asm.Type;

public class DescriptorUtils {

  public static final String DESCRIPTOR_PACKAGE_SEPARATOR = "/";
  public static final String JAVA_PACKAGE_SEPARATOR = ".";

  public static boolean isClassDescriptor(String descriptor) {
    return descriptor.length() >= 3
        && descriptor.charAt(0) == 'L'
        && descriptor.charAt(descriptor.length() - 1) == ';'
        && !descriptor.contains(JAVA_PACKAGE_SEPARATOR);
  }

  public static boolean isBinaryName(String name) {
    return name.length() > 0
        && name.charAt(0) != 'L'
        && name.charAt(name.length() - 1) != ';'
        && !name.contains(JAVA_PACKAGE_SEPARATOR);
  }

  public static boolean isCompanionClassBinaryName(String name) {
    checkArgument(isBinaryName(name), "'%s' is not a binary name", name);
    return name.endsWith(R8Utils.INTERFACE_COMPANION_SUFFIX);
  }

  public static String descriptorToBinaryName(String descriptor) {
    checkArgument(isClassDescriptor(descriptor), "'%s' is not a class descriptor", descriptor);
    return Type.getType(descriptor).getInternalName();
  }

  public static String classToBinaryName(Class<?> clazz) {
    return Type.getInternalName(clazz);
  }

  public static String descriptorToClassFileName(String descriptor) {
    return descriptorToBinaryName(descriptor) + CLASS_EXTENSION;
  }

  private DescriptorUtils() {}
}
