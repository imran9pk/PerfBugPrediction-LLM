package org.jkiss.dbeaver.ui.controls.resultset;

public interface IStatefulControl {


    Object saveState();

    void restoreState(Object state);

}
