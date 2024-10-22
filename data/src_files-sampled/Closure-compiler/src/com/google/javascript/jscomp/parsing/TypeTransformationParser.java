package com.google.javascript.jscomp.parsing;

import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt32;
import static java.lang.Double.isNaN;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Msg;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

public final class TypeTransformationParser {

  private final String typeTransformationString;
  private Node typeTransformationAst;
  private final StaticSourceFile sourceFile;
  private final ErrorReporter errorReporter;
  private final int templateLineno;
  private final int templateCharno;

  private static final int VAR_ARGS = Integer.MAX_VALUE;

  public static enum OperationKind {
    TYPE_CONSTRUCTOR,
    OPERATION,
    STRING_PREDICATE,
    TYPE_PREDICATE,
    TYPEVAR_PREDICATE
  }

  public static enum Keywords {
    ALL("all", 0, 0, OperationKind.TYPE_CONSTRUCTOR),
    COND("cond", 3, 3, OperationKind.OPERATION),
    EQ("eq", 2, 2, OperationKind.TYPE_PREDICATE),
    ISCTOR("isCtor", 1, 1, OperationKind.TYPE_PREDICATE),
    ISDEFINED("isDefined", 1, 1, OperationKind.TYPEVAR_PREDICATE),
    ISRECORD("isRecord", 1, 1, OperationKind.TYPE_PREDICATE),
    ISTEMPLATIZED("isTemplatized", 1, 1, OperationKind.TYPE_PREDICATE),
    ISUNKNOWN("isUnknown", 1, 1, OperationKind.TYPE_PREDICATE),
    INSTANCEOF("instanceOf", 1, 1, OperationKind.OPERATION),
    MAPUNION("mapunion", 2, 2, OperationKind.OPERATION),
    MAPRECORD("maprecord", 2, 2, OperationKind.OPERATION),
    NONE("none", 0, 0, OperationKind.TYPE_CONSTRUCTOR),
    PRINTTYPE("printType", 2, 2, OperationKind.OPERATION),
    PROPTYPE("propType", 2, 2, OperationKind.OPERATION),
    RAWTYPEOF("rawTypeOf", 1, 1, OperationKind.TYPE_CONSTRUCTOR),
    SUB("sub", 2, 2, OperationKind.TYPE_PREDICATE),
    STREQ("streq", 2, 2, OperationKind.STRING_PREDICATE),
    RECORD("record", 1, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    TEMPLATETYPEOF("templateTypeOf", 2, 2, OperationKind.TYPE_CONSTRUCTOR),
    TYPE("type", 2, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    TYPEEXPR("typeExpr", 1, 1, OperationKind.TYPE_CONSTRUCTOR),
    TYPEOFVAR("typeOfVar", 1, 1, OperationKind.OPERATION),
    UNION("union", 2, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    UNKNOWN("unknown", 0, 0, OperationKind.TYPE_CONSTRUCTOR);

    public final String name;
    public final int minParamCount;
    public final int maxParamCount;
    public final OperationKind kind;

    Keywords(String name, int minParamCount, int maxParamCount,
        OperationKind kind) {
      this.name = name;
      this.minParamCount = minParamCount;
      this.maxParamCount = maxParamCount;
      this.kind = kind;
    }
  }

  public TypeTransformationParser(String typeTransformationString,
      StaticSourceFile sourceFile, ErrorReporter errorReporter,
      int templateLineno, int templateCharno) {
    this.typeTransformationString = typeTransformationString;
    this.sourceFile = sourceFile;
    this.errorReporter = errorReporter;
    this.templateLineno = templateLineno;
    this.templateCharno = templateCharno;
  }

  public Node getTypeTransformationAst() {
    return typeTransformationAst;
  }

  private void addNewWarning(Msg messageId, String messageArg) {
    errorReporter.warning(
        "Bad type annotation. " + messageId.format(messageArg),
        sourceFile.getName(),
        templateLineno,
        templateCharno);
  }

  private Keywords nameToKeyword(String s) {
    return Keywords.valueOf(Ascii.toUpperCase(s));
  }

  private boolean isValidKeyword(String name) {
    for (Keywords k : Keywords.values()) {
      if (k.name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isOperationKind(String name, OperationKind kind) {
    return isValidKeyword(name) && nameToKeyword(name).kind == kind;
  }

  private boolean isValidStringPredicate(String name) {
    return isOperationKind(name, OperationKind.STRING_PREDICATE);
  }

  private boolean isValidTypePredicate(String name) {
    return isOperationKind(name, OperationKind.TYPE_PREDICATE);
  }

  private boolean isValidTypevarPredicate(String name) {
    return isOperationKind(name, OperationKind.TYPEVAR_PREDICATE);
  }

  private boolean isBooleanOperation(Node n) {
    return n.isAnd() || n.isOr() || n.isNot();
  }

  private boolean isValidPredicate(String name) {
    return isValidStringPredicate(name)
        || isValidTypePredicate(name)
        || isValidTypevarPredicate(name);
  }

  private int getFunctionParamCount(Node n) {
    Preconditions.checkArgument(n.isFunction(),
        "Expected a function node, found %s", n);
    return n.getSecondChild().getChildCount();
  }

  private Node getFunctionBody(Node n) {
    Preconditions.checkArgument(n.isFunction(),
        "Expected a function node, found %s", n);
    return n.getChildAtIndex(2);
  }

  private String getCallName(Node n) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getFirstChild().getString();
  }

  private Node getCallArgument(Node n, int i) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getChildAtIndex(i + 1);
  }

  private int getCallParamCount(Node n) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getChildCount() - 1;
  }

  private boolean isTypeVar(Node n) {
    return n.isName();
  }

  private boolean isTypeName(Node n) {
    return n.isStringLit();
  }

  private boolean isOperation(Node n) {
    return n.isCall();
  }

  private boolean isValidExpression(Node e) {
    return isTypeVar(e) || isTypeName(e) || isOperation(e);
  }

  private void warnInvalid(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_INVALID, msg);
  }

  private void warnInvalidExpression(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_INVALID_EXPRESSION, msg);
  }

