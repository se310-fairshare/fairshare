package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.GroupMemberResponse;
import nz.ac.auckland.se310.fairshare.dto.GroupResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberConflictException;
import nz.ac.auckland.se310.fairshare.exception.GroupMemberNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.GroupNotFoundException;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestCurrentUserConfig.class)
class ExpenseGroupIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired ExpenseGroupService groupService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void setUp() {
        groupRepository.deleteAll();
        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
    }

    @Test
    void ac1_createsGroupWithCreatorAsMember() {
        GroupResponse created = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId);

        assertThat(created.id()).isNotNull();
        assertThat(created.memberCount()).isEqualTo(1);
        assertThat(groupRepository.findByIdAndMembersUserId(created.id(), aliceId)).isPresent();
    }

    @Test
    void ac5_overviewListsOnlyGroupsTheUserBelongsTo() {
        groupService.createGroup(new CreateGroupRequest("Alice one", null), aliceId);
        groupService.createGroup(new CreateGroupRequest("Alice two", null), aliceId);
        groupService.createGroup(new CreateGroupRequest("Bob one", null), bobId);

        assertThat(groupService.getGroupsForUser(aliceId))
                .hasSize(2)
                .extracting(GroupResponse::name)
                .containsExactlyInAnyOrder("Alice one", "Alice two");

        assertThat(groupService.getGroupsForUser(bobId)).hasSize(1);
    }

    @Test
    void ac6_duplicateNamesGetDistinctIdentifiers() {
        GroupResponse first = groupService.createGroup(new CreateGroupRequest("Trip", null), aliceId);
        GroupResponse second = groupService.createGroup(new CreateGroupRequest("Trip", null), aliceId);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(groupService.getGroupsForUser(aliceId)).hasSize(2);
    }

    @Test
    void ac8_nonMemberCannotReadGroup() {
        GroupResponse created = groupService.createGroup(new CreateGroupRequest("Alice's flat", null), aliceId);

        Long groupId = created.id();

        assertThatThrownBy(() -> groupService.getGroup(groupId, bobId))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void manageAc1_addsRegisteredMemberByEmailAndListsThem() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);

        GroupMemberResponse added = groupService.addMember(
                created.id(), "BOB@test.com", aliceId);

        assertThat(added.userId()).isEqualTo(bobId);
        assertThat(added.netBalance()).isZero();
        assertThat(groupService.getMembers(created.id(), aliceId))
                .extracting(GroupMemberResponse::username)
                .containsExactly("alice", "bob");
        assertThat(groupRepository.findByIdAndMembersUserId(created.id(), bobId)).isPresent();
    }

    @Test
    void manageAc1_addsRegisteredMemberByUsername() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);

        GroupMemberResponse added = groupService.addMember(created.id(), "BoB", aliceId);

        assertThat(added.userId()).isEqualTo(bobId);
    }

    @Test
    void manageAc2_rejectsDuplicateMember() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);
        groupService.addMember(created.id(), "bob@test.com", aliceId);

        assertThatThrownBy(() -> groupService.addMember(
                created.id(), "bob@test.com", aliceId))
                .isInstanceOf(GroupMemberConflictException.class)
                .hasMessage("User is already a member of this group");
        assertThat(groupService.getMembers(created.id(), aliceId)).hasSize(2);
    }

    @Test
    void manageAc3_rejectsUnknownUser() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);

        assertThatThrownBy(() -> groupService.addMember(
                created.id(), "missing@test.com", aliceId))
                .isInstanceOf(GroupMemberNotFoundException.class)
                .hasMessage("No matching user was found");
    }

    @Test
    void manageAc4AndAc6_removesSettledMemberWithoutDeletingUser() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);
        groupService.addMember(created.id(), "bob@test.com", aliceId);

        groupService.removeMember(created.id(), bobId, aliceId);

        assertThat(userRepository.findById(bobId)).isPresent();
        assertThat(groupService.getMembers(created.id(), aliceId))
                .extracting(GroupMemberResponse::userId)
                .containsExactly(aliceId);
        assertThatThrownBy(() -> groupService.getGroup(created.id(), bobId))
                .isInstanceOf(GroupNotFoundException.class);
        assertThat(groupService.getGroupsForUser(bobId)).isEmpty();
    }

    @Test
    void manageAc4_creatorCanLeaveWithoutDeletingGroupOrChangingCreatorMetadata() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);
        groupService.addMember(created.id(), "bob@test.com", aliceId);

        groupService.removeMember(created.id(), aliceId, aliceId);

        assertThat(groupRepository.findById(created.id())).isPresent();
        assertThat(groupService.getGroup(created.id(), bobId).id()).isEqualTo(created.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM expense_group WHERE group_id = ?",
                Long.class,
                created.id())).isEqualTo(aliceId);
        assertThat(groupService.getGroupsForUser(aliceId)).isEmpty();
    }

    @Test
    void manageAc5_rejectsRemovalWithOutstandingBalance() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);
        groupService.addMember(created.id(), "bob@test.com", aliceId);
        jdbcTemplate.update(
                "UPDATE user_in_group SET net_balance = 10.00 "
                        + "WHERE group_id = ? AND user_id = ?",
                created.id(), bobId);

        assertThatThrownBy(() -> groupService.removeMember(created.id(), bobId, aliceId))
                .isInstanceOf(GroupMemberConflictException.class)
                .hasMessage("The member's balance must be settled before removal");
        assertThat(groupRepository.findByIdAndMembersUserId(created.id(), bobId)).isPresent();
    }

    @Test
    void manageAc7_rejectsRosterChangesByNonMember() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);

        assertThatThrownBy(() -> groupService.addMember(created.id(), "bob", bobId))
                .isInstanceOf(GroupAccessDeniedException.class);
        assertThatThrownBy(() -> groupService.removeMember(created.id(), aliceId, bobId))
                .isInstanceOf(GroupAccessDeniedException.class);
        assertThatThrownBy(() -> groupService.getMembers(created.id(), bobId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    @Test
    void manageAc8_rejectsRemovalOfLastMember() {
        GroupResponse created = groupService.createGroup(
                new CreateGroupRequest("Flat 3", null), aliceId);

        assertThatThrownBy(() -> groupService.removeMember(created.id(), aliceId, aliceId))
                .isInstanceOf(GroupMemberConflictException.class)
                .hasMessage("A group must have at least one member");
    }
}
