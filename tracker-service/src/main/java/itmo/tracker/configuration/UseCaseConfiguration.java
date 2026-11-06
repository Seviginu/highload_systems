package itmo.tracker.configuration;

import itmo.tracker.application.mapper.*;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.*;
import itmo.tracker.application.service.*;
import itmo.tracker.application.usecase.CreateTaskUseCase;
import itmo.tracker.application.usecase.ChangeTaskStatusUseCase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    TrackerAccess trackerAccess(ProjectMemberStore members) {
        return new TrackerAccess(members);
    }

    @Bean
    LabelService labelService(TrackerAccess access, LabelStore labels, UnitOfWork transactions) {
        return new LabelServiceImpl(access, labels, new LabelMapper(), transactions);
    }

    @Bean
    ProjectMemberService projectMemberService(
            TrackerAccess access,
            ProjectMemberStore members,
            ProjectStore projects,
            UserDirectoryPort users,
            UnitOfWork transactions,
            ProjectMemberships events) {
        return new ProjectMemberServiceImpl(
                access, members, projects, users, transactions, new ProjectMemberMapper(), events);
    }

    @Bean
    ProjectService projectService(
            TrackerAccess access,
            ProjectStore projects,
            ProjectMemberService members,
            UserDirectoryPort users,
            UnitOfWork transactions) {
        return new ProjectServiceImpl(
                access, projects, new ProjectMapper(), members, users, transactions);
    }

    @Bean
    CreateTaskUseCase createTaskUseCase(
            TrackerAccess access,
            TaskStore tasks,
            ProjectService projects,
            ProjectMemberService members,
            UserDirectoryPort users,
            UnitOfWork transactions,
            LabelService labels,
            TaskAssignments assignments) {
        return new CreateTaskUseCase(
                access, tasks, new TaskMapper(), projects, members, users,
                transactions, labels, assignments);
    }

    @Bean
    ChangeTaskStatusUseCase changeTaskStatusUseCase(
            TrackerAccess access,
            TaskStore tasks,
            UnitOfWork transactions,
            TaskStatusChanges statusChanges) {
        return new ChangeTaskStatusUseCase(
                access, tasks, new TaskMapper(), transactions, statusChanges);
    }

    @Bean
    TaskService taskService(
            TrackerAccess access,
            TaskStore tasks,
            ProjectService projects,
            ProjectMemberService members,
            UserDirectoryPort users,
            UnitOfWork transactions,
            LabelService labels,
            TaskAssignments assignments,
            TaskStatusChanges statusChanges,
            CreateTaskUseCase createTask,
            ChangeTaskStatusUseCase changeTaskStatus) {
        return new CachedTaskService(
                new TaskServiceImpl(
                        access,
                        tasks,
                        new TaskMapper(),
                        projects,
                        members,
                        users,
                        transactions,
                        labels,
                        assignments,
                        statusChanges,
                        createTask,
                        changeTaskStatus));
    }
}
