package com.google.devtools.build.lib.vfs;

import java.io.IOException;
import java.io.OutputStream;

public abstract class ReadonlyFileSystemWithCustomStat extends AbstractFileSystemWithCustomStat {
  public ReadonlyFileSystemWithCustomStat(DigestHashFunction hashFunction) {
    super(hashFunction);
  }

  protected IOException modificationException() {
    String longname = this.getClass().getName();
    String shortname = longname.substring(longname.lastIndexOf('.') + 1);
    return new IOException(
        shortname + " does not support mutating operations");
  }

  @Override
  protected OutputStream getOutputStream(Path path, boolean append) throws IOException {
    throw modificationException();
  }

  @Override
  protected void setReadable(Path path, boolean readable) throws IOException {
    throw modificationException();
  }

  @Override
  public void setWritable(Path path, boolean writable) throws IOException {
    throw modificationException();
  }

  @Override
  protected void setExecutable(Path path, boolean executable) {
    throw new UnsupportedOperationException("setExecutable");
  }

  @Override
  public boolean supportsModifications(Path path) {
    return false;
  }

  @Override
  public boolean supportsSymbolicLinksNatively(Path path) {
    return false;
  }

  @Override
  public boolean supportsHardLinksNatively(Path path) {
    return false;
  }

  @Override
  public boolean isFilePathCaseSensitive() {
    return true;
  }

  @Override
  public boolean createDirectory(Path path) throws IOException {
    throw modificationException();
  }

  @Override
  public void createDirectoryAndParents(Path path) throws IOException {
    throw modificationException();
  }

  @Override
  protected void createSymbolicLink(Path linkPath, PathFragment targetFragment) throws IOException {
    throw modificationException();
  }

  @Override
  protected void createFSDependentHardLink(Path linkPath, Path originalPath)
      throws IOException {
    throw modificationException();
  }

  @Override
  public void renameTo(Path sourcePath, Path targetPath) throws IOException {
    throw modificationException();
  }

  @Override
  public boolean delete(Path path) throws IOException {
    throw modificationException();
  }

  @Override
  public void setLastModifiedTime(Path path, long newTime) throws IOException {
    throw modificationException();
  }
}

