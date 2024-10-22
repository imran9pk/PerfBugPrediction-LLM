package com.google.devtools.build.buildjar.genclass;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.buildjar.jarhelper.JarCreator;
import com.google.devtools.build.buildjar.proto.JavaCompilation.CompilationUnit;
import com.google.devtools.build.buildjar.proto.JavaCompilation.Manifest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class GenClass {

  private static void deleteTree(Path directory) throws IOException {
    if (directory.toFile().exists()) {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }

  public static void main(String[] args) throws IOException {
    GenClassOptions options = GenClassOptionsParser.parse(Arrays.asList(args));
    Manifest manifest = readManifest(options.manifest());
    Path tempDir = Files.createTempDirectory("tmp");
    Files.createDirectories(tempDir);
    extractGeneratedClasses(options.classJar(), manifest, tempDir);
    writeOutputJar(tempDir, options);
    deleteTree(tempDir);
  }

  private static Manifest readManifest(Path path) throws IOException {
    Manifest manifest;
    try (InputStream inputStream = Files.newInputStream(path)) {
      manifest = Manifest.parseFrom(inputStream);
    }
    return manifest;
  }

  @VisibleForTesting
  static ImmutableSet<String> getPrefixes(Manifest manifest, Predicate<CompilationUnit> p) {
    return manifest
        .getCompilationUnitList()
        .stream()
        .filter(p)
        .flatMap(unit -> getUnitPrefixes(unit))
        .collect(toImmutableSet());
  }

  private static Stream<String> getUnitPrefixes(CompilationUnit unit) {
    String pkg;
    if (unit.hasPkg()) {
      pkg = unit.getPkg().replace('.', '/') + "/";
    } else {
      pkg = "";
    }
    return unit.getTopLevelList().stream().map(toplevel -> pkg + toplevel);
  }

  private static void extractGeneratedClasses(Path classJar, Manifest manifest, Path tempDir)
      throws IOException {
    ImmutableSet<String> generatedFilePrefixes =
        getPrefixes(manifest, unit -> unit.getGeneratedByAnnotationProcessor());
    ImmutableSet<String> userWrittenFilePrefixes =
        getPrefixes(manifest, unit -> !unit.getGeneratedByAnnotationProcessor());
    try (JarFile jar = new JarFile(classJar.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!name.endsWith(".class")) {
          continue;
        }
        String className = name.substring(0, name.length() - ".class".length());
        if (prefixesContains(generatedFilePrefixes, className)
            || !prefixesContains(userWrittenFilePrefixes, className)) {
          Files.createDirectories(tempDir.resolve(name).getParent());
          Files.copy(jar.getInputStream(entry), tempDir.resolve(name));
        }
      }
    }
  }

  private static boolean prefixesContains(ImmutableSet<String> prefixes, String className) {
    if (prefixes.contains(className)) {
      return true;
    }
    for (int i = className.indexOf('$'); i != -1; i = className.indexOf('$', i + 1)) {
      if (prefixes.contains(className.substring(0, i))) {
        return true;
      }
    }
    return false;
  }

  private static void writeOutputJar(Path tempDir, GenClassOptions options) throws IOException {
    JarCreator output = new JarCreator(options.outputJar().toString());
    output.setCompression(true);
    output.setNormalize(true);
    output.addDirectory(tempDir);
    output.execute();
  }
}
