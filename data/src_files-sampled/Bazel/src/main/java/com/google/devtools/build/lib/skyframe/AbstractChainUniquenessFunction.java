package com.google.devtools.build.lib.skyframe;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.skyframe.EmptySkyValue;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

abstract class AbstractChainUniquenessFunction<S> implements SkyFunction {
  protected abstract String getConciseDescription();

  protected abstract String getHeaderMessage();

  protected abstract String getFooterMessage();

  protected abstract String elementToString(S elt);

  @Override
  public SkyValue compute(SkyKey skyKey, Environment env) {
    StringBuilder errorMessage = new StringBuilder();
    errorMessage.append(getConciseDescription() + " detected\n");
    errorMessage.append(getHeaderMessage() + "\n");
    @SuppressWarnings("unchecked")
    ImmutableList<S> chain = (ImmutableList<S>) skyKey.argument();
    for (S elt : chain) {
      errorMessage.append(elementToString(elt) + "\n");
    }
    errorMessage.append(getFooterMessage() + "\n");
    env.getListener().handle(Event.error(errorMessage.toString()));
    return EmptySkyValue.INSTANCE;
  }

  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }
}

