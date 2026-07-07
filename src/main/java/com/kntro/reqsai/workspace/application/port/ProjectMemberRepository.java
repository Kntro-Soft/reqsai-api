package com.kntro.reqsai.workspace.application.port;

import com.kntro.reqsai.workspace.domain.model.ProjectMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository {
    ProjectMember save(ProjectMember assignment);
    Optional<ProjectMember> findByIdAndProjectId(UUID id, UUID projectId);
    List<ProjectMember> findAllByProjectId(UUID projectId);
    List<ProjectMember> findAllByMemberId(UUID memberId);
    boolean existsByProjectIdAndMemberId(UUID projectId, UUID memberId);
    long countByProjectIdAndRoleId(UUID projectId, UUID roleId);
    void delete(ProjectMember assignment);
}
