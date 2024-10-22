package com.codename1.components;

import com.codename1.ui.Button;
import com.codename1.ui.ButtonGroup;
import com.codename1.ui.CheckBox;
import com.codename1.ui.Command;
import com.codename1.ui.Component;
import static com.codename1.ui.ComponentSelector.$;
import com.codename1.ui.Container;
import com.codename1.ui.Image;
import com.codename1.ui.Label;
import com.codename1.ui.RadioButton;
import com.codename1.ui.SelectableIconHolder;
import com.codename1.ui.TextArea;
import com.codename1.ui.TextHolder;
import com.codename1.ui.events.ActionListener;
import com.codename1.ui.events.ActionSource;
import com.codename1.ui.layouts.BorderLayout;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.ui.layouts.FlowLayout;
import com.codename1.ui.plaf.UIManager;

public class SpanMultiButton extends Container implements ActionSource, SelectableIconHolder, TextHolder {
    private final TextArea firstRow = new TextArea();
    private final TextArea secondRow = new TextArea();
    private final TextArea thirdRow = new TextArea();
    private final TextArea forthRow = new TextArea();
    private final Button icon = new Button();
    private Button emblem = new Button();
    private boolean invert;
    private String group;  
    private boolean shouldLocalize;
    private int gap;
    
    public SpanMultiButton(String line1) {
        this();
        setTextLine1(line1);
    }
    
    public SpanMultiButton() {
        setUIID("MultiButton");
        
        firstRow.setActAsLabel(true);
        firstRow.setGrowByContent(true);
        firstRow.setUIID("MultiLine1");
        firstRow.setEditable(false);
        firstRow.setFocusable(false);
        
        secondRow.setActAsLabel(true);
        secondRow.setGrowByContent(true);
        secondRow.setUIID("MultiLine2");
        secondRow.setEditable(false);
        secondRow.setFocusable(false);
        
        thirdRow.setActAsLabel(true);
        thirdRow.setGrowByContent(true);
        thirdRow.setUIID("MultiLine3");
        thirdRow.setEditable(false);
        thirdRow.setFocusable(false);
        
        forthRow.setActAsLabel(true);
        forthRow.setGrowByContent(true);
        forthRow.setUIID("MultiLine4");
        forthRow.setEditable(false);
        forthRow.setFocusable(false);
        
        secondRow.setHidden(true);
        thirdRow.setHidden(true);
        forthRow.setHidden(true);
        
        setLayout(new BorderLayout());
        setFocusable(true);
        BorderLayout bl = new BorderLayout();
        Container iconContainer = new Container(bl);
        iconContainer.addComponent(BorderLayout.CENTER, icon);
        Container labels = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        Container labelsBorder = new Container(new BorderLayout());
        labelsBorder.addComponent(BorderLayout.SOUTH, labels);
        addComponent(BorderLayout.CENTER, labelsBorder);
        addComponent(BorderLayout.WEST, iconContainer);
        bl = new BorderLayout();
        Container emblemContainer = new Container(bl);
        emblemContainer.addComponent(BorderLayout.CENTER, emblem);
        addComponent(BorderLayout.EAST, emblemContainer);
        labelsBorder.addComponent(BorderLayout.CENTER, firstRow);
        labels.addComponent(secondRow);
        labels.addComponent(thirdRow);
        labels.addComponent(forthRow);
        firstRow.setName("Line1");
        secondRow.setName("Line2");
        thirdRow.setName("Line3");
        forthRow.setName("Line4");
        icon.setName("icon");
        icon.setUIID("Label");
        emblem.setName("emblem");
        emblem.setUIID("Emblem");
        setLeadComponent(emblem);
        Image i = UIManager.getInstance().getThemeImageConstant("defaultEmblemImage");
        if(i != null) {
            emblem.setIcon(i);
        }
        icon.bindStateTo(emblem);
        updateGap();
    }
    
