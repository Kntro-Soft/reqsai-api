package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.CreateMemberCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteMemberCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetMemberQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListMembersQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetMemberQuery;
import com.kntro.reqsai.workspace.application.query.ListMembersQuery;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MemberResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.MemberRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.MemberResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.MemberController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    private final DeleteMemberCommandHandler deleteMember;

    @Override
    public ResponseEntity<MemberResponse> createMember(UUID orgId, CreateMemberRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Member member = createMember.handle(MemberRequestMapper.toCommand(orgId, request, requestedBy));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(member.getId()).toUri();
        return ResponseEntity.created(location).body(MemberResponseMapper.toResponse(member));
    }

    @Override
    public ResponseEntity<List<MemberResponse>> listMembers(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(listMembers.handle(new ListMembersQuery(orgId, requestedBy)).stream()
                .map(MemberResponseMapper::toResponse).toList());
    }

    @Override
    public ResponseEntity<MemberResponse> getMember(UUID orgId, UUID memberId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(MemberResponseMapper.toResponse(getMember.handle(new GetMemberQuery(orgId, memberId, requestedBy))));
    }

    @Override
    public ResponseEntity<Void> deleteMember(UUID orgId, UUID memberId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteMember.handle(MemberRequestMapper.toDeleteCommand(orgId, memberId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
