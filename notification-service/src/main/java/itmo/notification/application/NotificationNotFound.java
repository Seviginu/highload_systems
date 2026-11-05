package itmo.notification.application;

public class NotificationNotFound extends RuntimeException {
    public NotificationNotFound() {
        super("Notification was not found");
    }
}
