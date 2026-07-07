package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.BatchInviteMembersCommandHandler;
import com.kntro.reqsai.workspace.application.handler.ChangeMemberRoleCommandHandler;
import com.kntro.reqsai.workspace.application.handler.ChangeMemberStatusCommandHandler;
import com.kntro.reqsai.workspace.application.handler.CreateMemberCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteMemberCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetMemberQueryHandler;
import com.kntro.reqsai.workspace.application.handler.LeaveOrganizationCommandHandler;
import com.kntro.reqsai.workspace.application.handler.ListMembersQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ResendInvitationCommandHandler;
import com.kntro.reqsai.workspace.application.command.ResendInvitationCommand;
import com.kntro.reqsai.workspace.application.query.GetMemberQuery;
import com.kntro.reqsai.workspace.application.query.ListMembersQuery;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.BatchInviteMembersRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ChangeMemberRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ChangeMemberStatusRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MemberResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.MemberRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.MemberResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.MemberController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MemberControllerImpl implements MemberController {

    private final CreateMemberCommandHandler createMember;
    private final ListMembersQueryHandler listMembers;
    private final GetMemberQueryHandler getMember;
    private final ChangeMemberRoleCommandHandler changeMemberRole;
    private final DeleteMemberCommandHandler deleteMember;
    private final ChangeMemberStatusCommandHandler changeMemberStatus;
    private final BatchInviteMembersCommandHandler batchInvite;
    private final LeaveOrganizationCommandHandler leaveOrganization;
    private final ResendInvitationCommandHandler resendInvitation;

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<MemberResponse> createMember(UUID orgId, CreateMemberRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Member member = createMember.handle(MemberRequestMapper.toCommand(orgId, request, requestedBy));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(member.getId()).toUri();
        return ResponseEntity.created(location).body(MemberResponseMapper.toResponse(member));
    }

    @Override
    @PreAuthorize("@authz.orgMember(#orgId, authentication)")
    public ResponseEntity<List<MemberResponse>> listMembers(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(listMembers.handle(new ListMembersQuery(orgId, requestedBy)).stream()
                .map(MemberResponseMapper::toResponse).toList());
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<MemberResponse> getMember(UUID orgId, UUID memberId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(MemberResponseMapper.toResponse(getMember.handle(new GetMemberQuery(orgId, memberId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<MemberResponse> changeMemberRole(UUID orgId, UUID memberId, ChangeMemberRoleRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Member member = changeMemberRole.handle(MemberRequestMapper.toChangeRoleCommand(orgId, memberId, request, requestedBy));
        return ResponseEntity.ok(MemberResponseMapper.toResponse(member));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<Void> deleteMember(UUID orgId, UUID memberId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteMember.handle(MemberRequestMapper.toDeleteCommand(orgId, memberId, requestedBy));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<MemberResponse> changeMemberStatus(UUID orgId, UUID memberId, ChangeMemberStatusRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Member member = changeMemberStatus.handle(MemberRequestMapper.toChangeStatusCommand(orgId, memberId, request, requestedBy));
        return ResponseEntity.ok(MemberResponseMapper.toResponse(member));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<List<MemberResponse>> batchInvite(UUID orgId, BatchInviteMembersRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<MemberResponse> created = batchInvite.handle(MemberRequestMapper.toBatchInviteCommand(orgId, request, requestedBy))
                .stream().map(MemberResponseMapper::toResponse).toList();
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<MemberResponse> resendInvitation(UUID orgId, UUID memberId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Member member = resendInvitation.handle(new ResendInvitationCommand(orgId, memberId, requestedBy));
        return ResponseEntity.ok(MemberResponseMapper.toResponse(member));
    }

    @Override
    @PreAuthorize("@authz.orgMember(#orgId, authentication)")
    public ResponseEntity<Void> leaveOrganization(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        leaveOrganization.handle(MemberRequestMapper.toLeaveCommand(orgId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