    public void setLinesTogetherMode(boolean l) {
        if(l != isLinesTogetherMode()) {
            if(l) {
                firstRow.getParent().removeComponent(firstRow);
                Container p = secondRow.getParent();
                p.addComponent(0, firstRow);
                Container pp = p.getParent();
                pp.removeComponent(p);
                pp.addComponent(BorderLayout.CENTER, p);
            } else {
                secondRow.getParent().removeComponent(secondRow);
                thirdRow.getParent().addComponent(0, secondRow);
            }
        }
    }
    
    public boolean isLinesTogetherMode() {
        return firstRow.getParent() == secondRow.getParent();
    }
    
    public Label getIconComponent() {
        return icon;
    }
    
    public void setCheckBox(boolean b) {
        if(b != isCheckBox()) {
            Container par = emblem.getParent();
            Button old = emblem;
            if(b) {
                emblem = new CheckBox();
            } else {
                emblem = new Button();
            }
            emblem.setUIID(old.getUIID());
            emblem.setName(old.getName());
            java.util.List actionListeners = (java.util.List)old.getListeners();
            if(actionListeners != null) {
                for(int iter = 0 ; iter < actionListeners.size() ; iter++) {
                    emblem.addActionListener((ActionListener)actionListeners.get(iter));
                }
            }
            if(old.getCommand() != null) {
                Image img = old.getIcon();
                emblem.setCommand(old.getCommand());
                emblem.setText("");
                emblem.setIcon(img);
            } else {
                emblem.setText(old.getText());
                if(old.getIcon() != null) {
                    emblem.setIcon(old.getIcon());
                }
            }
            par.replace(old, emblem, null);
            setLeadComponent(emblem);
        }
    }
    
    public void addActionListener(ActionListener al) {
        emblem.addActionListener(al);
    }

    public void removeActionListener(ActionListener al) {
        emblem.removeActionListener(al);
    }

    @Override
    public void addLongPressListener(ActionListener l) {
        emblem.addLongPressListener(l);
    }

    @Override
    public void removeLongPressListener(ActionListener l) {
        emblem.removeLongPressListener(l);
    }

    @Override
    public void addPointerPressedListener(ActionListener l) {
        emblem.addPointerPressedListener(l);
    }

    @Override
    public void removePointerPressedListener(ActionListener l) {
        emblem.removePointerPressedListener(l);
    }
    
    public void addPointerReleasedListener(ActionListener l) {
        emblem.addPointerReleasedListener(l);
    }
    
    public void removePointerReleasedListener(ActionListener l) {
        emblem.removePointerReleasedListener(l);
    }
    
    public void setCommand(Command c) {
        Image img = emblem.getIcon();
        emblem.setCommand(c);
        emblem.setIcon(img);
        emblem.setText("");
    }

    public Command getCommand() {
        return emblem.getCommand();
    }
    
    public boolean isCheckBox() {
        return emblem instanceof CheckBox;
    }
    
    public void setRadioButton(boolean b) {
        if(b != isRadioButton()) {
            Container par = emblem.getParent();
            Button old = emblem;
            if(b) {
                emblem = new RadioButton();
                if(group != null) {
                    ((RadioButton)emblem).setGroup(group);
                }
            } else {
                emblem = new Button();
            }
            emblem.setName(old.getName());
            emblem.setUIID(old.getUIID());
            java.util.List actionListeners = (java.util.List)old.getListeners();
            if(actionListeners != null) {
                for(int iter = 0 ; iter < actionListeners.size() ; iter++) {
                    emblem.addActionListener((ActionListener)actionListeners.get(iter));
                }
            }
            if(old.getCommand() != null) {
                Image img = old.getIcon();
                emblem.setCommand(old.getCommand());
                emblem.setText("");
                emblem.setIcon(img);
            }
            par.replace(old, emblem, null);
            setLeadComponent(emblem);
            emblem.setShowEvenIfBlank(true);
        }
    }
    
    public boolean isRadioButton() {
        return emblem instanceof RadioButton;
    }
    
