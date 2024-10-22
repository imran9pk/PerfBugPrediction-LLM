package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

public interface CompilerPass {

  void process(Node externs, Node root);
}
