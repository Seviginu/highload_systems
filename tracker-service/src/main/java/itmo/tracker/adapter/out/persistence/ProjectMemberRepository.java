package itmo.tracker.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {

    Page<ProjectMemberEntity> findAllByProjectId(Long projectId, Pageable pageable);

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMemberEntity> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndUserIdAndActiveTrue(Long projectId, Long userId);
}
