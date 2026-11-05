package itmo.notification.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.file.FileUploadedEvent;
import itmo.events.project.ProjectMemberAddedEvent;
import itmo.events.task.TaskAssignedEvent;
import itmo.events.task.TaskStatusChangedEvent;
import itmo.notification.application.*;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TrackerEventListener {
    private final ObjectMapper mapper;
    private final NotificationService notifications;

    public TrackerEventListener(ObjectMapper mapper, NotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @KafkaListener(
            topics = {
                TaskAssignedEvent.TOPIC,
                ProjectMemberAddedEvent.TOPIC,
                FileUploadedEvent.TOPIC
            },
            groupId = "${app.notifications.group-id:notifications-task-assigned-v1}",
            autoStartup = "${app.notifications.listener-enabled:true}")
    public void receive(ConsumerRecord<String, String> message) {
        try {
            var json = mapper.readTree(message.value());
            if (json == null || !json.isObject())
                throw new IllegalArgumentException("Expected event object");
            switch (json.path("eventType").asText()) {
                case TaskAssignedEvent.TYPE -> {
                    requireTopic(message, TaskAssignedEvent.TOPIC);
                    var e = mapper.treeToValue(json, TaskAssignedEvent.class);
                    notifications.receive(
                            new AssignmentReceived(
                                    e.eventId(),
                                    e.aggregateId(),
                                    e.payload().taskKey(),
                                    e.recipientIds()));
                }
                case TaskStatusChangedEvent.TYPE -> {
                    requireTopic(message, TaskStatusChangedEvent.TOPIC);
                    var e = mapper.treeToValue(json, TaskStatusChangedEvent.class);
                    notifications.receive(
                            new StatusChangedReceived(
                                    e.eventId(),
                                    e.aggregateId(),
                                    e.payload().taskKey(),
                                    e.payload().previousStatus(),
                                    e.payload().status(),
                                    e.recipientIds()));
                }
                case ProjectMemberAddedEvent.TYPE -> {
                    requireTopic(message, ProjectMemberAddedEvent.TOPIC);
                    var e = mapper.treeToValue(json, ProjectMemberAddedEvent.class);
                    notifications.receive(
                            new ProjectMemberReceived(
                                    e.eventId(),
                                    e.aggregateId(),
                                    e.payload().projectName(),
                                    e.recipientIds()));
                }
                case FileUploadedEvent.TYPE -> {
                    requireTopic(message, FileUploadedEvent.TOPIC);
                    var e = mapper.treeToValue(json, FileUploadedEvent.class);
                    notifications.receive(
                            new FileUploadedReceived(
                                    e.eventId(), e.aggregateId(), e.recipientIds()));
                }
                default -> throw new IllegalArgumentException("Unsupported event type");
            }
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid tracker event", error);
        }
    }

    private void requireTopic(ConsumerRecord<String, String> message, String expected) {
        if (!expected.equals(message.topic()))
            throw new IllegalArgumentException("Unexpected event topic");
    }
}
