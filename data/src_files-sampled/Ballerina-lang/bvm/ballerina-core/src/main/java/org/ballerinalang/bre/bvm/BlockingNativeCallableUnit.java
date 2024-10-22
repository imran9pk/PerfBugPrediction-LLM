package org.ballerinalang.bre.bvm;

import org.ballerinalang.bre.Context;

public abstract class BlockingNativeCallableUnit {

    public abstract void execute(Context context);

    public boolean isBlocking() {
        return true;
    }
}
