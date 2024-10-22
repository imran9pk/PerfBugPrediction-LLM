package org.ballerinalang.tool.util;

import org.ballerinalang.util.diagnostic.Diagnostic;
import org.testng.Assert;

public class BAssertUtil {

    private static final String CARRIAGE_RETURN_CHAR = "\\r";
    private static final String EMPTY_STRING = "";

    public static void validateError(CompileResult result, int errorIndex, String expectedErrMsg, int expectedErrLine,
            int expectedErrCol) {
        Diagnostic diag = result.getDiagnostics()[errorIndex];
        Assert.assertEquals(diag.getKind(), Diagnostic.Kind.ERROR, "incorrect diagnostic type");
        Assert.assertEquals(diag.getMessage().replace(CARRIAGE_RETURN_CHAR, EMPTY_STRING),
                expectedErrMsg.replace(CARRIAGE_RETURN_CHAR, EMPTY_STRING), "incorrect error message:");
        Assert.assertEquals(diag.getPosition().getStartLine(), expectedErrLine, "incorrect line number:");
        Assert.assertEquals(diag.getPosition().getStartColumn(), expectedErrCol, "incorrect column position:");
    }

    public static void validateError(CompileResult result, int errorIndex, int expectedErrLine, int expectedErrCol) {
        Diagnostic diag = result.getDiagnostics()[errorIndex];
        Assert.assertEquals(diag.getKind(), Diagnostic.Kind.ERROR, "incorrect diagnostic type");
        Assert.assertEquals(diag.getPosition().getStartLine(), expectedErrLine, "incorrect line number:");
        Assert.assertEquals(diag.getPosition().getStartColumn(), expectedErrCol, "incorrect column position:");
    }

    public static void validateErrorMessageOnly(CompileResult result, int errorIndex, String expectedPartOfErrMsg) {
        Diagnostic diag = result.getDiagnostics()[errorIndex];
        Assert.assertEquals(diag.getKind(), Diagnostic.Kind.ERROR, "incorrect diagnostic type");
        Assert.assertTrue(diag.getMessage().contains(expectedPartOfErrMsg),
                '\'' + expectedPartOfErrMsg + "' is not contained in error message '" + diag.getMessage() + '\'');
    }

    public static void validateErrorMessageOnly(CompileResult result, int errorIndex, String[] expectedPartsOfErrMsg) {
        Diagnostic diag = result.getDiagnostics()[errorIndex];
        Assert.assertEquals(diag.getKind(), Diagnostic.Kind.ERROR, "incorrect diagnostic type");
        boolean contains = false;
        for (String part : expectedPartsOfErrMsg) {
            if (diag.getMessage().contains(part)) {
                contains = true;
                break;
            }
        }
        Assert.assertTrue(contains,
                "None of given strings is contained in the error message '" + diag.getMessage() + '\'');
    }

    public static void validateWarning(CompileResult result, int warningIndex, String expectedWarnMsg,
            int expectedWarnLine, int expectedWarnCol) {
        Diagnostic diag = result.getDiagnostics()[warningIndex];
        Assert.assertEquals(diag.getKind(), Diagnostic.Kind.WARNING, "incorrect diagnostic type");
        Assert.assertEquals(diag.getMessage(), expectedWarnMsg, "incorrect warning message:");
        Assert.assertEquals(diag.getPosition().getStartLine(), expectedWarnLine, "incorrect line number:");
        Assert.assertEquals(diag.getPosition().getStartColumn(), expectedWarnCol, "incorrect column position:");
    }
}
