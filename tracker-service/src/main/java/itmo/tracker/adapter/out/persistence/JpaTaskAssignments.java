package itmo.tracker.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import itmo.events.task.TaskAssignedEvent;
import itmo.tracker.application.model.TaskAssignment;
import itmo.tracker.application.port.out.TaskAssignments;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
public class JpaTaskAssignments implements TaskAssignments {
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JpaTaskAssignments(OutboxRepository outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(TaskAssignment assignment) {
        var now = clock.instant();
        var event =
                new TaskAssignedEvent(
                        UUID.randomUUID(),
                        TaskAssignedEvent.TYPE,
                        1,
                        now,
                        assignment.taskId(),
                        assignment.version(),
                        assignment.actorId(),
                        List.of(assignment.recipientId()),
                        new TaskAssignedEvent.Payload(
                                assignment.taskKey(), assignment.projectId()));
        try {
            outbox.saveAndFlush(
                    new OutboxEntity(
                            event.eventId(),
                            TaskAssignedEvent.TOPIC,
                            Long.toString(assignment.taskId()),
                            mapper.writeValueAsString(event),
                            now));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode TaskAssigned event", error);
        }
    }
}
