package nz.ac.auckland.se310.fairshare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.security.FairShareUserDetails;
import nz.ac.auckland.se310.fairshare.security.SecurityContextCurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider =
            new SecurityContextCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheIdOfTheAuthenticatedUser() {
        User user = new User("alice", "hash", "alice@test.com",
                User.Country.NEW_ZEALAND, User.Currency.NZD);
        ReflectionTestUtils.setField(user, "id", 7L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new FairShareUserDetails(user), null, List.of()));

        assertThat(provider.currentUserId()).isEqualTo(7L);
    }

    @Test
    void throwsWhenNoUserIsAuthenticated() {
        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(IllegalStateException.class);
    }
}