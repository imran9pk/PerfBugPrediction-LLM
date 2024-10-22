package com.codename1.designer;

public class CustomComponent {
    private String type;
    private String className;
    private String codenameOneBaseClass;
    private Class cls;
    private boolean uiResource;

    public CustomComponent() {
    }

    public CustomComponent(boolean uiResource, String type) {
        this.type = type;
        this.uiResource = uiResource;
        cls = com.codename1.ui.Container.class;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getCodenameOneBaseClass() {
        return codenameOneBaseClass;
    }

    public void setCodenameOneBaseClass(String codenameOneBaseClass) {
        this.codenameOneBaseClass = codenameOneBaseClass;
    }

    public Class getCls() {
        return cls;
    }

    public void setCls(Class cls) {
        this.cls = cls;
    }

    public boolean isUiResource() {
        return uiResource;
    }

    public void setUiResource(boolean uiResource) {
        this.uiResource = uiResource;
    }
}
