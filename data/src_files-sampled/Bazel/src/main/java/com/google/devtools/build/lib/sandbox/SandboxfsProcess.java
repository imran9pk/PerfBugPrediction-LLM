package com.google.devtools.build.lib.sandbox;

import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;

interface SandboxfsProcess {

  Path getMountPoint();

  boolean isAlive();

  void destroy();

  @FunctionalInterface
  interface SandboxMapper {
    void map(PathFragment path, PathFragment underlyingPath, boolean writable) throws IOException;
  }

  @FunctionalInterface
  interface SandboxCreator {
    void create(SandboxMapper mapper) throws IOException;
  }

  void createSandbox(String name, SandboxCreator creator) throws IOException;

  void destroySandbox(String name) throws IOException;
}
