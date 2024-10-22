package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

public final class CheckPrototypeProperties implements CompilerPass, NodeTraversal.Callback {
  public static final DiagnosticType ILLEGAL_PROTOTYPE_MEMBER =
      DiagnosticType.disabled(
          "JSC_ILLEGAL_PROTOTYPE_MEMBER",
          "Prototype property {0} should be a primitive, not an Array or Object.");

  final AbstractCompiler compiler;

  public CheckPrototypeProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal unused, Node n, Node parent) {
    if (NodeUtil.isPrototypePropertyDeclaration(n)) {
      Node assign = n.getFirstChild();
      Node rhs = assign.getLastChild();
      if (rhs.isArrayLit() || rhs.isObjectLit()) {
        JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(rhs);
        if (jsDoc != null && jsDoc.hasEnumParameterType()) {
          return;
        }
        String propName = assign.getFirstChild().getLastChild().getString();
        compiler.report(JSError.make(assign, ILLEGAL_PROTOTYPE_MEMBER, propName));
      }
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }
}

