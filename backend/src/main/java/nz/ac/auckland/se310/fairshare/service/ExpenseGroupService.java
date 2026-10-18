package nz.ac.auckland.se310.fairshare.service;


import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.*;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberConflictException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.GroupNotFoundException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseParticipantRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ExpenseGroupService {

    private final ExpenseGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SettlementService settlementService;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseParticipantRepository recurringExpenseParticipantRepository;

    public ExpenseGroupService(ExpenseGroupRepository groupRepository, UserRepository userRepository, SettlementService settlementService,
                                RecurringExpenseRepository recurringExpenseRepository, RecurringExpenseParticipantRepository recurringExpenseParticipantRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.settlementService = settlementService;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.recurringExpenseParticipantRepository = recurringExpenseParticipantRepository;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + creatorId));

        ExpenseGroup group = new ExpenseGroup(
                request.name().trim(),
                request.description(),
                creator.getCurrency(),   // AC: base currency defaults from the creator
                creator);

        return toResponse(groupRepository.save(group));
    }

    // Returns the current user's groups in newest-first order, which matches the usual dashboard flow.
    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsForUser(Long userId) {
        return groupRepository.findByMembersUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long groupId, Long userId) {
        return groupRepository.findByIdAndMembersUserId(groupId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new GroupNotFoundException(groupId));  // AC8
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        Map<Long, BigDecimal> memberBalances = settlementService.getEffectiveBalances(groupId, currentUserId);
        return group.getMembers().stream()
                .map(member -> toMemberResponse(member, currentUserId, memberBalances))
                .sorted((first, second) -> first.username().compareToIgnoreCase(second.username()))
                .toList();
    }


    @Transactional
    public GroupMemberResponse addMember(Long groupId, String identifier, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        User user = findUser(identifier.trim());

        if (group.hasMember(user.getId())) {
            throw new GroupMemberConflictException("User is already a member of this group");
        }

        group.addMember(user);
        groupRepository.save(group);
        Map<Long, BigDecimal> memberBalances = settlementService.getEffectiveBalances(group.getId(), currentUserId);
        return toMemberResponse(group.getMember(user.getId()), currentUserId, memberBalances);
    }

    @Transactional
    public void removeMember(Long groupId, Long memberUserId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId);
        UserInGroup member = group.getMember(memberUserId);

        if (member == null) {
            throw new GroupMemberNotFoundException("Group member not found");
        }
        if (group.getMembers().size() == 1) {
            throw new GroupMemberConflictException("A group must have at least one member");
        }
        if (member.hasOutstandingBalance()) {
            throw new GroupMemberConflictException(
                    "The member's balance must be settled before removal");
        }
        // #13 bug fix: a member who is still the payer or a participant of an active recurring
        // expense can't leave, since generation would otherwise have no one to resolve them to.
        if (recurringExpenseRepository.existsByGroupIdAndPaidBy_IdAndActiveTrue(groupId, memberUserId)
                || recurringExpenseParticipantRepository.existsByRecurringExpense_GroupIdAndUser_IdAndRecurringExpense_ActiveTrue(groupId, memberUserId)) {
            throw new GroupMemberConflictException(
                    "The member is part of an active recurring expense and cannot be removed until it ends");
        }

        group.removeMember(member);
        groupRepository.save(group);
    }

    private ExpenseGroup requireMemberGroup(Long groupId, Long currentUserId) {
        return groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);
    }

    private User findUser(String identifier) {
        return userRepository.findByEmailIgnoreCase(identifier)
                .orElseGet(() -> findUserByUsername(identifier));
    }

    private User findUserByUsername(String username) {
        List<User> matches = userRepository.findAllByUsernameIgnoreCase(username);
        if (matches.isEmpty()) {
            throw new GroupMemberNotFoundException("No matching user was found");
        }
        if (matches.size() > 1) {
            throw new GroupMemberConflictException(
                    "Multiple users match that username; use an email address");
        }
        return matches.getFirst();
    }

    private GroupResponse toResponse(ExpenseGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getGroupName(),
                group.getDescription(),
                group.getBaseCurrency().name(),
                group.getCreatedAt(),
                group.getMembers().size());
    }

    private GroupMemberResponse toMemberResponse(UserInGroup member, Long currentUserId, Map<Long, BigDecimal> memberBalances) {
        User user = member.getUser();
        return new GroupMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                memberBalances.getOrDefault(user.getId(), BigDecimal.ZERO),
                user.getId().equals(currentUserId));
    }

}
