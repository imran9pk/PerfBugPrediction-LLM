package org.jkiss.dbeaver.ui;

import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.jkiss.utils.CommonUtils;

public class UITextUtils {

    public static String getShortText(GC gc, String t, int width) {
        if (CommonUtils.isEmpty(t)) {
            return t;
        }

        if (width >= gc.textExtent(t).x) {
            return t;
        }

        int w = gc.textExtent("...").x;
        String text = t;
        int l = text.length();
        if (l > 500) l = 500; int pivot = l / 2;
        int s = pivot;
        int e = pivot + 1;

        while (s >= 0 && e < l) {
            String s1 = text.substring(0, s);
            String s2 = text.substring(e, l);
            int l1 = gc.textExtent(s1).x;
            int l2 = gc.textExtent(s2).x;
            if (l1 + w + l2 < width) {
                text = s1 + " ... " + s2;
                break;
            }
            s--;
            e++;
        }

        if (s == 0 || e == l) {
            text = text.substring(0, 1) + "..." + text.substring(l - 1, l);
        }

        return text;
    }

    public static String getShortString(FontMetrics fontMetrics, String t, int width) {

if (CommonUtils.isEmpty(t)) {
            return t;
        }

        if (width <= 1) {
            return ""; }
        double avgCharWidth = fontMetrics.getAverageCharWidth();
        double length = t.length();
        if (width < length * avgCharWidth) {
            length = (float) width / avgCharWidth;
            length *= 2; if (length < t.length()) {
                t = t.substring(0, (int) length);
                }
        }
        return t;
    }

    public static boolean isPointInRectangle(int x, int y, int rectX, int rectY, int rectWidth, int rectHeight)
    {
        return (x >= rectX) && (y >= rectY) && x < (rectX + rectWidth) && y < (rectY + rectHeight);
    }

    public static Point getTextSize(String text) {
        int length = text.length();
        int maxLength = 0;
        int lineCount = 1;
        int lineLength = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n':
                    maxLength = Math.max(maxLength, lineLength);
                    lineCount++;
                    lineLength = 0;
                    break;
                case '\r':
                    break;
                case '\t':
                    lineLength += 4;
                    break;
                default:
                    lineLength++;
                    break;
            }
        }
        maxLength = Math.max(maxLength, lineLength);
        return new Point(maxLength, lineCount);
    }

}
