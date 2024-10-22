package com.google.javascript.jscomp.deps;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DependencyInfo {

  @AutoValue
  @Immutable
  abstract class Require {
    public static final Require BASE = googRequireSymbol("goog");

    public enum Type {
      GOOG_REQUIRE_SYMBOL,
      ES6_IMPORT,
      PARSED_FROM_DEPS,
      COMMON_JS,
      COMPILER_MODULE
    }

    public static ImmutableList<String> asSymbolList(Iterable<Require> requires) {
      return stream(requires).map(Require::getSymbol).collect(toImmutableList());
    }

    public static Require googRequireSymbol(String symbol) {
      return builder()
          .setRawText(symbol)
          .setSymbol(symbol)
          .setType(Type.GOOG_REQUIRE_SYMBOL)
          .build();
    }

    public static Require es6Import(String symbol, String rawPath) {
      return builder().setRawText(rawPath).setSymbol(symbol).setType(Type.ES6_IMPORT).build();
    }

    public static Require commonJs(String symbol, String rawPath) {
      return builder().setRawText(rawPath).setSymbol(symbol).setType(Type.COMMON_JS).build();
    }

    public static Require compilerModule(String symbol) {
      return builder().setRawText(symbol).setSymbol(symbol).setType(Type.COMPILER_MODULE).build();
    }

    public static Require parsedFromDeps(String symbol) {
      return builder().setRawText(symbol).setSymbol(symbol).setType(Type.PARSED_FROM_DEPS).build();
    }

    private static Builder builder() {
      return new AutoValue_DependencyInfo_Require.Builder();
    }

    protected abstract Builder toBuilder();

    public Require withSymbol(String symbol) {
      return toBuilder().setSymbol(symbol).build();
    }

    public abstract String getSymbol();

    public abstract String getRawText();

    public abstract Type getType();

    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setType(Type value);

      public abstract Builder setRawText(String rawText);

      public abstract Builder setSymbol(String value);

      public abstract Require build();
    }
  }

  String getName();

  String getPathRelativeToClosureBase();

  ImmutableList<String> getProvides();

  ImmutableList<Require> getRequires();

  ImmutableList<String> getRequiredSymbols();

  ImmutableList<String> getTypeRequires();

  ImmutableMap<String, String> getLoadFlags();

  boolean isModule();

  boolean getHasExternsAnnotation();

  boolean getHasNoCompileAnnotation();

  abstract class Base implements DependencyInfo {
    @Override public boolean isModule() {
      return "goog".equals(getLoadFlags().get("module"));
    }

    @Override
    public ImmutableList<String> getRequiredSymbols() {
      return Require.asSymbolList(getRequires());
    }
  }

  class Util {
    private Util() {}

    public static void writeAddDependency(Appendable out, DependencyInfo info) throws IOException {
      out.append("goog.addDependency('")
          .append(info.getPathRelativeToClosureBase())
          .append("', ");
      writeJsArray(out, info.getProvides());
      out.append(", ");
      writeJsArray(out, Require.asSymbolList(info.getRequires()));
      Map<String, String> loadFlags = info.getLoadFlags();
      if (!loadFlags.isEmpty()) {
        out.append(", ");
        writeJsObject(out, loadFlags);
      }
      out.append(");\n");
    }

    private static void writeJsObject(Appendable out, Map<String, String> map) throws IOException {
      List<String> entries = new ArrayList<>();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        String key = entry.getKey().replace("'", "\\'");
        String value = entry.getValue().replace("'", "\\'");
        entries.add("'" + key + "': '" + value + "'");
      }
      out.append("{");
      out.append(Joiner.on(", ").join(entries));
      out.append("}");
    }

    private static void writeJsArray(Appendable out, Collection<String> values) throws IOException {
      Iterable<String> quoted =
          Iterables.transform(values, arg -> "'" + arg.replace("'", "\\'") + "'");
      out.append("[");
      out.append(Joiner.on(", ").join(quoted));
      out.append("]");
    }
  }
}
