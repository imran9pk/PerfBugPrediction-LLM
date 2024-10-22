package com.google.devtools.build.lib.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.Immutable;

@Immutable
public abstract class FileType implements Predicate<String> {
  @AutoCodec @VisibleForSerialization
  public static final FileType NO_EXTENSION =
      new FileType() {
        @Override
        public boolean apply(String path) {
          int lastSlashIndex = path.lastIndexOf('/');
          return path.indexOf('.', lastSlashIndex + 1) == -1;
        }
      };

  public static FileType of(final String ext) {
    return new SingletonFileType(ext);
  }

  public static FileType of(final ImmutableList<String> extensions) {
    return new ListFileType(extensions);
  }

  public static FileType of(final String... extensions) {
    return of(ImmutableList.copyOf(extensions));
  }

  @AutoCodec.VisibleForSerialization
  @AutoCodec
  static final class SingletonFileType extends FileType {
    private final String ext;

    @AutoCodec.VisibleForSerialization
    SingletonFileType(String ext) {
      this.ext = ext;
    }

    @Override
    public boolean apply(String path) {
      return path.endsWith(ext);
    }

    @Override
    public ImmutableList<String> getExtensions() {
      return ImmutableList.of(ext);
    }
  }

  @AutoCodec.VisibleForSerialization
  @AutoCodec
  static final class ListFileType extends FileType {
    private final ImmutableList<String> extensions;

    @AutoCodec.VisibleForSerialization
    ListFileType(ImmutableList<String> extensions) {
      this.extensions = Preconditions.checkNotNull(extensions);
    }

    @Override
    public boolean apply(String path) {
      for (int i = 0; i < extensions.size(); i++) {
        if (path.endsWith(extensions.get(i))) {
          return true;
        }
      }
      return false;
    }

    @Override
    public ImmutableList<String> getExtensions() {
      return extensions;
    }

    @Override
    public int hashCode() {
      return extensions.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof ListFileType
          && this.extensions.equals(((ListFileType) obj).extensions));
    }
  }

  @Override
  public String toString() {
    return getExtensions().toString();
  }

  @Override
  public abstract boolean apply(String path);

  public ImmutableList<String> getExtensions() {
    return ImmutableList.of();
  }

  @Deprecated
  public boolean matches(String path) {
    return apply(path);
  }

  public boolean matches(HasFileType item) {
    return apply(item.filePathForFileTypeMatcher());
  }

  public interface HasFileType {
    String filePathForFileTypeMatcher();
  }

  public static <T extends HasFileType> boolean contains(
      final Iterable<T> items, FileType... fileTypes) {
    Preconditions.checkState(fileTypes.length > 0, "Must specify at least one file type");
    final FileTypeSet fileTypeSet = FileTypeSet.of(fileTypes);
    for (T item : items)  {
      if (fileTypeSet.matches(item.filePathForFileTypeMatcher())) {
        return true;
      }
    }
    return false;
  }

  public static <T extends HasFileType> boolean contains(T item, FileType... fileTypes) {
    return FileTypeSet.of(fileTypes).matches(item.filePathForFileTypeMatcher());
  }

  private static <T extends HasFileType> Predicate<T> typeMatchingPredicateFor(
      final FileType matchingType) {
    return item -> matchingType.matches(item.filePathForFileTypeMatcher());
  }

  private static <T extends HasFileType> Predicate<T> typeMatchingPredicateFor(
      final FileTypeSet matchingTypes) {
    return item -> matchingTypes.matches(item.filePathForFileTypeMatcher());
  }

  private static <T extends HasFileType> Predicate<T> typeMatchingPredicateFrom(
      final Predicate<String> fileTypePredicate) {
    return item -> fileTypePredicate.apply(item.filePathForFileTypeMatcher());
  }

  public static <T extends HasFileType> Iterable<T> filter(
      final Iterable<T> items, final Predicate<String> predicate) {
    return Iterables.filter(items, typeMatchingPredicateFrom(predicate));
  }

  public static <T extends HasFileType> Iterable<T> filter(
      final Iterable<T> items, FileType... fileTypes) {
    return filter(items, FileTypeSet.of(fileTypes));
  }

  public static <T extends HasFileType> Iterable<T> filter(
      final Iterable<T> items, FileTypeSet fileTypes) {
    return Iterables.filter(items, typeMatchingPredicateFor(fileTypes));
  }

  public static <T extends HasFileType> Iterable<T> filter(
      final Iterable<T> items, FileType fileType) {
    return Iterables.filter(items, typeMatchingPredicateFor(fileType));
  }

  public static <T extends HasFileType> Iterable<T> except(
      final Iterable<T> items, FileType fileType) {
    return Iterables.filter(items, Predicates.not(typeMatchingPredicateFor(fileType)));
  }

  public static <T extends HasFileType> List<T> filterList(
      final Iterable<T> items, FileType... fileTypes) {
    if (fileTypes.length > 0) {
      return filterList(items, FileTypeSet.of(fileTypes));
    } else {
      return new ArrayList<>();
    }
  }

  public static <T extends HasFileType> List<T> filterList(
      final Iterable<T> items, final FileType fileType) {
    List<T> result = new ArrayList<>();
    for (T item : items)  {
      if (fileType.matches(item.filePathForFileTypeMatcher())) {
        result.add(item);
      }
    }
    return result;
  }

  public static <T extends HasFileType> List<T> filterList(
      final Iterable<T> items, final FileTypeSet fileTypeSet) {
    List<T> result = new ArrayList<>();
    for (T item : items)  {
      if (fileTypeSet.matches(item.filePathForFileTypeMatcher())) {
        result.add(item);
      }
    }
    return result;
  }
}
