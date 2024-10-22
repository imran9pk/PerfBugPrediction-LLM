package org.ballerinalang.cli.module;

import org.ballerinalang.cli.module.util.ErrorUtil;
import org.ballerinalang.cli.module.util.Utils;
import org.ballerinalang.jvm.JSONParser;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.MapValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Authenticator;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import static org.ballerinalang.cli.module.util.Utils.convertToUrl;
import static org.ballerinalang.cli.module.util.Utils.createHttpsUrlConnection;
import static org.ballerinalang.cli.module.util.Utils.getStatusCode;
import static org.ballerinalang.cli.module.util.Utils.setRequestMethod;

public class Search {

    private static PrintStream outStream = System.out;

    private Search() {
    }

    public static void execute(String url, String proxyHost, int proxyPort, String proxyUsername, String proxyPassword,
            String terminalWidth) {
        HttpsURLConnection conn = createHttpsUrlConnection(convertToUrl(url), proxyHost, proxyPort, proxyUsername,
                                                           proxyPassword);
        conn.setInstanceFollowRedirects(false);
        setRequestMethod(conn, Utils.RequestMethod.GET);
        handleResponse(conn, getStatusCode(conn), terminalWidth);
        Authenticator.setDefault(null);
    }

    private static void handleResponse(HttpsURLConnection conn, int statusCode, String terminalWidth) {
        try {
            MapValue payload;
            if (statusCode == HttpsURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))) {
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    payload = (MapValue) JSONParser.parse(result.toString());
                } catch (IOException e) {
                    throw ErrorUtil.createCommandException(e.getMessage());
                }

                if (payload.getIntValue("count") > 0) {
                    ArrayValue modules = payload.getArrayValue("modules");
                    printModules(modules, terminalWidth);
                } else {
                    outStream.println("no modules found");
                }
            } else {
                StringBuilder result = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                } catch (IOException e) {
                    throw ErrorUtil.createCommandException(e.getMessage());
                }

                payload = (MapValue) JSONParser.parse(result.toString());
                throw ErrorUtil.createCommandException(payload.getStringValue("message"));
            }
        } finally {
            conn.disconnect();
        }
    }

    public static void printModules(ArrayValue modules, String terminalWidth) {
        int rightMargin = 3;
        int width = Integer.parseInt(terminalWidth) - rightMargin;
        int dateColWidth = 15;
        int versionColWidth = 8;
        int authorsColWidth = 15;
        double nameColFactor = 9.0;
        double descColFactor = 16.0;
        int additionalSpace = 7;
        double remainingWidth = (double) width - (dateColWidth + versionColWidth + additionalSpace);
        int nameColWidth = (int) Math.round(remainingWidth * (nameColFactor / (nameColFactor + descColFactor)));
        int descColWidth = (int) Math.round(remainingWidth * (descColFactor / (nameColFactor + descColFactor)));
        int minDescColWidth = 60;

        printTitle();
        printTableHeader(dateColWidth, versionColWidth, nameColWidth, descColWidth, minDescColWidth, authorsColWidth);

        int i = 0;
        while (i < modules.size()) {
            printModule((MapValue) modules.get(i), dateColWidth, versionColWidth, authorsColWidth, nameColWidth,
                    descColWidth, minDescColWidth);
            i = i + 1;
            outStream.println();
        }
        outStream.println();
        outStream.println(modules.size() + " modules found");
    }

    private static void printModule(MapValue module, int dateColWidth, int versionColWidth, int authorsColWidth,
            int nameColWidth, int descColWidth, int minDescColWidth) {
        String orgName = module.getStringValue("orgName");
        String packageName = module.getStringValue("name");
        printInCLI("|" + orgName + "/" + packageName, nameColWidth);

        String summary = module.getStringValue("summary");

        if (descColWidth >= minDescColWidth) {
            printInCLI(summary, descColWidth - authorsColWidth);
            String authors = "";
            ArrayValue authorsArr = module.getArrayValue("authors");

            if (authorsArr.size() > 0) {
                for (int j = 0; j < authorsArr.size(); j++) {
                    if (j == 0) {
                        authors = (String) authorsArr.get(j);
                    } else if (j == authorsArr.size() - 1) {
                        authors = (String) authorsArr.get(j);
                    } else {
                        authors = ", " + authorsArr.get(j);
                    }
                }
            }
            printInCLI(authors, authorsColWidth);
        } else {
            printInCLI(summary, descColWidth);
        }

        long createTimeJson = module.getIntValue("createdDate");
        printInCLI(getDateCreated(createTimeJson), dateColWidth);

        String packageVersion = module.getStringValue("version");
        printInCLI(packageVersion, versionColWidth);
    }

    private static void printTitle() {
        outStream.println();
        outStream.println("Ballerina Central");
        outStream.println("=================");
        outStream.println();
    }

    private static void printInCLI(String element, int charactersAllowed) {
        int lengthOfElement = element.length();
        if (lengthOfElement > charactersAllowed || lengthOfElement == charactersAllowed) {
            int margin = 3;
            String trimmedElement = element.substring(0, charactersAllowed - margin) + "...";
            outStream.print(trimmedElement + " |");
        } else {
            printCharacter(element, charactersAllowed, " ", false);
        }
    }

    private static void printCharacter(String element, int charactersAllowed, String separator, boolean isDashElement) {
        int lengthOfElement = element.length();
        StringBuilder print = new StringBuilder(element);
        int i = 0;
        while (i < charactersAllowed - lengthOfElement) {
            print.append(separator);
            i = i + 1;
        }
        if (isDashElement) {
            outStream.print(print + "-|");
        } else {
            outStream.print(print + " |");
        }
    }

    private static String getDateCreated(long timeInMillis) {
        Date date = new Date(timeInMillis);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd-E");
        return df.format(date);
    }

    private static void printTableHeader(int dateColWidth, int versionColWidth, int nameColWidth, int descColWidth,
            int minDescColWidth, int authorsColWidth) {
        printHeadingRow(dateColWidth, versionColWidth, nameColWidth, descColWidth, minDescColWidth, authorsColWidth);
        printDashRow(dateColWidth, versionColWidth, nameColWidth, descColWidth, minDescColWidth, authorsColWidth);
    }

    private static void printHeadingRow(int dateColWidth, int versionColWidth, int nameColWidth, int descColWidth,
            int minDescColWidth, int authorsColWidth) {
        printInCLI("|NAME", nameColWidth);
        if (descColWidth >= minDescColWidth) {
            printInCLI("DESCRIPTION", descColWidth - authorsColWidth);
            printInCLI("AUTHOR", authorsColWidth);
        } else {
            printInCLI("DESCRIPTION", descColWidth);
        }
        printInCLI("DATE", dateColWidth);
        printInCLI("VERSION", versionColWidth);
        outStream.println();
    }

    private static void printDashRow(int dateColWidth, int versionColWidth, int nameColWidth, int descColWidth,
            int minDescColWidth, int authorsColWidth) {
        printCharacter("|-", nameColWidth, "-", true);

        if (descColWidth >= minDescColWidth) {
            printCharacter("-", descColWidth - authorsColWidth, "-", true);
            printCharacter("-", authorsColWidth, "-", true);
        } else {
            printCharacter("-", descColWidth, "-", true);
        }

        printCharacter("-", dateColWidth, "-", true);
        printCharacter("-", versionColWidth, "-", true);

        outStream.println();
    }
}
