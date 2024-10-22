package com.google.devtools.build.lib.graph;

import java.util.ArrayList;
import java.util.List;

public class CollectingVisitor<T> extends AbstractGraphVisitor<T> {
  private final List<Node<T>> order = new ArrayList<>();

  @Override
  public void visitNode(Node<T> node) {
    order.add(node);
  }

  public List<Node<T>> getVisitedNodes() {
    return order;
  }
}
