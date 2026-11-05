package itmo.notification.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.task.TaskAssignedEvent;
import itmo.notification.application.*;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskAssignmentListener {
    private final ObjectMapper mapper;
    private final NotificationService notifications;

    public TaskAssignmentListener(ObjectMapper mapper, NotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = TaskAssignedEvent.TOPIC,
            groupId = "${app.notifications.group-id:notifications-task-assigned-v1}",
            autoStartup = "${app.notifications.listener-enabled:true}")
    public void receive(String message) {
        try {
            var event = mapper.readValue(message, TaskAssignedEvent.class);
            notifications.receive(
                    new AssignmentReceived(
                            event.eventId(),
                            event.aggregateId(),
                            event.payload().taskKey(),
                            event.recipientIds()));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid task event", error);
        }
    }
}
