package com.google.devtools.build.lib.rules.python;

import static net.starlark.java.eval.Starlark.NONE;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.collect.nestedset.Depset;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.BuiltinProvider;
import com.google.devtools.build.lib.packages.Info;
import com.google.devtools.build.lib.starlarkbuildapi.python.PyRuntimeInfoApi;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.Objects;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.Location;

public final class PyRuntimeInfo implements Info, PyRuntimeInfoApi<Artifact> {

  public static final String STARLARK_NAME = "PyRuntimeInfo";

  public static final PyRuntimeInfoProvider PROVIDER = new PyRuntimeInfoProvider();

  private final Location location;
  @Nullable private final PathFragment interpreterPath;
  @Nullable private final Artifact interpreter;
  @Nullable private final Depset files;
  private final PythonVersion pythonVersion;

  private final String stubShebang;

  private PyRuntimeInfo(
      @Nullable Location location,
      @Nullable PathFragment interpreterPath,
      @Nullable Artifact interpreter,
      @Nullable Depset files,
      PythonVersion pythonVersion,
      @Nullable String stubShebang) {
    Preconditions.checkArgument((interpreterPath == null) != (interpreter == null));
    Preconditions.checkArgument((interpreter == null) == (files == null));
    Preconditions.checkArgument(pythonVersion.isTargetValue());
    this.location = location != null ? location : Location.BUILTIN;
    this.files = files;
    this.interpreterPath = interpreterPath;
    this.interpreter = interpreter;
    this.pythonVersion = pythonVersion;
    if (stubShebang != null && !stubShebang.isEmpty()) {
      this.stubShebang = stubShebang;
    } else {
      this.stubShebang = PyRuntimeInfoApi.DEFAULT_STUB_SHEBANG;
    }
  }

  @Override
  public PyRuntimeInfoProvider getProvider() {
    return PROVIDER;
  }

  @Override
  public Location getCreationLocation() {
    return location;
  }

  public static PyRuntimeInfo createForInBuildRuntime(
      Artifact interpreter,
      NestedSet<Artifact> files,
      PythonVersion pythonVersion,
      @Nullable String stubShebang) {
    return new PyRuntimeInfo(
        null,
        null,
        interpreter,
        Depset.of(Artifact.TYPE, files),
        pythonVersion,
        stubShebang);
  }

  public static PyRuntimeInfo createForPlatformRuntime(
      PathFragment interpreterPath, PythonVersion pythonVersion, @Nullable String stubShebang) {
    return new PyRuntimeInfo(
        null,
        interpreterPath,
        null,
        null,
        pythonVersion,
        stubShebang);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof PyRuntimeInfo)) {
      return false;
    }
    PyRuntimeInfo otherInfo = (PyRuntimeInfo) other;
    return (this.interpreterPath.equals(otherInfo.interpreterPath)
        && this.interpreter.equals(otherInfo.interpreter)
        && this.files.equals(otherInfo.files)
        && this.stubShebang.equals(otherInfo.stubShebang));
  }

  @Override
  public int hashCode() {
    return Objects.hash(PyRuntimeInfo.class, interpreterPath, interpreter, files, stubShebang);
  }

  public boolean isInBuild() {
    return getInterpreter() != null;
  }

  @Nullable
  public PathFragment getInterpreterPath() {
    return interpreterPath;
  }

  @Override
  @Nullable
  public String getInterpreterPathString() {
    return interpreterPath == null ? null : interpreterPath.getPathString();
  }

  @Override
  @Nullable
  public Artifact getInterpreter() {
    return interpreter;
  }

  @Override
  public String getStubShebang() {
    return stubShebang;
  }

  @Nullable
  public NestedSet<Artifact> getFiles() {
    try {
      return files == null ? null : files.getSet(Artifact.class);
    } catch (Depset.TypeException ex) {
      throw new IllegalStateException("for files, " + ex.getMessage());
    }
  }

  @Override
  @Nullable
  public Depset getFilesForStarlark() {
    return files;
  }

  public PythonVersion getPythonVersion() {
    return pythonVersion;
  }

  @Override
  public String getPythonVersionForStarlark() {
    return pythonVersion.name();
  }

  public static class PyRuntimeInfoProvider extends BuiltinProvider<PyRuntimeInfo>
      implements PyRuntimeInfoApi.PyRuntimeInfoProviderApi {

    private PyRuntimeInfoProvider() {
      super(STARLARK_NAME, PyRuntimeInfo.class);
    }

    @Override
    public PyRuntimeInfo constructor(
        Object interpreterPathUncast,
        Object interpreterUncast,
        Object filesUncast,
        String pythonVersion,
        String stubShebang,
        StarlarkThread thread)
        throws EvalException {
      String interpreterPath =
          interpreterPathUncast == NONE ? null : (String) interpreterPathUncast;
      Artifact interpreter = interpreterUncast == NONE ? null : (Artifact) interpreterUncast;
      Depset filesDepset = null;
      if (filesUncast != NONE) {
        Depset.cast(filesUncast, Artifact.class, "files");
        filesDepset = (Depset) filesUncast;
      }

      if ((interpreter == null) == (interpreterPath == null)) {
        throw Starlark.errorf(
            "exactly one of the 'interpreter' or 'interpreter_path' arguments must be specified");
      }
      boolean isInBuildRuntime = interpreter != null;
      if (!isInBuildRuntime && filesDepset != null) {
        throw Starlark.errorf("cannot specify 'files' if 'interpreter_path' is given");
      }

      PythonVersion parsedPythonVersion;
      try {
        parsedPythonVersion = PythonVersion.parseTargetValue(pythonVersion);
      } catch (IllegalArgumentException ex) {
        throw Starlark.errorf("illegal value for 'python_version': %s", ex.getMessage());
      }

      Location loc = thread.getCallerLocation();
      if (isInBuildRuntime) {
        if (filesDepset == null) {
          filesDepset = Depset.of(Artifact.TYPE, NestedSetBuilder.emptySet(Order.STABLE_ORDER));
        }
        return new PyRuntimeInfo(
            loc,
            null,
            interpreter,
            filesDepset,
            parsedPythonVersion,
            stubShebang);
      } else {
        return new PyRuntimeInfo(
            loc,
            PathFragment.create(interpreterPath),
            null,
            null,
            parsedPythonVersion,
            stubShebang);
      }
    }
  }
}
