package itmo.tracker.configuration;

import itmo.tracker.application.mapper.*;
import itmo.tracker.application.port.in.*;
import itmo.tracker.application.port.out.*;
import itmo.tracker.application.service.*;

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
            UnitOfWork transactions) {
        return new ProjectMemberServiceImpl(
                access, members, projects, users, transactions, new ProjectMemberMapper());
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
    TaskService taskService(
            TrackerAccess access,
            TaskStore tasks,
            ProjectService projects,
            ProjectMemberService members,
            UserDirectoryPort users,
            UnitOfWork transactions,
            LabelService labels,
            TaskAssignments assignments) {
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
                        assignments));
    }
}
