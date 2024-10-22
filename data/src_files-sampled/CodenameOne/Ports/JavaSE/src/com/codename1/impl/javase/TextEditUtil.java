package com.codename1.impl.javase;

import com.codename1.ui.Component;
import com.codename1.ui.Display;
import com.codename1.ui.Form;

public class TextEditUtil {

    public static Component curEditedComponent;

    public static void setCurrentEditComponent(Component current) {
        curEditedComponent = current;
    }

    public static boolean isLastEditComponent() {
        return getNextEditComponent() == null;
    }

    public static void editNextTextArea() {
        Runnable task = new Runnable() {

            public void run() {
                Component next = getNextEditComponent();
                if (next != null) {
                    if (next.isFocusable()) {
                        next.requestFocus();
                        next.startEditingAsync();
                    }
                    } 
            }
        };
        Display.getInstance().callSerially(task);
    }
    
    public static void editPrevTextArea() {
        Runnable task = new Runnable() {

            public void run() {
                Component next = getPrevEditComponent();
                if (next != null) {
                    if (next.isFocusable()) {
                        next.requestFocus();
                        next.startEditingAsync();
                    }
                    } 
            }
        };
        Display.getInstance().callSerially(task);
    }

    private static Component getNextEditComponent() {
        
        if (curEditedComponent != null) {
            Form parent = curEditedComponent.getComponentForm();
            if (parent != null) {
                return parent.getNextComponent(curEditedComponent);
            }
            
        }
        return null;
    }
    
    private static Component getPrevEditComponent() {
        
        if (curEditedComponent != null) {
            Form parent = curEditedComponent.getComponentForm();
            if (parent != null) {
                return parent.getPreviousComponent(curEditedComponent);
            }
            
        }
        return null;
    }

}

class BooleanContainer {

    private boolean on;

    public BooleanContainer() {
        on = false;
    }

    public void setOn() {
        this.on = true;
    }

    public boolean isOn() {
        return on;
    }

}
