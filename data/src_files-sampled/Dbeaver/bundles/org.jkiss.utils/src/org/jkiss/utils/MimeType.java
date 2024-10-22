package org.jkiss.utils;

import java.util.Locale;

public class MimeType {

    private String primaryType;
    private String subType;

    public MimeType() {
        primaryType = "application";
        subType = "*";
    }

    public MimeType(String rawdata) throws IllegalArgumentException {
        parse(rawdata);
    }

    public MimeType(String primary, String sub) {
        primaryType = primary.toLowerCase(Locale.ENGLISH);
        subType = sub.toLowerCase(Locale.ENGLISH);
    }

    private void parse(String rawdata) throws IllegalArgumentException {
        int slashIndex = rawdata.indexOf('/');
        int semIndex = rawdata.indexOf(';');
        if ((slashIndex < 0) && (semIndex < 0)) {
            primaryType = rawdata;
            subType = "*";
        } else if ((slashIndex < 0) && (semIndex >= 0)) {
            primaryType = rawdata.substring(0, semIndex);
            subType = "*";
        } else if ((slashIndex >= 0) && (semIndex < 0)) {
            primaryType = rawdata.substring(0, slashIndex).trim().toLowerCase(Locale.ENGLISH);
            subType = rawdata.substring(slashIndex + 1).trim().toLowerCase(Locale.ENGLISH);
        } else if (slashIndex < semIndex) {
            primaryType = rawdata.substring(0, slashIndex).trim().toLowerCase(Locale.ENGLISH);
            subType = rawdata.substring(slashIndex + 1, semIndex).trim().toLowerCase(Locale.ENGLISH);
        } else {
            throw new IllegalArgumentException("Unable to find a sub type.");
        }
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public String getSubType() {
        return subType;
    }

    public String toString() {
        return getBaseType();
    }

    public String getBaseType() {
        return primaryType + "/" + subType;
    }

    public boolean match(MimeType type) {
        return primaryType.equals(type.getPrimaryType()) &&
            (subType.equals("*") || type.getSubType().equals("*") || (subType.equals(type.getSubType())));
    }

    public boolean match(String rawdata) throws IllegalArgumentException {
        return match(new MimeType(rawdata));
    }

}