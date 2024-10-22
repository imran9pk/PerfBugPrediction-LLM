package org.jkiss.dbeaver.lang;

import org.jkiss.code.Nullable;

public interface SCMNode {

    int getBeginOffset();

    int getEndOffset();

    @Nullable
    SCMCompositeNode getParentNode();

    @Nullable
    SCMNode getPreviousNode();

    @Nullable
    SCMNode getNextNode();

}
