package org.ballerinalang.model.symbols;

import org.ballerinalang.model.types.Type;

import java.util.List;

public interface InvokableSymbol extends Symbol {

    List<? extends VariableSymbol> getParameters();

    Type getReturnType();
}
