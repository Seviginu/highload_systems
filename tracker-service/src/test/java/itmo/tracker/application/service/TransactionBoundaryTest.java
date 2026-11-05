package itmo.tracker.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import itmo.tracker.application.mapper.ProjectMapper;
import itmo.tracker.application.model.CreateProjectCommand;
import itmo.tracker.application.port.in.ProjectMemberService;
import itmo.tracker.application.port.out.*;
import itmo.tracker.domain.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class TransactionBoundaryTest {
    @Test
    void shouldValidateRemoteTeamLeadBeforeAtomicProjectAndMembershipWrite() {
        var active = new AtomicBoolean();
        UnitOfWork transactions =
                new UnitOfWork() {
                    public <T> T write(Supplier<T> operation) {
                        assertThat(active.get()).isFalse();
                        active.set(true);
                        try {
                            return operation.get();
                        } finally {
                            active.set(false);
                        }
                    }

                    public <T> T read(Supplier<T> operation) {
                        return operation.get();
                    }
                };
        var users = mock(UserDirectoryPort.class);
        var projects = mock(ProjectStore.class);
        var members = mock(ProjectMemberService.class);
        doAnswer(
                        call -> {
                            assertThat(active.get()).isFalse();
                            return null;
                        })
                .when(users)
                .requireTeamLead(2L);
        when(projects.save(any()))
                .thenAnswer(
                        call -> {
                            assertThat(active.get()).isTrue();
                            return new Project(
                                    1L,
                                    "Platform",
                                    "PLATFORM",
                                    null,
                                    ProjectStatus.ACTIVE,
                                    null,
                                    null);
                        });
        when(members.assignValidatedTeamLead(1L, 2L))
                .thenAnswer(
                        call -> {
                            assertThat(active.get()).isTrue();
                            return null;
                        });
        var service =
                new ProjectServiceImpl(
                        itmo.support.TestActors.access(),
                        projects,
                        new ProjectMapper(),
                        members,
                        users,
                        transactions);
        service.create(
                new CreateProjectCommand("Platform", "PLATFORM", null, ProjectStatus.ACTIVE, 2L),
                itmo.support.TestActors.admin());
        var order = inOrder(users, projects, members);
        order.verify(users).requireTeamLead(2L);
        order.verify(projects).existsByCode("PLATFORM");
        order.verify(projects).save(any());
        order.verify(members).assignValidatedTeamLead(1L, 2L);
        assertThat(active.get()).isFalse();
    }
}
