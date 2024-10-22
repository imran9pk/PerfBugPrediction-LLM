package org.dcache.boot;

import com.google.common.base.Strings;
import java.io.PrintStream;
import org.dcache.util.configuration.AnnotatedKey;
import org.dcache.util.configuration.Annotation;
import org.dcache.util.configuration.ConfigurationProperties;

public class ShellOracleLayoutPrinter implements LayoutPrinter {

    private final Layout _layout;

    public ShellOracleLayoutPrinter(Layout layout) {
        _layout = layout;
    }

    @Override
    public void print(PrintStream out) {
        out.println("getProperty()");
        out.println("{");

        out.println("  case \"$2\" in");
        out.println("    \"\")");
        out.println("      ;;"); for (Domain domain : _layout.getDomains()) {
            out.append("    ").append(quoteForCase(domain.getName())).println(")");
            out.println("      case \"$3\" in");
            out.println("        \"\")");
            out.println("          ;;"); for (ConfigurationProperties service : domain.getServices()) {
                String cellName = Properties.getCellName(service);
                if (!Strings.isNullOrEmpty(cellName)) {
                    out.append("        ").append(quoteForCase(cellName)).println(")");
                    compile(out, "          ", service, domain.properties());
                    out.println("          ;;");
                }
            }
            out.println("        *)");
            out.println("          undefinedCell \"$@\"");
            out.println("          ;;");
            out.println("      esac");
            out.println("      ;;");
        }
        out.println("    *)");
        out.println("      undefinedDomain \"$@\"");
        out.println("      ;;");
        out.println("  esac");
        out.println();

        out.println("  case \"$2\" in");
        for (Domain domain : _layout.getDomains()) {
            out.append("    ").append(quoteForCase(domain.getName())).println(")");
            compile(out, "      ", domain.properties(), _layout.properties());
            out.println("      ;;");
        }
        out.println("  esac");
        out.println();

        compile(out, "  ", _layout.properties(), new ConfigurationProperties());
        out.println();

        out.println("  undefinedProperty \"$@\"");
        out.println("}");
    }

    private static String quote(String s) {
        char[] output = new char[2 * s.length()];
        int len = 0;
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\':
                case '$':
                case '`':
                case '"':
                    output[len++] = '\\';
                    break;
            }
            output[len++] = c;
        }
        return new String(output, 0, len);
    }

    private static String quoteForCase(String s) {
        char[] output = new char[2 * s.length()];
        int len = 0;
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\':
                case '$':
                case '`':
                case '"':
                case ')':
                case '?':
                case '*':
                case '[':
                case ' ':
                    output[len++] = '\\';
                    break;
            }
            output[len++] = c;
        }
        return new String(output, 0, len);
    }

    private static void compile(PrintStream out, String indent,
          ConfigurationProperties properties,
          ConfigurationProperties parentProperties) {
        out.append(indent).println("case \"$1\" in");
        for (String key : properties.stringPropertyNames()) {
            AnnotatedKey annotatedKey = properties.getAnnotatedKey(key);
            if (annotatedKey == null ||
                  !annotatedKey.hasAnnotation(Annotation.DEPRECATED)) {
                String value = properties.getValue(key);
                if (!value.equals(parentProperties.getValue(key))) {
                    out.append(indent).append("  ");
                    out.append(quoteForCase(key)).append(") echo \"");
                    out.append(quote(value.trim()));
                    out.println("\"; return;;");
                }
            }
        }
        out.append(indent).println("esac");
    }
}