  private void warnMissingParam(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_MISSING_PARAM, msg);
  }

  private void warnExtraParam(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_EXTRA_PARAM, msg);
  }

  private void warnInvalidInside(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_INVALID_INSIDE, msg);
  }

  private boolean checkParameterCount(Node expr, Keywords keyword) {
    int paramCount = getCallParamCount(expr);
    if (paramCount < keyword.minParamCount) {
      warnMissingParam(keyword.name);
      return false;
    }
    if (paramCount > keyword.maxParamCount) {
      warnExtraParam(keyword.name);
      return false;
    }
    return true;
  }

  public boolean parseTypeTransformation() {
    Config config =
        Config.builder()
            .setLanguageMode(Config.LanguageMode.ES_NEXT)
            .setStrictMode(Config.StrictMode.SLOPPY)
            .build();
    ParseResult result = ParserRunner.parse(
        sourceFile, typeTransformationString, config, errorReporter);
    Node ast = result.ast;
    if (ast == null
        || !ast.isScript()
        || !ast.hasChildren()
        || !ast.getFirstChild().isExprResult()) {
      warnInvalidExpression("type transformation");
      return false;
    }

    Node expr = ast.getFirstFirstChild();
    if (!validTypeTransformationExpression(expr)) {
      return false;
    }
    fixLineNumbers(expr);
    typeTransformationAst = expr;
    return true;
  }

  private void fixLineNumbers(Node expr) {
    expr.setLinenoCharno(expr.getLineno() + templateLineno, expr.getCharno() + templateCharno);
    for (Node child = expr.getFirstChild(); child != null; child = child.getNext()) {
      fixLineNumbers(child);
    }
  }

  private boolean validTemplateTypeExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.TYPE)) {
      return false;
    }
    int paramCount = getCallParamCount(expr);
    Node firstParam = getCallArgument(expr, 0);
    if (!isTypeVar(firstParam) && !isTypeName(firstParam)) {
      warnInvalid("type name or type variable");
      warnInvalidInside("template type operation");
      return false;
    }
    for (int i = 1; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("template type operation");
        return false;
      }
    }
    return true;
  }

  private boolean validUnionTypeExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.UNION)) {
      return false;
    }
    int paramCount = getCallParamCount(expr);
    for (int i = 0; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("union type");
        return false;
      }
    }
    return true;
  }

  private boolean validNoneTypeExpression(Node expr) {
    return checkParameterCount(expr, Keywords.NONE);
  }

  private boolean validAllTypeExpression(Node expr) {
    return checkParameterCount(expr, Keywords.ALL);
  }

  private boolean validUnknownTypeExpression(Node expr) {
    return checkParameterCount(expr, Keywords.UNKNOWN);
  }

  private boolean validRawTypeOfTypeExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.RAWTYPEOF)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.RAWTYPEOF.name);
      return false;
    }
    return true;
  }

  private boolean validTemplateTypeOfExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.TEMPLATETYPEOF)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name);
      return false;
    }
    if (!getCallArgument(expr, 1).isNumber()) {
      warnInvalid("index");
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name);
      return false;
    }
    double index = getCallArgument(expr, 1).getDouble();
    if (isNaN(index) || !isExactInt32(index)) {
      warnInvalid("index");
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name);
      return false;
    }
    return true;
  }

  private boolean validRecordParam(Node expr) {
    if (expr.isObjectLit()) {
      for (Node prop = expr.getFirstChild(); prop != null; prop = prop.getNext()) {
        if (prop.isShorthandProperty()) {
          warnInvalid("property, missing type");
          return false;
        } else if (!validTypeTransformationExpression(prop.getFirstChild())) {
          return false;
        }
      }
    } else if (!validTypeTransformationExpression(expr)) {
      return false;
    }
    return true;
  }

  private boolean validRecordTypeExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.RECORD)) {
      return false;
    }
    for (int i = 0; i < getCallParamCount(expr); i++) {
      if (!validRecordParam(getCallArgument(expr, i))) {
        warnInvalidInside(Keywords.RECORD.name);
        return false;
      }
    }
    return true;
  }

  private boolean validNativeTypeExpr(Node expr) {
    if (!checkParameterCount(expr, Keywords.TYPEEXPR)) {
      return false;
    }
    Node typeString = getCallArgument(expr, 0);
    if (!typeString.isStringLit()) {
      warnInvalidExpression("native type");
      warnInvalidInside(Keywords.TYPEEXPR.name);
      return false;
    }
    Node typeExpr = JsDocInfoParser.parseTypeString(typeString.getString());
    typeString.detach();
    expr.addChildToBack(typeExpr);
    return true;
  }

  private boolean validTypeExpression(Node expr) {
    String name = getCallName(expr);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case TYPE:
        return validTemplateTypeExpression(expr);
      case UNION:
        return validUnionTypeExpression(expr);
      case NONE:
        return validNoneTypeExpression(expr);
      case ALL:
        return validAllTypeExpression(expr);
      case UNKNOWN:
        return validUnknownTypeExpression(expr);
      case RAWTYPEOF:
        return validRawTypeOfTypeExpression(expr);
      case TEMPLATETYPEOF:
        return validTemplateTypeOfExpression(expr);
      case RECORD:
        return validRecordTypeExpression(expr);
      case TYPEEXPR:
        return validNativeTypeExpr(expr);
      default:
        throw new IllegalStateException("Invalid type expression");
    }
  }

  private boolean validTypePredicate(Node expr, int paramCount) {
    for (int i = 0; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("boolean");
        return false;
      }
    }
    return true;
  }

  private boolean isValidStringParam(Node expr) {
    if (!expr.isName() && !expr.isStringLit()) {
      warnInvalid("string");
      return false;
    }
    if (expr.getString().isEmpty()) {
      warnInvalid("string parameter");
      return false;
    }
    return true;
  }

  private boolean validStringPredicate(Node expr, int paramCount) {
    for (int i = 0; i < paramCount; i++) {
      if (!isValidStringParam(getCallArgument(expr, i))) {
        warnInvalidInside("boolean");
        return false;
      }
    }
    return true;
  }

  private boolean validTypevarParam(Node expr) {
    if (!isTypeVar(expr)) {
      warnInvalid("name");
      return false;
    }
    return true;
  }

  private boolean validTypevarPredicate(Node expr, int paramCount) {
    for (int i = 0; i < paramCount; i++) {
      if (!validTypevarParam(getCallArgument(expr, i))) {
        warnInvalidInside("boolean");
        return false;
      }
    }
    return true;
  }

  private boolean validBooleanOperation(Node expr) {
    boolean valid;
    if (expr.isNot()) {
      valid = validBooleanExpression(expr.getFirstChild());
    } else {
      valid = validBooleanExpression(expr.getFirstChild())
          && validBooleanExpression(expr.getSecondChild());
    }
    if (!valid) {
      warnInvalidInside("boolean");
      return false;
    }
    return true;
  }

  private boolean validBooleanExpression(Node expr) {
    if (isBooleanOperation(expr)) {
      return validBooleanOperation(expr);
    }

    if (!isOperation(expr)) {
      warnInvalidExpression("boolean");
      return false;
    }
    if (!isValidPredicate(getCallName(expr))) {
      warnInvalid("boolean predicate");
      return false;
    }
    Keywords keyword = nameToKeyword(getCallName(expr));
    if (!checkParameterCount(expr, keyword)) {
      return false;
    }
    switch (keyword.kind) {
      case TYPE_PREDICATE:
        return validTypePredicate(expr, getCallParamCount(expr));
      case STRING_PREDICATE:
        return validStringPredicate(expr, getCallParamCount(expr));
      case TYPEVAR_PREDICATE:
        return validTypevarPredicate(expr, getCallParamCount(expr));
      default:
        throw new IllegalStateException("Invalid boolean expression");
    }
  }

  private boolean validConditionalExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.COND)) {
      return false;
    }
    if (!validBooleanExpression(getCallArgument(expr, 0))) {
      warnInvalidInside("conditional");
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside("conditional");
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 2))) {
      warnInvalidInside("conditional");
      return false;
    }
    return true;
  }

  private boolean validMapunionExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.MAPUNION)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    if (!getCallArgument(expr, 1).isFunction()) {
      warnInvalid("map function");
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    Node mapFn = getCallArgument(expr, 1);
    int mapFnParamCount = getFunctionParamCount(mapFn);
    if (mapFnParamCount < 1) {
      warnMissingParam("map function");
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    if (mapFnParamCount > 1) {
      warnExtraParam("map function");
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    Node mapFnBody = getFunctionBody(mapFn);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body");
      return false;
    }
    return true;
  }

  private boolean validMaprecordExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.MAPRECORD)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    if (!getCallArgument(expr, 1).isFunction()) {
      warnInvalid("map function");
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    Node mapFn = getCallArgument(expr, 1);
    int mapFnParamCount = getFunctionParamCount(mapFn);
    if (mapFnParamCount < 2) {
      warnMissingParam("map function");
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    if (mapFnParamCount > 2) {
      warnExtraParam("map function");
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    Node mapFnBody = getFunctionBody(mapFn);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body");
      return false;
    }
    return true;
  }

  private boolean validTypeOfVarExpression(Node expr) {
 if (!checkParameterCount(expr, Keywords.TYPEOFVAR)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isStringLit()) {
      warnInvalid("name");
      warnInvalidInside(Keywords.TYPEOFVAR.name);
      return false;
    }
    return true;
  }

  private boolean validInstanceOfExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.INSTANCEOF)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.INSTANCEOF.name);
      return false;
    }
    return true;
  }

  private boolean validPrintTypeExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.PRINTTYPE)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isStringLit()) {
      warnInvalid("message");
      warnInvalidInside(Keywords.PRINTTYPE.name);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside(Keywords.PRINTTYPE.name);
      return false;
    }
    return true;
  }

  private boolean validPropTypeExpression(Node expr) {
    if (!checkParameterCount(expr, Keywords.PROPTYPE)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isStringLit()) {
      warnInvalid("property name");
      warnInvalidInside(Keywords.PROPTYPE.name);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside(Keywords.PROPTYPE.name);
      return false;
    }
    return true;
  }

  private boolean validOperationExpression(Node expr) {
    String name = getCallName(expr);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case COND:
        return validConditionalExpression(expr);
      case MAPUNION:
        return validMapunionExpression(expr);
      case MAPRECORD:
        return validMaprecordExpression(expr);
      case TYPEOFVAR:
        return validTypeOfVarExpression(expr);
      case INSTANCEOF:
        return validInstanceOfExpression(expr);
      case PRINTTYPE:
        return validPrintTypeExpression(expr);
      case PROPTYPE:
        return validPropTypeExpression(expr);
      default:
        throw new IllegalStateException("Invalid type transformation operation");
    }
  }

  private boolean validTypeTransformationExpression(Node expr) {
    if (!isValidExpression(expr)) {
      warnInvalidExpression("type transformation");
      return false;
    }
    if (isTypeVar(expr) || isTypeName(expr)) {
      return true;
    }
    String name = getCallName(expr);
    if (!isValidKeyword(name)) {
      warnInvalidExpression("type transformation");
      return false;
    }
    Keywords keyword = nameToKeyword(name);
    switch (keyword.kind) {
      case TYPE_CONSTRUCTOR:
        return validTypeExpression(expr);
      case OPERATION:
        return validOperationExpression(expr);
      default:
        throw new IllegalStateException("Invalid type transformation expression");
    }
  }
}
