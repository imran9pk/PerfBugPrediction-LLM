package com.google.devtools.build.lib.vfs;

import com.google.common.base.Preconditions;
import java.io.IOException;
import javax.annotation.Nullable;

public class FileStatusWithDigestAdapter implements FileStatusWithDigest {
  private final FileStatus stat;

  public static FileStatusWithDigest adapt(FileStatus stat) {
    return stat == null ? null : new FileStatusWithDigestAdapter(stat);
  }

  private FileStatusWithDigestAdapter(FileStatus stat) {
    this.stat = Preconditions.checkNotNull(stat);
  }

  @Nullable
  @Override
  public byte[] getDigest() {
    return null;
  }

  @Override
  public boolean isFile() {
    return stat.isFile();
  }

  @Override
  public boolean isSpecialFile() {
    return stat.isSpecialFile();
  }

  @Override
  public boolean isDirectory() {
    return stat.isDirectory();
  }

  @Override
  public boolean isSymbolicLink() {
    return stat.isSymbolicLink();
  }

  @Override
  public long getSize() throws IOException {
    return stat.getSize();
  }

  @Override
  public long getLastModifiedTime() throws IOException {
    return stat.getLastModifiedTime();
  }

  @Override
  public long getLastChangeTime() throws IOException {
    return stat.getLastChangeTime();
  }

  @Override
  public long getNodeId() throws IOException {
    return stat.getNodeId();
  }
}