    public boolean isSelected() {
        return (emblem instanceof RadioButton || emblem instanceof CheckBox) && emblem.isSelected();
    }
    
    public void setSelected(boolean b) {
        if(emblem instanceof RadioButton) {
            ((RadioButton)emblem).setSelected(b);
            return;
        }
        if(emblem instanceof CheckBox) {
            ((CheckBox)emblem).setSelected(b);
            return;
        }
    }
    
    public void setHorizontalLayout(boolean b) {
        if(isHorizontalLayout() != b) {
            if(isHorizontalLayout()) {
                secondRow.getParent().getParent().removeComponent(secondRow.getParent());
            }
            secondRow.getParent().removeComponent(secondRow);
            if(b) {
                Container wrapper = new Container();
                Container c = firstRow.getParent();
                wrapper.addComponent(secondRow);
                c.addComponent(BorderLayout.EAST, wrapper);
            } else {
                Container c = thirdRow.getParent();
                c.addComponent(0, secondRow);
            }
        }
    }
    
    public boolean isHorizontalLayout() {
        return secondRow.getParent().getLayout() instanceof FlowLayout;
    }
    
    public void setInvertFirstTwoEntries(boolean b) {
        if(b != invert) {
            invert = b;
            if(isHorizontalLayout()) {
                Container c = firstRow.getParent();
                c.removeComponent(secondRow);
                if(invert) {
                    c.addComponent(BorderLayout.WEST, secondRow);
                } else {
                    c.addComponent(BorderLayout.EAST, secondRow);
                }
            }
        }
    }
    
    public boolean isInvertFirstTwoEntries() {
        return invert;
    }
    
    public void setTextLine1(String t) {
        t = shouldLocalize ? getUIManager().localize(t, t) : t;
        firstRow.setText(t);
        firstRow.setColumns(t.length() + 1);
        firstRow.setHidden(false);
    }
    
    public String getTextLine1() {
        return firstRow.getText();
    }

    public void setNameLine1(String t) {
        firstRow.setName(t);
    }
    
    public String getNameLine1() {
        return firstRow.getName();
    }
    
    public void setUIIDLine1(String t) {
        firstRow.setUIID(t);
    }
    
    public String getUIIDLine1() {
        return firstRow.getUIID();
    }

    public void setTextLine2(String t) {
        t = shouldLocalize ? getUIManager().localize(t, t) : t;
        secondRow.setText(t);
        secondRow.setColumns(t.length() + 1);
        secondRow.setHidden(false);
    }
    
    public String getTextLine2() {
        return secondRow.getText();
    }

    public void setNameLine2(String t) {
        secondRow.setName(t);
    }
    
    public String getNameLine2() {
        return secondRow.getName();
    }

    public void setUIIDLine2(String t) {
        secondRow.setUIID(t);
    }
    
    public String getUIIDLine2() {
        return secondRow.getUIID();
    }

    public void setTextLine3(String t) {
        t = shouldLocalize ? getUIManager().localize(t, t) : t;
        thirdRow.setText(t);
        thirdRow.setColumns(t.length() + 1);
        thirdRow.setHidden(false);
    }
    
    public void removeTextLine1() {
        firstRow.setText("");
        firstRow.setHidden(true);
    }
    
    public void removeTextLine2() {
        secondRow.setText("");
        secondRow.setHidden(true);
    }
    
    public void removeTextLine3() {
        thirdRow.setText("");
        thirdRow.setHidden(true);
    }
    
    public void removeTextLine4() {
        forthRow.setText("");
        forthRow.setHidden(true);
    }
    
    public String getTextLine3() {
        return thirdRow.getText();
    }

    public void setNameLine3(String t) {
        thirdRow.setName(t);
    }
    
    public String getNameLine3() {
        return thirdRow.getName();
    }

    public void setUIIDLine3(String t) {
        thirdRow.setUIID(t);
    }
    
    public String getUIIDLine3() {
        return thirdRow.getUIID();
    }

