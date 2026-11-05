package itmo.tracker.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.project.ProjectMemberAddedEvent;
import itmo.events.task.*;
import itmo.tracker.application.model.*;
import itmo.tracker.application.port.out.*;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Component
public class JpaTrackerEvents implements TaskAssignments, TaskStatusChanges, ProjectMemberships {
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JpaTrackerEvents(OutboxRepository outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(TaskAssignment event) {
        var now = clock.instant();
        var id = UUID.randomUUID();
        store(
                id,
                TaskAssignedEvent.TOPIC,
                event.taskId(),
                new TaskAssignedEvent(
                        id,
                        TaskAssignedEvent.TYPE,
                        1,
                        now,
                        event.taskId(),
                        event.version(),
                        event.actorId(),
                        List.of(event.recipientId()),
                        new TaskAssignedEvent.Payload(event.taskKey(), event.projectId())),
                now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(TaskStatusChange event) {
        var now = clock.instant();
        var id = UUID.randomUUID();
        store(
                id,
                TaskStatusChangedEvent.TOPIC,
                event.taskId(),
                new TaskStatusChangedEvent(
                        id,
                        TaskStatusChangedEvent.TYPE,
                        1,
                        now,
                        event.taskId(),
                        event.version(),
                        event.actorId(),
                        List.of(event.recipientId()),
                        new TaskStatusChangedEvent.Payload(
                                event.taskKey(), event.previousStatus(), event.status())),
                now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ProjectMembership event) {
        var now = clock.instant();
        var id = UUID.randomUUID();
        store(
                id,
                ProjectMemberAddedEvent.TOPIC,
                event.projectId(),
                new ProjectMemberAddedEvent(
                        id,
                        ProjectMemberAddedEvent.TYPE,
                        1,
                        now,
                        event.projectId(),
                        null,
                        event.actorId(),
                        List.of(event.recipientId()),
                        new ProjectMemberAddedEvent.Payload(event.memberId(), event.projectName())),
                now);
    }

    private void store(UUID id, String topic, long aggregateId, Object event, Instant now) {
        try {
            outbox.saveAndFlush(
                    new OutboxEntity(
                            id,
                            topic,
                            Long.toString(aggregateId),
                            mapper.writeValueAsString(event),
                            now));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode tracker event", error);
        }
    }
}
