package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // AC7: newest first for the group's expense list
    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);
}
