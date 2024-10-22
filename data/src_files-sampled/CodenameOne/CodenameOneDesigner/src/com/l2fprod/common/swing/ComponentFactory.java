package com.l2fprod.common.swing;

import com.l2fprod.common.beans.editor.FixedButton;

import javax.swing.JButton;
import javax.swing.JComboBox;

public interface ComponentFactory {

  JButton createMiniButton();

  JComboBox createComboBox();

  public static class Helper {

    static ComponentFactory factory = new DefaultComponentFactory();

    public static ComponentFactory getFactory() {
      return factory;
    }
    
    public static void setFactory(ComponentFactory factory) {
      Helper.factory = factory;
    }
  }

  public static class DefaultComponentFactory implements ComponentFactory {
    public JButton createMiniButton() {
      return new FixedButton();
    }
    public JComboBox createComboBox() {
      return new JComboBox();
    }
  }
}
