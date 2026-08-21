package nz.ac.auckland.se310.fairshare;

import static org.assertj.core.api.Assertions.assertThat;

import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.security.FairShareUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FairShareUserDetailsTest {

    @Test
    void getUsernameReturnsTheEmailBecauseThatIsTheLoginIdentifier() {
        User user = new User("alice", "hashed-password", "alice@test.com",
                User.Country.NEW_ZEALAND, User.Currency.NZD);
        ReflectionTestUtils.setField(user, "id", 7L);

        FairShareUserDetails details = new FairShareUserDetails(user);

        assertThat(details.getUsername()).isEqualTo("alice@test.com");
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(details.getId()).isEqualTo(7L);
    }
}
