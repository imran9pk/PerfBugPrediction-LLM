package org.jkiss.dbeaver.runtime;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPMessageType;

public class DBeaverNotifications
{

    public static final String NT_COMMIT = "commit";
    public static final String NT_ROLLBACK = "rollback";
    public static final String NT_RECONNECT = "reconnect";
    public static final String NT_GENERAL = "generalInfo";

    @NotNull
    private static NotificationHandler notificationHandler = new ConsoleHandler();

    public static void showNotification(DBPDataSource dataSource, String id, String text) {
        showNotification(dataSource, id, text, null, null);
    }

    public static void showNotification(DBPDataSource dataSource, String id, String text, DBPMessageType messageType) {
        showNotification(dataSource, id, text, messageType, null);
    }

    public static void showNotification(DBPDataSource dataSource, String id, String text, DBPMessageType messageType, Runnable feedback) {
        notificationHandler.sendNotification(dataSource, id, text, messageType, feedback);
    }

    public static void showNotification(String id, String title, String text, DBPMessageType messageType, Runnable feedback) {
        notificationHandler.sendNotification(id, title, text, messageType, feedback);
    }

    public static void setHandler(@NotNull NotificationHandler handler) {
        notificationHandler = handler;
    }

    public interface NotificationHandler {

        void sendNotification(DBPDataSource dataSource, String id, String text, DBPMessageType messageType, Runnable feedback);

        void sendNotification(String id, String title, String text, DBPMessageType messageType, Runnable feedback);

    }

    private static class ConsoleHandler implements NotificationHandler {

        @Override
        public void sendNotification(DBPDataSource dataSource, String id, String text, DBPMessageType messageType, Runnable feedback) {
            System.out.println(text);
        }

        @Override
        public void sendNotification(String id, String title, String text, DBPMessageType messageType, Runnable feedback) {
            System.out.println(title + "\n\t" + text);
        }
    }
}
