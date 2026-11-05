package itmo.tracker.adapter.out.persistence;

import itmo.tracker.domain.*;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PersistenceMapper {
    public Project toDomain(ProjectEntity entity) {
        return new Project(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public Label toDomain(LabelEntity entity) {
        return new Label(
                entity.getId(), entity.getName(), entity.getColor(), entity.getCreatedAt());
    }

    public ProjectMember toDomain(ProjectMemberEntity entity) {
        return new ProjectMember(
                entity.getId(),
                toDomain(entity.getProject()),
                entity.getUserId(),
                entity.getJoinedAt(),
                entity.isActive());
    }

    public Task toDomain(TaskEntity entity) {
        return new Task(
                entity.getId(),
                entity.getTaskKey(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getAuthorId(),
                entity.getAssigneeId(),
                toDomain(entity.getProject()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLabels().stream().map(this::toDomain).collect(Collectors.toSet()));
    }
}
