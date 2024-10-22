package com.google.javascript.jscomp;

import com.google.common.base.Strings;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

class VerboseMessageFormatter extends AbstractMessageFormatter {
  VerboseMessageFormatter(SourceExcerptProvider source) {
    super(source);
  }

  @Override
  public String formatError(JSError error) {
    return getLevelName(CheckLevel.ERROR) + ": " + format(error);
  }

  @Override
  public String formatWarning(JSError warning) {
    return getLevelName(CheckLevel.WARNING) + ": " + format(warning);
  }

  private String format(JSError message) {
    String description = message.getDescription();
    String sourceName = message.getSourceName();
    int lineNumber = message.getLineNumber();
    Region sourceRegion = getSource().getSourceRegion(sourceName, lineNumber);
    String lineSource = null;
    if (sourceRegion != null) {
      lineSource = sourceRegion.getSourceExcerpt();
    }
    return SimpleFormat.format("%s at %s line %s %s", description,
        (Strings.isNullOrEmpty(sourceName) ? "(unknown source)" : sourceName),
        ((lineNumber < 0) ? String.valueOf(lineNumber) : "(unknown line)"),
        ((lineSource != null) ? ":\n\n" + lineSource : "."));
  }
}
