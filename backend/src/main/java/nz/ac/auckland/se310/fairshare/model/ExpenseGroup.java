package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name="expense_group")
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    // Historical creator metadata only; roster permissions depend on current membership.
    private User createdBy;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", nullable = false, length = 3)
    private User.Currency baseCurrency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserInGroup> members = new HashSet<>();

    protected ExpenseGroup() {} // JPA

    // A group is the container for all members, shared expenses, and any settlement activity.
    public ExpenseGroup(String groupName, String description, User.Currency baseCurrency, User createdBy) {
        this.groupName = groupName;
        this.description = description;
        this.baseCurrency = baseCurrency;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        addMember(createdBy); // AC1: the creator is always a member
    }

    public void addMember(User user) {
        if (hasMember(user.getId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }
        members.add(new UserInGroup(this, user));
    }

    public UserInGroup getMember(Long userId) {
        return members.stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    public boolean hasMember(Long userId) {
        return getMember(userId) != null;
    }

    public void removeMember(UserInGroup member) {
        if (members.size() == 1) {
            throw new IllegalStateException("A group must have at least one member");
        }
        members.remove(member);
    }

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public String getDescription() { return description; }
    public User.Currency getBaseCurrency() { return baseCurrency; }
    public Instant getCreatedAt() { return createdAt; }
    public User getCreatedBy() { return createdBy; }

    public Set<UserInGroup> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseGroup other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
