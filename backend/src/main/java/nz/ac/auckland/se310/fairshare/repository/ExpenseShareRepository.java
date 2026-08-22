package nz.ac.auckland.se310.fairshare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpenseId(Long expenseId);
}
