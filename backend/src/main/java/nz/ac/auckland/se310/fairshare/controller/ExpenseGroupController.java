package nz.ac.auckland.se310.fairshare.controller;

import jakarta.validation.Valid;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.GroupMemberResponse;
import nz.ac.auckland.se310.fairshare.dto.GroupResponse;
import nz.ac.auckland.se310.fairshare.dto.ManageGroupMemberRequest;
import nz.ac.auckland.se310.fairshare.dto.MemberBalance;
import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import nz.ac.auckland.se310.fairshare.dto.SettlementRequest;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/groups")
public class ExpenseGroupController {

    private final ExpenseGroupService groupService;
    private final CurrentUserProvider currentUser;

    public ExpenseGroupController(ExpenseGroupService groupService, CurrentUserProvider currentUser) {
        this.groupService = groupService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse created = groupService.createGroup(request, currentUser.currentUserId());
        return ResponseEntity
                .created(URI.create("/groups/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<GroupResponse> list() {
        return groupService.getGroupsForUser(currentUser.currentUserId());
    }

    @GetMapping("/{id}")
    public GroupResponse get(@PathVariable Long id) {
        return groupService.getGroup(id, currentUser.currentUserId());
    }

    @GetMapping("/{id}/members")
    public List<GroupMemberResponse> members(@PathVariable Long id) {
        return groupService.getMembers(id, currentUser.currentUserId());
    }

    @GetMapping("/{id}/balances")
    public List<MemberBalance> balances(@PathVariable Long id) {
        return groupService.getBalances(id, currentUser.currentUserId());
    }

    @PostMapping("/{id}/settlement")
    public List<SettlementLine> settlement(
            @PathVariable Long id,
            @RequestBody SettlementRequest request) {
        return groupService.computeSettlement(id, currentUser.currentUserId(), request);
    }

    @PatchMapping("/{id}/settlements/{fromUserId}/{toUserId}/paid")
    public ResponseEntity<Void> markSettlementPaid(
            @PathVariable Long id,
            @PathVariable Long fromUserId,
            @PathVariable Long toUserId) {
        // This marks a pending settlement as completed from the perspective of the current user.
        groupService.markSettlementPaid(id, fromUserId, toUserId, currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<GroupMemberResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody ManageGroupMemberRequest request) {
        GroupMemberResponse member = groupService.addMember(
                id, request.identifier(), currentUser.currentUserId());
        return ResponseEntity
                .created(URI.create("/groups/" + id + "/members/" + member.userId()))
                .body(member);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        groupService.removeMember(id, userId, currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
