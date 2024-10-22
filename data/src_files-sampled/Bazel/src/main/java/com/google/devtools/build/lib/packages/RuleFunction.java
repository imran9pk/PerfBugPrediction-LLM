package com.google.devtools.build.lib.packages;

import com.google.devtools.build.docgen.annot.DocCategory;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkCallable;

@StarlarkBuiltin(
    name = "rule",
    category = DocCategory.BUILTIN,
    doc =
        "A callable value representing the type of a native or Starlark rule. Calling the value"
            + " during evaluation of a package's BUILD file creates an instance of the rule and"
            + " adds it to the package's target set.")
public interface RuleFunction extends StarlarkCallable {
  RuleClass getRuleClass();
}
