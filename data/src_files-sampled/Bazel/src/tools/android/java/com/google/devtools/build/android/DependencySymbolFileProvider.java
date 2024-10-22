package com.google.devtools.build.android;

import com.android.builder.dependency.SymbolFileProvider;
import com.google.common.base.Preconditions;
import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

class DependencySymbolFileProvider implements SymbolFileProvider {

  private final File symbolFile;
  private final File manifest;

  public DependencySymbolFileProvider(File symbolFile, File manifest) {
    this.symbolFile = symbolFile;
    this.manifest = manifest;
  }

  public static DependencySymbolFileProvider valueOf(String text) {
    return valueOf(text, FileSystems.getDefault());
  }

  @Override
  public File getSymbolFile() {
    return symbolFile;
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @Override
  public File getManifest() {
    return manifest;
  }

  private static DependencySymbolFileProvider valueOf(String text, FileSystem fileSystem) {
    int separatorIndex = text.indexOf(',');
    if (separatorIndex == -1) {
      separatorIndex = text.indexOf(':');
    }
    if (separatorIndex == -1) {
      throw new IllegalArgumentException(text + " is not in the format " + commandlineFormat(""));
    }
    return new DependencySymbolFileProvider(
        getFile(text.substring(0, separatorIndex), fileSystem),
        getFile(text.substring(separatorIndex + 1), fileSystem));
  }

  private static File getFile(String pathString, FileSystem fileSystem) {
    Preconditions.checkArgument(!pathString.trim().isEmpty());
    return exists(fileSystem.getPath(pathString)).toFile();
  }

  private static Path exists(Path path) {
    if (!Files.exists(path)) {
      throw new IllegalArgumentException(path + " does not exist");
    }
    return path;
  }

  public static String commandlineFormat(String libNum) {
    return String.format("lib%s/R.txt,lib%s/AndroidManifest.xml", libNum, libNum);
  }

  @Override
  public String toString() {
    return String.format("%s, %s", symbolFile, manifest);
  }
}
