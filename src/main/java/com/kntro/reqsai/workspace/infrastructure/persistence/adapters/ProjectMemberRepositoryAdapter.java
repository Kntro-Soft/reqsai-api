package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.ProjectMemberRepository;
import com.kntro.reqsai.workspace.domain.model.ProjectMember;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.ProjectMemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final ProjectMemberJpaRepository jpa;

    @Override
    public ProjectMember save(ProjectMember assignment) {
        return jpa.save(assignment);
    }

    @Override
    public Optional<ProjectMember> findByIdAndProjectId(UUID id, UUID projectId) {
        return jpa.findByIdAndProjectId(id, projectId);
    }

    @Override
    public List<ProjectMember> findAllByProjectId(UUID projectId) {
        return jpa.findAllByProjectId(projectId);
    }

    @Override
    public List<ProjectMember> findAllByMemberId(UUID memberId) {
        return jpa.findAllByMemberId(memberId);
    }

    @Override
    public boolean existsByProjectIdAndMemberId(UUID projectId, UUID memberId) {
        return jpa.existsByProjectIdAndMemberId(projectId, memberId);
    }

    @Override
    public long countByProjectIdAndRoleId(UUID projectId, UUID roleId) {
        return jpa.countByProjectIdAndRoleId(projectId, roleId);
    }

    @Override
    public void delete(ProjectMember assignment) {
        jpa.delete(assignment);
    }
}
