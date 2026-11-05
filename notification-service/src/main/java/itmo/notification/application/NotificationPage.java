package itmo.notification.application;

import itmo.notification.domain.Notification;

import java.util.List;

public record NotificationPage(List<Notification> content, long total) {
    public NotificationPage {
        content = List.copyOf(content);
    }
}
