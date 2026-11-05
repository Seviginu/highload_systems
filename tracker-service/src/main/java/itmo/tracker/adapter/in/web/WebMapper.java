package itmo.tracker.adapter.in.web;

import itmo.tracker.application.model.*;

public final class WebMapper {
    private WebMapper() {}

    public static UpdateLabelCommand toCommand(UpdateLabelRequest value) {
        return new UpdateLabelCommand(value.name(), value.color());
    }

    public static CreateLabelCommand toCommand(CreateLabelRequest value) {
        return new CreateLabelCommand(value.name(), value.color());
    }

    public static LabelResponse toResponse(LabelView value) {
        return new LabelResponse(value.id(), value.name(), value.color(), value.createdAt());
    }

    public static UpdateProjectCommand toCommand(UpdateProjectRequest value) {
        return new UpdateProjectCommand(
                value.name(), value.code(), value.description(), value.status());
    }

    public static ProjectMemberResponse toResponse(ProjectMemberView value) {
        return new ProjectMemberResponse(
                value.id(), value.projectId(), value.userId(), value.joinedAt(), value.active());
    }

    public static ProjectResponse toResponse(ProjectView value) {
        return new ProjectResponse(
                value.id(),
                value.name(),
                value.code(),
                value.description(),
                value.status(),
                value.createdAt(),
                value.updatedAt());
    }

    public static CreateProjectCommand toCommand(CreateProjectRequest value) {
        return new CreateProjectCommand(
                value.name(),
                value.code(),
                value.description(),
                value.status(),
                value.teamLeadId());
    }

    public static AddProjectMemberCommand toCommand(AddProjectMemberRequest value) {
        return new AddProjectMemberCommand(value.userId());
    }

    public static TaskFeedResponse toResponse(TaskFeedView value) {
        return new TaskFeedResponse(
                value.tasks().stream().map(WebMapper::toResponse).toList(), value.nextCursor());
    }

    public static ChangeTaskStatusCommand toCommand(ChangeTaskStatusRequest value) {
        return new ChangeTaskStatusCommand(value.status(), value.version());
    }

    public static TaskResponse toResponse(TaskView value) {
        return new TaskResponse(
                value.id(),
                value.taskKey(),
                value.title(),
                value.description(),
                value.status(),
                value.priority(),
                value.authorId(),
                value.assigneeId(),
                value.projectId(),
                value.labelIds(),
                value.version(),
                value.createdAt(),
                value.updatedAt());
    }

    public static MoveTaskCommand toCommand(MoveTaskRequest value) {
        return new MoveTaskCommand(
                value.projectId(), value.assigneeId(), value.labelIds(), value.version());
    }

    public static CreateTaskCommand toCommand(CreateTaskRequest value) {
        return new CreateTaskCommand(
                value.taskKey(),
                value.title(),
                value.description(),
                value.status(),
                value.priority(),
                value.assigneeId(),
                value.projectId(),
                value.labelIds());
    }

    public static UpdateTaskCommand toCommand(UpdateTaskRequest value) {
        return new UpdateTaskCommand(
                value.taskKey(),
                value.title(),
                value.description(),
                value.status(),
                value.priority(),
                value.assigneeId(),
                value.projectId(),
                value.labelIds(),
                value.version());
    }
}
