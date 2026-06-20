package com.kntro.reqsai.workspace.infrastructure.persistence.repositories;

import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMember, UUID> {
    Optional<ProjectMember> findByIdAndProjectId(UUID id, UUID projectId);
    List<ProjectMember> findAllByProjectId(UUID projectId);
    boolean existsByProjectIdAndMemberId(UUID projectId, UUID memberId);
}
