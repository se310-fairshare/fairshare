package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "user_in_group",
        uniqueConstraints =
        @UniqueConstraint(name = "uq_uig_user_group", columnNames = {"user_id", "group_id"}))
public class UserInGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_in_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @Column(name = "net_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal netBalance = BigDecimal.ZERO.setScale(2);

    protected UserInGroup() {} // JPA

    UserInGroup(ExpenseGroup group, User user) {
        this.group = group;
        this.user = user;
        this.netBalance = BigDecimal.ZERO.setScale(2);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public ExpenseGroup getGroup() { return group; }
    public BigDecimal getNetBalance() { return netBalance; }

    public boolean hasOutstandingBalance() {
        return netBalance.compareTo(BigDecimal.ZERO) != 0;
    }

    // Positive means the member is owed, negative means they owe.
    public void adjustNetBalance(BigDecimal delta) {
        this.netBalance = this.netBalance.add(delta).setScale(2);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UserInGroup other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
