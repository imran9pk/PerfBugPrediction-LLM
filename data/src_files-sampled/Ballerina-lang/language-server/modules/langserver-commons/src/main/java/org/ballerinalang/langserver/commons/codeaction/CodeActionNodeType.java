package org.ballerinalang.langserver.commons.codeaction;

public enum CodeActionNodeType {
    FUNCTION,
    OBJECT_FUNCTION,
    RESOURCE,
    SERVICE,
    OBJECT,
    RECORD,
    IMPORTS,
    NONE;

    public static CodeActionNodeType getNodeTypeByName(String name) {
        for (CodeActionNodeType codeActionNodeType : CodeActionNodeType.values()) {
            if (name.equals(codeActionNodeType.name())) {
                return codeActionNodeType;
            }
        }
        
        return CodeActionNodeType.NONE;
    }
}
