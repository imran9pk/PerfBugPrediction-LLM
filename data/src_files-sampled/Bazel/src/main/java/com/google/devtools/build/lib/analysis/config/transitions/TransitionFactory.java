package com.google.devtools.build.lib.analysis.config.transitions;

public interface TransitionFactory<T> {

  ConfigurationTransition create(T data);

  default boolean isHost() {
    return false;
  }

  default boolean isTool() {
    if (isHost()) {
      return true;
    }

    return false;
  }

  default boolean isSplit() {
    return false;
  }
}
