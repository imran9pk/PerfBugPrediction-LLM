package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.collect.ImmutableList;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.IncludeType;

import java.util.List;

public class IncludeWithMacroFunctionCallExpression extends AbstractInclude {
    private final String name;
    private final boolean isImport;
    private final ImmutableList<Expression> arguments;

    public IncludeWithMacroFunctionCallExpression(String name, boolean isImport, ImmutableList<Expression> arguments) {
        this.name = name;
        this.isImport = isImport;
        this.arguments = arguments;
    }

    @Override
    public IncludeType getType() {
        return IncludeType.MACRO_FUNCTION;
    }

    @Override
    public String getValue() {
        return name;
    }

    @Override
    public boolean isImport() {
        return isImport;
    }

    @Override
    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IncludeWithMacroFunctionCallExpression other = (IncludeWithMacroFunctionCallExpression) obj;
        return name.equals(other.name) && isImport == other.isImport && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ arguments.hashCode();
    }
}
