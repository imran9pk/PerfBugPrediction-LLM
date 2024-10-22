package org.dcache.util;

import com.google.common.base.CharMatcher;
import java.io.PrintWriter;
import java.io.Writer;

public class NoTrailingWhitespacePrintWriter extends PrintWriter {

    private StringBuilder padding;

    public NoTrailingWhitespacePrintWriter(Writer inner) {
        super(inner);
    }

    private StringBuilder getPadding() {
        if (padding == null) {
            padding = new StringBuilder();
        }
        return padding;
    }

    private void flushPadding() {
        if (padding != null) {
            String pad = padding.toString();
            super.write(pad, 0, pad.length());
            padding = null;
        }
    }

    private int lastTrailingWhitespace(char[] buf, int off, int len) {
        int idx = -1;
        for (int i = off + len - 1; i >= off; i--) {
            if (!CharMatcher.whitespace().matches(buf[i])) {
                break;
            }
            idx = i;
        }
        return idx;
    }

    private int lastTrailingWhitespace(String s, int off, int len) {
        int idx = -1;
        for (int i = off + len - 1; i >= off; i--) {
            if (!CharMatcher.whitespace().matches(s.charAt(i))) {
                break;
            }
            idx = i;
        }
        return idx;
    }

    @Override
    public void println() {
        super.println();
        padding = null;
    }

    @Override
    public void write(String s) {
        write(s, 0, s.length());
    }

    @Override
    public void write(char[] buf) {
        write(buf, 0, buf.length);
    }

    @Override
    public void write(int c) {
        if (CharMatcher.whitespace().matches((char) c)) {
            getPadding().append((char) c);
        } else {
            flushPadding();
            super.write(c);
        }
    }

    @Override
    public void write(char buf[], int off, int len) {
        int idx = lastTrailingWhitespace(buf, off, len);
        if (idx != 0) {
            flushPadding();
            super.write(buf, off, idx == -1 ? len : (idx - off));
        }
        if (idx > -1) {
            getPadding().append(buf, idx, len - (idx - off));
        }
    }

    @Override
    public void write(String s, int off, int len) {
        int idx = lastTrailingWhitespace(s, off, len);
        if (idx != 0) {
            flushPadding();
            super.write(s, off, idx == -1 ? len : (idx - off));
        }
        if (idx > -1) {
            getPadding().append(s, idx, off + len);
        }
    }
}
