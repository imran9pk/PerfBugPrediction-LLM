package com.codename1.ui.html;

interface CSSParserCallback {

    public static int ERROR_CSS_ATTRIBUTE_NOT_SUPPORTED = 200;

    public static int ERROR_CSS_ATTIBUTE_VALUE_INVALID = 201;

    public static int ERROR_CSS_NOT_FOUND = 202;

    public static int ERROR_CSS_NO_BASE_URL = 203;

    public boolean parsingError(int errorId,String tag,String attribute,String value,String description);


}
