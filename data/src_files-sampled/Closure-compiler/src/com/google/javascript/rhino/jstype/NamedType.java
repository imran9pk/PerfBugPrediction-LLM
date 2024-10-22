package com.google.javascript.rhino.jstype;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public final class NamedType extends ProxyObjectType {
  private static final JSTypeClass TYPE_CLASS = JSTypeClass.NAMED;

  static int nominalHashCode(ObjectType type) {
    checkState(type.hasReferenceName());
    String name = checkNotNull(type.getReferenceName());
    return name.hashCode();
  }

  private final String reference;
  private final String sourceName;
  private final int lineno;
  private final int charno;
  private final ResolutionKind resolutionKind;

  @Nullable private StaticTypedScope resolutionScope;

  private transient Predicate<JSType> validator;

  private transient List<PropertyContinuation> propertyContinuations = null;

  private final ImmutableList<JSType> templateTypes;

  private final boolean restrictByNull;

  private NamedType(Builder builder) {
    super(builder.registry, builder.referencedType);
    checkNotNull(builder.referenceName);
    checkNotNull(builder.resolutionKind);
    checkNotNull(builder.templateTypes);
    if (builder.resolutionKind.equals(ResolutionKind.TYPEOF)) {
      checkState(builder.referenceName.startsWith("typeof "));
    }
    this.restrictByNull = builder.restrictByNull;
    this.resolutionScope = builder.scope;
    this.reference = builder.referenceName;
    this.sourceName = builder.sourceName;
    this.lineno = builder.lineno;
    this.charno = builder.charno;
    this.templateTypes = builder.templateTypes;
    this.resolutionKind = builder.resolutionKind;

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  JSType getBangType() {
    if (restrictByNull) {
      return this;
    } else if (isResolved()) {
      return this.isNoResolvedType() || this.isUnknownType()
          ? this
          : getReferencedType().restrictByNotNullOrUndefined();
    }
    return this.toBuilder().setRestrictByNull(true).build();
  }

  @Override
  public ImmutableList<JSType> getTemplateTypes() {
    return templateTypes;
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode) {
    if (!isResolved()) {
      if (propertyContinuations == null) {
        propertyContinuations = new ArrayList<>();
      }
      propertyContinuations.add(
          new PropertyContinuation(
              propertyName, type, inferred, propertyNode));
      return true;
    } else {
      return super.defineProperty(
          propertyName, type, inferred, propertyNode);
    }
  }

  private void finishPropertyContinuations() {
    ObjectType referencedObjType = getReferencedObjTypeInternal();
    if (referencedObjType != null
        && !referencedObjType.isUnknownType()
        && propertyContinuations != null) {
      for (PropertyContinuation c : propertyContinuations) {
        c.commit(this);
      }
    }
    propertyContinuations = null;
  }

  public JSType getReferencedType() {
    return getReferencedTypeInternal();
  }

  @Override
  public String getReferenceName() {
    return reference;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    JSType type = this.getReferencedType();
    if (!isResolved() || type.isNoResolvedType()) {
      sb.append(getReferenceName());
    } else {
      sb.append(type);
    }
  }

  @Override
  public NamedType toMaybeNamedType() {
    return this;
  }

  @Override
  public boolean isNominalType() {
    return isResolved() ? super.isNominalType() : true;
  }

  @Override
  int recursionUnsafeHashCode() {
    return isSuccessfullyResolved() ? super.recursionUnsafeHashCode() : nominalHashCode(this);
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    ImmutableList<JSType> resolvedTypeArgs =
        JSTypeIterations.mapTypes((t) -> t.resolve(reporter), this.templateTypes);

    if (resolutionKind.equals(ResolutionKind.NONE)) {
      return super.resolveInternal(reporter);
    }
    checkState(
        getReferencedType().isUnknownType(),
        "NamedTypes given a referenced type pre-resolution should have ResolutionKind.NONE");

    if (resolutionScope == null) {
      return this;
    }

    boolean unused = resolveTypeof(reporter) || resolveViaRegistry(reporter);

    super.resolveInternal(reporter);
    if (detectInheritanceCycle()) {
      handleTypeCycle(reporter);
    }
    finishPropertyContinuations();

    JSType result = getReferencedType();
    if (isSuccessfullyResolved()) {
      this.resolutionScope = null;

      ObjectType resultAsObject = result.toMaybeObjectType();

      if (resultAsObject == null) {
        return result;
      }

      if (resolvedTypeArgs.isEmpty() || !resultAsObject.isRawTypeOfTemplatizedType()) {
        return result;
      }

      int numKeys = result.getTemplateParamCount();
      if (numKeys < resolvedTypeArgs.size()) {
        resolvedTypeArgs = resolvedTypeArgs.subList(0, numKeys);
      }

      result = registry.createTemplatizedType(resultAsObject, resolvedTypeArgs);
      setReferencedType(result.resolve(reporter));
    }

    return result;
  }

  private boolean resolveViaRegistry(ErrorReporter reporter) {
    JSType type = registry.getType(resolutionScope, reference);
    if (type == null) {
      handleUnresolvedType(reporter);
      return false;
    }
    setReferencedAndResolvedType(type, reporter);
    return true;
  }

  private boolean resolveTypeof(ErrorReporter reporter) {
    if (!resolutionKind.equals(ResolutionKind.TYPEOF)) {
      return false;
    }

    String scopeName = reference.substring("typeof ".length());
    JSType type = resolutionScope.lookupQualifiedName(QualifiedName.of(scopeName));
    if (type == null || type.isUnknownType()) {
      if (registry.isForwardDeclaredType(scopeName)) {
        setReferencedType(new NoResolvedType(registry, getReferenceName(), getTemplateTypes()));
        if (validator != null) {
          validator.apply(getReferencedType());
        }
      } else {
        warning(reporter, "Missing type for `typeof` value. The value must be declared and const.");
        setReferencedAndResolvedType(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE), reporter);
      }
    } else {
      if (type.isLiteralObject()) {
        JSType objlit = type;
        type =
            NamedType.builder(registry, getReferenceName())
                .setResolutionKind(ResolutionKind.NONE)
                .setReferencedType(objlit)
                .build();
      }
      setReferencedAndResolvedType(type, reporter);
    }

    return true;
  }

  private void setReferencedAndResolvedType(
      JSType type, ErrorReporter reporter) {
    if (restrictByNull) {
      type = type.restrictByNotNullOrUndefined();
    }
    if (validator != null) {
      validator.apply(type);
    }
    setReferencedType(type);
    checkEnumElementCycle(reporter);
    checkProtoCycle(reporter);
  }

  private void handleTypeCycle(ErrorReporter reporter) {
    setReferencedType(
        registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));
    warning(reporter, "Cycle detected in inheritance chain of type " + reference);
  }

  private void checkEnumElementCycle(ErrorReporter reporter) {
    JSType referencedType = getReferencedType();
    if (referencedType instanceof EnumElementType
        && identical(this, ((EnumElementType) referencedType).getPrimitiveType())) {
      handleTypeCycle(reporter);
    }
  }

  private void checkProtoCycle(ErrorReporter reporter) {
    JSType referencedType = getReferencedType();
    if (identical(referencedType, this)) {
      handleTypeCycle(reporter);
    }
  }

  private void handleUnresolvedType(ErrorReporter reporter) {
    boolean isForwardDeclared = registry.isForwardDeclaredType(reference);
    if (!isForwardDeclared) {
      String msg = "Bad type annotation. Unknown type " + reference;
      String root =
          reference.contains(".") ? reference.substring(0, reference.indexOf(".")) : reference;
      if (localVariableShadowsGlobalNamespace(root)) {
        msg += "\nIt's possible that a local variable called '" + root
            + "' is shadowing the intended global namespace.";
      }
      warning(reporter, msg);
    } else {
      setReferencedType(new NoResolvedType(registry, getReferenceName(), getTemplateTypes()));
      if (validator != null) {
        validator.apply(getReferencedType());
      }
    }
  }

  private boolean localVariableShadowsGlobalNamespace(String root) {
    StaticSlot rootVar = resolutionScope.getSlot(root);
    if (rootVar != null) {
      checkNotNull(rootVar.getScope(), rootVar);
      StaticScope parent = rootVar.getScope().getParentScope();
      if (parent != null) {
        StaticSlot globalVar = parent.getSlot(root);
        return globalVar != null;
      }
    }
    return false;
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    if (this.isResolved()) {
      return super.setValidator(validator);
    } else {
      this.validator = validator;
      return true;
    }
  }

  void warning(ErrorReporter reporter, String message) {
    reporter.warning(message, sourceName, lineno, charno);
  }

  private static final class PropertyContinuation {
    private final String propertyName;
    private final JSType type;
    private final boolean inferred;
    private final Node propertyNode;

    private PropertyContinuation(
        String propertyName,
        JSType type,
        boolean inferred,
        Node propertyNode) {
      this.propertyName = propertyName;
      this.type = type;
      this.inferred = inferred;
      this.propertyNode = propertyNode;
    }

    void commit(ObjectType target) {
      target.defineProperty(
          propertyName, type, inferred, propertyNode);
    }
  }

  @Override
  public boolean isObject() {
    if (isEnumElementType()) {
      return toMaybeEnumElementType().isObject();
    }
    return super.isObject();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseNamedType(this);
  }

  static Builder builder(JSTypeRegistry registry, String reference) {
    return new Builder(registry, reference);
  }

  enum ResolutionKind {
    NONE,
    TYPE_NAME,
    TYPEOF
  }

  Builder toBuilder() {
    checkState(!isResolved(), "Only call toBuilder on unresolved NamedTypes");
    return new Builder(this.registry, this.reference)
        .setScope(this.resolutionScope)
        .setResolutionKind(this.resolutionKind)
        .setErrorReportingLocation(this.sourceName, this.lineno, this.charno)
        .setTemplateTypes(this.templateTypes)
        .setReferencedType(getReferencedType())
        .setRestrictByNull(this.restrictByNull);
  }

  static final class Builder {
    private final JSTypeRegistry registry;
    private ResolutionKind resolutionKind;
    private final String referenceName;
    private StaticTypedScope scope;
    private String sourceName;
    private int lineno;
    private int charno;
    private JSType referencedType;
    private boolean restrictByNull;
    private ImmutableList<JSType> templateTypes = ImmutableList.of();

    private Builder(JSTypeRegistry registry, String referenceName) {
      this.registry = registry;
      this.referenceName = referenceName;
      this.referencedType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }

    Builder setScope(StaticTypedScope scope) {
      this.scope = scope;
      return this;
    }

    Builder setResolutionKind(ResolutionKind resolutionKind) {
      this.resolutionKind = resolutionKind;
      return this;
    }

    Builder setErrorReportingLocation(String sourceName, int lineno, int charno) {
      this.sourceName = sourceName;
      this.lineno = lineno;
      this.charno = charno;
      return this;
    }

    Builder setErrorReportingLocationFrom(Node source) {
      this.sourceName = source.getSourceFileName();
      this.lineno = source.getLineno();
      this.charno = source.getCharno();
      return this;
    }

    Builder setTemplateTypes(ImmutableList<JSType> templateTypes) {
      this.templateTypes = templateTypes;
      return this;
    }

    Builder setReferencedType(JSType referencedType) {
      this.referencedType = referencedType;
      return this;
    }

    private Builder setRestrictByNull(boolean restrictByNull) {
      this.restrictByNull = restrictByNull;
      return this;
    }

    NamedType build() {
      return new NamedType(this);
    }
  }
}