    public void setTextLine4(String t) {
        t = shouldLocalize ? getUIManager().localize(t, t) : t;
        forthRow.setText(t);
        forthRow.setColumns(t.length() + 1);
        forthRow.setHidden(false);
    }
    
    public String getTextLine4() {
        return forthRow.getText();
    }

    public void setNameLine4(String t) {
        forthRow.setName(t);
    }
    
    public String getNameLine4() {
        return forthRow.getName();
    }

    public void setUIIDLine4(String t) {
        forthRow.setUIID(t);
    }
    
    public String getUIIDLine4() {
        return forthRow.getUIID();
    }


    public void setIcon(Image i) {
        icon.setIcon(i);
        updateGap();
    }
    
    public Image getIcon() {
        return icon.getIcon();
    }

    public void setEmblem(Image i) {
        emblem.setIcon(i);
    }
    
    public Image getEmblem() {
        return emblem.getIcon();
    }
    
    public void setIconPosition(String t) {
        String ip = getEmblemPosition();
        if(ip != null && ip.equals(t)) {
            String ep = getIconPosition();
            removeComponent(icon.getParent());
            setEmblemPosition(ep);
        } else {
            removeComponent(icon.getParent());
        }
        addComponent(t, icon.getParent());
        updateGap();
        revalidateLater();
    }
    
    public String getIconPosition() {
        return (String)getLayout().getComponentConstraint(icon.getParent());
    }


    public void setEmblemPosition(String t) {
        String ip = getIconPosition();
        if(ip != null && ip.equals(t)) {
            String ep = getEmblemPosition();
            removeComponent(emblem.getParent());
            setIconPosition(ep);
        } else {
            removeComponent(emblem.getParent());
        }
        addComponent(t, emblem.getParent());
        revalidateLater();
    }
    
    public String getEmblemPosition() {
        return (String)getLayout().getComponentConstraint(emblem.getParent());
    }
    
    public void setIconName(String t) {
        icon.setName(t);
    }
    
    public String getIconName() {
        return icon.getName();
    }

    public void setIconUIID(String t) {
        icon.setUIID(t);
        updateGap();
    }
    
    public String getIconUIID() {
        return icon.getUIID();
    }

    public void setEmblemName(String t) {
        emblem.setName(t);
    }
    
    public String getEmblemName() {
        return emblem.getName();
    }

    public void setEmblemUIID(String t) {
        emblem.setUIID(t);
    }
    
    public String getEmblemUIID() {
        return emblem.getUIID();
    }


    public String[] getPropertyNames() {
        return new String[] {
            "line1", "line2", "line3", "line4", "name1", "name2", "name3", "name4", 
            "uiid1", "uiid2", "uiid3", "uiid4", "icon", "iconName", "iconUiid", "iconPosition",
            "emblem", "emblemName", "emblemUiid", "emblemPosition", "horizontalLayout", 
            "invertFirstTwoEntries", "checkBox", "radioButton", "group", "selected",
            "maskName"
        };
    }

    public Class[] getPropertyTypes() {
       return new Class[] {
           String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,Image.class,String.class,String.class,String.class,Image.class,String.class,String.class,String.class,Boolean.class,
           Boolean.class,
           Boolean.class,
           Boolean.class,
           String.class,Boolean.class, String.class
       };
    }

