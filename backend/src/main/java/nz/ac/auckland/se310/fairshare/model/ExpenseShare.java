package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table (
        name = "expense_share",
        uniqueConstraints = 
        @UniqueConstraint(name = "uq_es_user_expense", columnNames = {"user_id", "expense_id"}))
public class ExpenseShare {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_share_id")
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @Column(name = "share_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal shareAmount = BigDecimal.ZERO.setScale(2);

    protected ExpenseShare() {} // JPA

    public ExpenseShare(User user, Expense expense, BigDecimal shareAmount) {
        this.user = user;
        this.expense = expense;
        this.shareAmount = shareAmount.setScale(2);
    }

    public long getId() { return id; }
    public User getUser() { return user; }
    public Expense getExpense() { return expense; }
    public BigDecimal getShareAmount() { return shareAmount; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ExpenseShare other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
