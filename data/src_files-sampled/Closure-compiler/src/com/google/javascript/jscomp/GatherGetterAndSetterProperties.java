package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;

public final class GatherGetterAndSetterProperties implements CompilerPass {

  private final AbstractCompiler compiler;

  GatherGetterAndSetterProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    update(compiler, externs, root);
  }

  public static void update(AbstractCompiler compiler, Node externs, Node root) {
    checkState(externs.getParent() == root.getParent());
    compiler.setAccessorSummary(AccessorSummary.create(gather(compiler, externs.getParent())));
  }

  static ImmutableMap<String, PropertyAccessKind> gather(AbstractCompiler compiler, Node root) {
    GatherCallback gatherCallback = new GatherCallback();
    NodeTraversal.traverse(compiler, root, gatherCallback);
    return ImmutableMap.copyOf(gatherCallback.properties);
  }

  private static final class GatherCallback extends AbstractPostOrderCallback {
    private final LinkedHashMap<String, PropertyAccessKind> properties = new LinkedHashMap<>();

    private void record(String property, PropertyAccessKind kind) {
      properties.merge(property, kind, PropertyAccessKind::unionWith);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETTER_DEF:
          recordGetterDef(n);
          break;
        case SETTER_DEF:
          recordSetterDef(n);
          break;
        case CALL:
          if (NodeUtil.isObjectDefinePropertyDefinition(n)) {
            visitDefineProperty(n);
          } else if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
            visitDefineProperties(n);
          }
          break;
        default:
          break;
      }
    }

    private void recordGetterDef(Node getterDef) {
      checkState(getterDef.isGetterDef());

      String name = getterDef.getString();
      record(name, PropertyAccessKind.GETTER_ONLY);
    }

    private void recordSetterDef(Node setterDef) {
      checkState(setterDef.isSetterDef());

      String name = setterDef.getString();
      record(name, PropertyAccessKind.SETTER_ONLY);
    }

    private void visitDescriptor(String propertyName, Node descriptor) {
      for (Node key = descriptor.getFirstChild(); key != null; key = key.getNext()) {
        if (key.isStringKey() || key.isMemberFunctionDef()) {
          if ("get".equals(key.getString())) {
            record(propertyName, PropertyAccessKind.GETTER_ONLY);
          } else if ("set".equals(key.getString())) {
            record(propertyName, PropertyAccessKind.SETTER_ONLY);
          }
        }
      }
    }

    private void visitDefineProperty(Node definePropertyCall) {
      Node propertyNameNode = definePropertyCall.getChildAtIndex(2);
      Node descriptor = definePropertyCall.getChildAtIndex(3);

      if (!propertyNameNode.isStringLit() || !descriptor.isObjectLit()) {
        return;
      }

      String propertyName = propertyNameNode.getString();
      visitDescriptor(propertyName, descriptor);
    }

    private void visitDefineProperties(Node definePropertiesCall) {
      Node props = definePropertiesCall.getChildAtIndex(2);

      if (!props.isObjectLit()) {
        return;
      }

      for (Node prop = props.getFirstChild(); prop != null; prop = prop.getNext()) {
        if (prop.isStringKey() && prop.hasOneChild() && prop.getFirstChild().isObjectLit()) {
          String propertyName = prop.getString();
          Node descriptor = prop.getFirstChild();

          visitDescriptor(propertyName, descriptor);
        }
      }
    }
  }
}