    public Object getPropertyValue(String name) {
        if(name.equals("line1")) {
            return getTextLine1();
        }
        if(name.equals("line2")) {
            return getTextLine2();
        }
        if(name.equals("line3")) {
            return getTextLine3();
        }
        if(name.equals("line4")) {
            return getTextLine4();
        }
        if(name.equals("name1")) {
            return getNameLine1();
        }
        if(name.equals("name2")) {
            return getNameLine2();
        }
        if(name.equals("name3")) {
            return getNameLine3();
        }
        if(name.equals("name4")) {
            return getNameLine4();
        }
        if(name.equals("uiid1")) {
            return getUIIDLine1();
        }
        if(name.equals("uiid2")) {
            return getUIIDLine2();
        }
        if(name.equals("uiid3")) {
            return getUIIDLine3();
        }
        if(name.equals("uiid4")) {
            return getUIIDLine4();
        }
        if(name.equals("icon")) {
            return getIcon();
        }
        if(name.equals("iconName")) {
            return getIconName();
        }
        if(name.equals("iconUiid")) {
            return getIconUIID();
        }
        if(name.equals("iconPosition")) {
            return getIconPosition();
        }
        if(name.equals("emblem")) {
            return getEmblem();
        }
        if(name.equals("emblemName")) {
            return getEmblemName();
        }
        if(name.equals("emblemUiid")) {
            return getEmblemUIID();
        }
        if(name.equals("emblemPosition")) {
            return getEmblemPosition();
        }
        if(name.equals("horizontalLayout")) {
            if(isHorizontalLayout()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        if(name.equals("invertFirstTwoEntries")) {
            if(isInvertFirstTwoEntries()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        if(name.equals("checkBox")) {
            if(isCheckBox()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        if(name.equals("radioButton")) {
            if(isRadioButton()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        if(name.equals("group")) {
            return getGroup();
        }
        if(name.equals("selected")) {
            if(isSelected()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        if(name.equals("maskName")) {
            return getMaskName();
        }
        return null;
    }

    @Override
    public void setText(String text) {
        setTextLine1(text);
    }

    @Override
    public String getText() {
        return getTextLine1();
    }

    
    public String setPropertyValue(String name, Object value) {
        if(name.equals("line1")) {
            setTextLine1((String)value);
            return null;
        }
        if(name.equals("line2")) {
            setTextLine2((String)value);
            return null;
        }
        if(name.equals("line3")) {
            setTextLine3((String)value);
            return null;
        }
        if(name.equals("line4")) {
            setTextLine4((String)value);
            return null;
        }
        if(name.equals("name1")) {
            setNameLine1((String)value);
            return null;
        }
        if(name.equals("name2")) {
            setNameLine2((String)value);
            return null;
        }
        if(name.equals("name3")) {
            setNameLine3((String)value);
            return null;
        }
        if(name.equals("name4")) {
            setNameLine4((String)value);
            return null;
        }
        if(name.equals("uiid1")) {
            setUIIDLine1((String)value);
            return null;
        }
        if(name.equals("uiid2")) {
            setUIIDLine2((String)value);
            return null;
        }
        if(name.equals("uiid3")) {
            setUIIDLine3((String)value);
            return null;
        }
        if(name.equals("uiid4")) {
            setUIIDLine4((String)value);
            return null;
        }
        if(name.equals("icon")) {
            setIcon((Image)value);
            return null;
        }
        if(name.equals("iconUiid")) {
            setIconUIID((String)value);
            return null;
        }
        if(name.equals("iconName")) {
            setIconName((String)value);
            return null;
        }
        if(name.equals("iconPosition")) {
            setIconPosition((String)value);
            return null;
        }
        if(name.equals("emblem")) {
            setEmblem((Image)value);
            return null;
        }
        if(name.equals("emblemUiid")) {
            setEmblemUIID((String)value);
            return null;
        }
        if(name.equals("emblemName")) {
            setEmblemName((String)value);
            return null;
        }
        if(name.equals("emblemPosition")) {
            setEmblemPosition((String)value);
            return null;
        }
        if(name.equals("horizontalLayout")) {
            setHorizontalLayout(((Boolean)value).booleanValue());
            return null;
        }
        if(name.equals("invertFirstTwoEntries")) {
            setInvertFirstTwoEntries(((Boolean)value).booleanValue());
            return null;
        }
        if(name.equals("checkBox")) {
            setCheckBox(((Boolean)value).booleanValue());
            return null;
        }
        if(name.equals("radioButton")) {
            setRadioButton(((Boolean)value).booleanValue());
            return null;
        }
        if(name.equals("group")) {
            setGroup((String)value);
            return null;
        }
        if(name.equals("selected")) {
            setSelected(((Boolean)value).booleanValue());
            return null;
        }
        if(name.equals("maskName")) {
            setMaskName((String)value);
            return null;
        }
        return super.setPropertyValue(name, value);
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
        if(emblem instanceof RadioButton) {
            ((RadioButton)emblem).setGroup(group);
        }
    }

    public String getMaskName() {
        return icon.getMaskName();
    }

    public void setMaskName(String maskName) {
        icon.setMaskName(maskName);
    }
    
    public boolean isShouldLocalize() {
        return shouldLocalize;
    }

    public void setShouldLocalize(boolean shouldLocalize) {
        this.shouldLocalize = shouldLocalize;
    }
    
    public void setGroup(ButtonGroup bg) {
        bg.add((RadioButton)emblem);
    }

    @Override
    public void setGap(int gap) {
        if (gap != this.gap) {
            this.gap = gap;
            updateGap();
        }
    }

    @Override
    public int getGap() {
        return gap;
    }

    @Override
    public void setTextPosition(int textPosition) {
        switch (textPosition) {
            case Component.TOP:
                setIconPosition(BorderLayout.SOUTH);
                break;
            case Component.BOTTOM:
                setIconPosition(BorderLayout.NORTH);
                break;
            case Component.LEFT:
                setIconPosition(BorderLayout.EAST);
                break;
            case Component.RIGHT:
                setIconPosition(BorderLayout.WEST);
                break;
            default:
                setIconPosition(BorderLayout.EAST);
        }
        
    }

    @Override
    public int getTextPosition() {
        String iconPosition = getIconPosition();
        if (BorderLayout.NORTH.equals(iconPosition)) {
            return Component.BOTTOM;
        }
        if (BorderLayout.SOUTH.equals(iconPosition)) {
            return Component.TOP;
        }
        if (BorderLayout.EAST.equals(iconPosition)) {
            return Component.LEFT;
        }
        if (BorderLayout.WEST.equals(iconPosition)) {
            return Component.RIGHT;
        }
        return Component.LEFT;
        
    }
    
    
    private void updateGap() {
        if (getIcon() == null) {
            $(icon).setMargin(0);
        } else if (BorderLayout.NORTH.equals(getIconPosition())) {
            $(icon).selectAllStyles().setMargin(0, 0, gap, 0);
        } else if (BorderLayout.SOUTH.equals(getIconPosition())) {
            $(icon).selectAllStyles().setMargin(gap, 0, 0, 0);
        } else if (BorderLayout.EAST.equals(getIconPosition())) {
            $(icon).selectAllStyles().setMargin(0, 0, 0, gap);
        } else if (BorderLayout.WEST.equals(getIconPosition())) {
            $(icon).selectAllStyles().setMargin(0, gap, 0, 0);
        }
    }
    

    @Override
    public Component getIconStyleComponent() {
        return icon;
    }
    
    @Override
    public Image getRolloverIcon() {
        return icon.getRolloverIcon();
    }

    
    @Override
    public void setPressedIcon(Image arg0) {
        icon.setPressedIcon(arg0);
    }

    @Override
    public Image getPressedIcon() {
        return icon.getPressedIcon();
    }

    @Override
    public void setDisabledIcon(Image arg0) {
        icon.setDisabledIcon(arg0);
    }

    @Override
    public Image getDisabledIcon() {
        return icon.getDisabledIcon();
    }

    @Override
    public void setRolloverPressedIcon(Image icn) {
        icon.setRolloverPressedIcon(icn);
    }

    @Override
    public Image getRolloverPressedIcon() {
        return icon.getRolloverPressedIcon();
    }

    @Override
    public Image getIconFromState() {
        return icon.getIconFromState();
    }

    @Override
    public void setRolloverIcon(Image arg0) {
        icon.setRolloverIcon(arg0);
    }
    
}
