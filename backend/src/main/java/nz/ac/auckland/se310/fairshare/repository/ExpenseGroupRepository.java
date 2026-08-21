package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {

    // AC8: only returns the group if the requesting user is a member
    Optional<ExpenseGroup> findByIdAndMembersUserId(Long groupId, Long userId);

    // AC5: the user's groups overview, newest first
    List<ExpenseGroup> findByMembersUserIdOrderByCreatedAtDesc(Long userId);
}