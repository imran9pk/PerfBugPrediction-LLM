package com.google.devtools.build.lib.vfs;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.CommandLineItem;
import com.google.devtools.build.lib.skyframe.serialization.DeserializationContext;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodec;
import com.google.devtools.build.lib.skyframe.serialization.SerializationContext;
import com.google.devtools.build.lib.skyframe.serialization.SerializationException;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.SerializationConstant;
import com.google.devtools.build.lib.util.FileType;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Nullable;

public final class PathFragment
    implements Comparable<PathFragment>,
        FileType.HasFileType,
        CommandLineItem {
  private static final OsPathPolicy OS = OsPathPolicy.getFilePathOs();

  @SerializationConstant public static final PathFragment EMPTY_FRAGMENT = new PathFragment("", 0);
  public static final char SEPARATOR_CHAR = '/';
  private static final char ADDITIONAL_SEPARATOR_CHAR = OS.additionalSeparator();
  public static final int INVALID_SEGMENT = -1;

  private final String normalizedPath;
  private final int driveStrLength; public static PathFragment create(String path) {
    if (path.isEmpty()) {
      return EMPTY_FRAGMENT;
    }
    int normalizationLevel = OS.needsToNormalize(path);
    String normalizedPath =
        normalizationLevel != OsPathPolicy.NORMALIZED
            ? OS.normalize(path, normalizationLevel)
            : path;
    int driveStrLength = OS.getDriveStrLength(normalizedPath);
    return new PathFragment(normalizedPath, driveStrLength);
  }

  public static PathFragment createAlreadyNormalized(String normalizedPath) {
    int driveStrLength = OS.getDriveStrLength(normalizedPath);
    return createAlreadyNormalized(normalizedPath, driveStrLength);
  }

  static PathFragment createAlreadyNormalized(String normalizedPath, int driveStrLength) {
    if (normalizedPath.isEmpty()) {
      return EMPTY_FRAGMENT;
    }
    return new PathFragment(normalizedPath, driveStrLength);
  }

  private PathFragment(String normalizedPath, int driveStrLength) {
    this.normalizedPath = Preconditions.checkNotNull(normalizedPath);
    this.driveStrLength = driveStrLength;
  }

  public String getPathString() {
    return normalizedPath;
  }

  public boolean isEmpty() {
    return normalizedPath.isEmpty();
  }

  int getDriveStrLength() {
    return driveStrLength;
  }

  public String getBaseName() {
    int lastSeparator = normalizedPath.lastIndexOf(SEPARATOR_CHAR);
    return lastSeparator < driveStrLength
        ? normalizedPath.substring(driveStrLength)
        : normalizedPath.substring(lastSeparator + 1);
  }

  public PathFragment getRelative(PathFragment other) {
    Preconditions.checkNotNull(other);
    String otherStr = other.normalizedPath;
    return getRelative(otherStr, other.getDriveStrLength(), OS.needsToNormalizeSuffix(otherStr));
  }

  public static boolean isNormalizedRelativePath(String path) {
    int driveStrLength = OS.getDriveStrLength(path);
    int normalizationLevel = OS.needsToNormalize(path);
    return driveStrLength == 0 && normalizationLevel == OsPathPolicy.NORMALIZED;
  }

  public static boolean containsSeparator(String path) {
    return path.lastIndexOf(SEPARATOR_CHAR) != -1;
  }

  public PathFragment getRelative(String other) {
    Preconditions.checkNotNull(other);
    return getRelative(other, OS.getDriveStrLength(other), OS.needsToNormalize(other));
  }

  private PathFragment getRelative(String other, int otherDriveStrLength, int normalizationLevel) {
    if (normalizedPath.isEmpty()) {
      return create(other);
    }
    if (other.isEmpty()) {
      return this;
    }
    if (otherDriveStrLength > 0) {
      String normalizedPath =
          normalizationLevel != OsPathPolicy.NORMALIZED
              ? OS.normalize(other, normalizationLevel)
              : other;
      return new PathFragment(normalizedPath, otherDriveStrLength);
    }
    String newPath;
    if (normalizedPath.length() == driveStrLength) {
      newPath = normalizedPath + other;
    } else {
      newPath = normalizedPath + '/' + other;
    }
    newPath =
        normalizationLevel != OsPathPolicy.NORMALIZED
            ? OS.normalize(newPath, normalizationLevel)
            : newPath;
    return new PathFragment(newPath, driveStrLength);
  }

  public PathFragment getChild(String baseName) {
    checkBaseName(baseName);
    String newPath;
    if (normalizedPath.length() == driveStrLength) {
      newPath = normalizedPath + baseName;
    } else {
      newPath = normalizedPath + '/' + baseName;
    }
    return new PathFragment(newPath, driveStrLength);
  }

  @Nullable
  public PathFragment getParentDirectory() {
    int lastSeparator = normalizedPath.lastIndexOf(SEPARATOR_CHAR);

    if (driveStrLength > 0) {
      if (lastSeparator < driveStrLength) {
        if (normalizedPath.length() > driveStrLength) {
          String newPath = normalizedPath.substring(0, driveStrLength);
          return new PathFragment(newPath, driveStrLength);
        } else {
          return null;
        }
      }
    } else {
      if (lastSeparator == -1) {
        if (!normalizedPath.isEmpty()) {
          return EMPTY_FRAGMENT;
        } else {
          return null;
        }
      }
    }
    String newPath = normalizedPath.substring(0, lastSeparator);
    return new PathFragment(newPath, driveStrLength);
  }

  public PathFragment relativeTo(PathFragment base) {
    Preconditions.checkNotNull(base);
    if (isAbsolute() != base.isAbsolute()) {
      throw new IllegalArgumentException(
          "Cannot relativize an absolute and a non-absolute path pair");
    }
    String basePath = base.normalizedPath;
    if (!OS.startsWith(normalizedPath, basePath)) {
      throw new IllegalArgumentException(
          String.format("Path '%s' is not under '%s', cannot relativize", this, base));
    }
    int bn = basePath.length();
    if (bn == 0) {
      return this;
    }
    if (normalizedPath.length() == bn) {
      return EMPTY_FRAGMENT;
    }
    final int lastSlashIndex;
    if (basePath.charAt(bn - 1) == '/') {
      lastSlashIndex = bn - 1;
    } else {
      lastSlashIndex = bn;
    }
    if (normalizedPath.charAt(lastSlashIndex) != '/') {
      throw new IllegalArgumentException(
          String.format("Path '%s' is not under '%s', cannot relativize", this, base));
    }
    String newPath = normalizedPath.substring(lastSlashIndex + 1);
    return new PathFragment(newPath, 0 ;
  }

  public PathFragment relativeTo(String base) {
    return relativeTo(PathFragment.create(base));
  }

  public boolean startsWith(PathFragment other) {
    Preconditions.checkNotNull(other);
    if (other.normalizedPath.length() > normalizedPath.length()) {
      return false;
    }
    if (driveStrLength != other.driveStrLength) {
      return false;
    }
    if (!OS.startsWith(normalizedPath, other.normalizedPath)) {
      return false;
    }
    return normalizedPath.length() == other.normalizedPath.length()
        || other.normalizedPath.length() == driveStrLength
        || normalizedPath.charAt(other.normalizedPath.length()) == SEPARATOR_CHAR;
  }

  public boolean endsWith(PathFragment other) {
    Preconditions.checkNotNull(other);
    if (other.normalizedPath.length() > normalizedPath.length()) {
      return false;
    }
    if (other.isAbsolute()) {
      return this.equals(other);
    }
    if (!OS.endsWith(normalizedPath, other.normalizedPath)) {
      return false;
    }
    return normalizedPath.length() == other.normalizedPath.length()
        || other.normalizedPath.length() == 0
        || normalizedPath.charAt(normalizedPath.length() - other.normalizedPath.length() - 1)
            == SEPARATOR_CHAR;
  }

  public boolean isAbsolute() {
    return driveStrLength > 0;
  }

  public static boolean isAbsolute(String path) {
    return OS.getDriveStrLength(path) > 0;
  }

  @Override
  public String toString() {
    return normalizedPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return OS.equals(this.normalizedPath, ((PathFragment) o).normalizedPath);
  }

  @Override
  public int hashCode() {
    return OS.hash(this.normalizedPath);
  }

  @Override
  public int compareTo(PathFragment o) {
    return OS.compare(this.normalizedPath, o.normalizedPath);
  }

  public int segmentCount() {
    int n = normalizedPath.length();
    int segmentCount = 0;
    int i;
    for (i = driveStrLength; i < n; ++i) {
      if (normalizedPath.charAt(i) == SEPARATOR_CHAR) {
        ++segmentCount;
      }
    }
    if (i > driveStrLength) {
      ++segmentCount;
    }
    return segmentCount;
  }

  public String getSegment(int index) {
    int n = normalizedPath.length();
    int segmentCount = 0;
    int i;
    for (i = driveStrLength; i < n && segmentCount < index; ++i) {
      if (normalizedPath.charAt(i) == SEPARATOR_CHAR) {
        ++segmentCount;
      }
    }
    int starti = i;
    for (; i < n; ++i) {
      if (normalizedPath.charAt(i) == SEPARATOR_CHAR) {
        break;
      }
    }
    if (i > driveStrLength) {
      ++segmentCount;
    }
    int endi = i;
    if (index < 0 || index >= segmentCount) {
      throw new IllegalArgumentException("Illegal segment index: " + index);
    }
    return normalizedPath.substring(starti, endi);
  }

  public PathFragment subFragment(int beginIndex, int endIndex) {
    if (beginIndex < 0 || beginIndex > endIndex) {
      throw new IndexOutOfBoundsException(
          String.format("path: %s, beginIndex: %d endIndex: %d", toString(), beginIndex, endIndex));
    }
    return subFragmentImpl(beginIndex, endIndex);
  }

  public PathFragment subFragment(int beginIndex) {
    if (beginIndex < 0) {
      throw new IndexOutOfBoundsException(
          String.format("path: %s, beginIndex: %d", toString(), beginIndex));
    }
    return subFragmentImpl(beginIndex, -1);
  }

  private PathFragment subFragmentImpl(int beginIndex, int endIndex) {
    int n = normalizedPath.length();
    int segmentIndex = 0;
    int i;
    for (i = driveStrLength; i < n && segmentIndex < beginIndex; ++i) {
      if (normalizedPath.charAt(i) == SEPARATOR_CHAR) {
        ++segmentIndex;
      }
    }
    int starti = i;
    if (segmentIndex < endIndex) {
      for (; i < n; ++i) {
        if (normalizedPath.charAt(i) == SEPARATOR_CHAR) {
          ++segmentIndex;
          if (segmentIndex == endIndex) {
            break;
          }
        }
      }
    } else if (endIndex == -1) {
      i = normalizedPath.length();
    }
    int endi = i;
    if (i == n && i > driveStrLength) {
      ++segmentIndex;
    }
    if (beginIndex > segmentIndex || endIndex > segmentIndex) {
      throw new IndexOutOfBoundsException(
          String.format("path: %s, beginIndex: %d endIndex: %d", toString(), beginIndex, endIndex));
    }
    int driveStrLength = 0;
    if (beginIndex == 0) {
      starti = 0;
      driveStrLength = this.driveStrLength;
      endi = Math.max(endi, driveStrLength);
    }
    return new PathFragment(normalizedPath.substring(starti, endi), driveStrLength);
  }

  String[] segments() {
    int segmentCount = segmentCount();
    String[] segments = new String[segmentCount];
    int segmentIndex = 0;
    int nexti = driveStrLength;
    int n = normalizedPath.length();
    for (int i = driveStrLength; i < n; ++i) {
      if (normalizedPath.charAt(i) == SEPARATOR_CHAR) {
        segments[segmentIndex++] = normalizedPath.substring(nexti, i);
        nexti = i + 1;
      }
    }
    if (nexti < n) {
      segments[segmentIndex] = normalizedPath.substring(nexti);
    }
    return segments;
  }

  public ImmutableList<String> getSegments() {
    return ImmutableList.copyOf(segments());
  }

  public int getFirstSegment(Set<String> values) {
    String[] segments = segments();
    for (int i = 0; i < segments.length; ++i) {
      if (values.contains(segments[i])) {
        return i;
      }
    }
    return INVALID_SEGMENT;
  }

  public String getSafePathString() {
    return !normalizedPath.isEmpty() ? normalizedPath : ".";
  }

  public String getCallablePathString() {
    if (isAbsolute()) {
      return normalizedPath;
    } else if (normalizedPath.isEmpty()) {
      return ".";
    } else if (normalizedPath.indexOf(SEPARATOR_CHAR) == -1) {
      return "." + SEPARATOR_CHAR + normalizedPath;
    } else {
      return normalizedPath;
    }
  }

  public String getFileExtension() {
    int n = normalizedPath.length();
    for (int i = n - 1; i > driveStrLength; --i) {
      char c = normalizedPath.charAt(i);
      if (c == '.') {
        return normalizedPath.substring(i + 1, n);
      } else if (c == SEPARATOR_CHAR) {
        break;
      }
    }
    return "";
  }

  public PathFragment replaceName(String newName) {
    PathFragment parent = getParentDirectory();
    return parent != null ? parent.getRelative(newName) : null;
  }

  public String getDriveStr() {
    Preconditions.checkArgument(isAbsolute());
    return normalizedPath.substring(0, driveStrLength);
  }

  public PathFragment toRelative() {
    Preconditions.checkArgument(isAbsolute());
    return new PathFragment(normalizedPath.substring(driveStrLength), 0);
  }

  public boolean containsUplevelReferences() {
    return normalizedPath.startsWith("..");
  }

  public static boolean isNormalized(String path) {
    return isNormalizedImpl(path, true);
  }

  public static boolean containsUplevelReferences(String path) {
    return !isNormalizedImpl(path, false);
  }

  private enum NormalizedImplState {
    Base, Separator, Dot, DotDot, 

  private static boolean isNormalizedImpl(String path, boolean lookForSameLevelReferences) {
    NormalizedImplState state = NormalizedImplState.Separator;
    int n = path.length();
    for (int i = 0; i < n; ++i) {
      char c = path.charAt(i);
      boolean isSeparator = OS.isSeparator(c);
      switch (state) {
        case Base:
          if (isSeparator) {
            state = NormalizedImplState.Separator;
          } else {
            state = NormalizedImplState.Base;
          }
          break;
        case Separator:
          if (isSeparator) {
            state = NormalizedImplState.Separator;
          } else if (c == '.') {
            state = NormalizedImplState.Dot;
          } else {
            state = NormalizedImplState.Base;
          }
          break;
        case Dot:
          if (isSeparator) {
            if (lookForSameLevelReferences) {
              return false;
            }
            state = NormalizedImplState.Separator;
          } else if (c == '.') {
            state = NormalizedImplState.DotDot;
          } else {
            state = NormalizedImplState.Base;
          }
          break;
        case DotDot:
          if (isSeparator) {
            return false;
          } else {
            state = NormalizedImplState.Base;
          }
          break;
        default:
          throw new IllegalStateException("Unhandled state: " + state);
      }
    }
    switch (state) {
      case Dot:
        if (lookForSameLevelReferences) {
          return false;
        }
        break;
      case DotDot:
        return false;
      default:
    }
    return true;
  }

  public static void checkAllPathsAreUnder(
      Iterable<PathFragment> paths, PathFragment startingWithPath) {
    for (PathFragment path : paths) {
      Preconditions.checkArgument(
          !path.equals(startingWithPath) && path.startsWith(startingWithPath),
          "%s is not beneath %s",
          path,
          startingWithPath);
    }
  }

  @Override
  public String filePathForFileTypeMatcher() {
    return normalizedPath;
  }

  @Override
  public String expandToCommandLine() {
    return getPathString();
  }

  private static void checkBaseName(String baseName) {
    if (baseName.length() == 0) {
      throw new IllegalArgumentException("Child must not be empty string ('')");
    }
    if (baseName.equals(".") || baseName.equals("..")) {
      throw new IllegalArgumentException("baseName must not be '" + baseName + "'");
    }
    try {
      checkSeparators(baseName);
    } catch (InvalidBaseNameException e) {
      throw new IllegalArgumentException("baseName " + e.getMessage() + ": '" + baseName + "'", e);
    }
  }

  public static void checkSeparators(String baseName) throws InvalidBaseNameException {
    if (baseName.indexOf(SEPARATOR_CHAR) != -1) {
      throw new InvalidBaseNameException("must not contain " + SEPARATOR_CHAR);
    }
    if (ADDITIONAL_SEPARATOR_CHAR != 0) {
      if (baseName.indexOf(ADDITIONAL_SEPARATOR_CHAR) != -1) {
        throw new InvalidBaseNameException("must not contain " + ADDITIONAL_SEPARATOR_CHAR);
      }
    }
  }

  public static class InvalidBaseNameException extends Exception {
    private InvalidBaseNameException(String message) {
      super(message);
    }
  }

  @SuppressWarnings("unused") private static class Codec implements ObjectCodec<PathFragment> {
    @Override
    public Class<? extends PathFragment> getEncodedClass() {
      return PathFragment.class;
    }

    @Override
    public void serialize(
        SerializationContext context, PathFragment obj, CodedOutputStream codedOut)
        throws SerializationException, IOException {
      context.serialize(obj.normalizedPath, codedOut);
    }

    @Override
    public PathFragment deserialize(DeserializationContext context, CodedInputStream codedIn)
        throws SerializationException, IOException {
      return PathFragment.createAlreadyNormalized(context.deserialize(codedIn));
    }
  }
}
