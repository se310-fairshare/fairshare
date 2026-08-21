package nz.ac.auckland.se310.fairshare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.service.FairShareUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class FairShareUserDetailsServiceTest {

    @Test
    void loadsUserByEmail() {
        UserRepository repository = mock(UserRepository.class);
        User user = new User("alice", "hash", "alice@test.com",
                User.Country.NEW_ZEALAND, User.Currency.NZD);
        when(repository.findByEmailIgnoreCase("alice@test.com")).thenReturn(Optional.of(user));

        var details = new FairShareUserDetailsService(repository)
                .loadUserByUsername("alice@test.com");

        assertThat(details.getUsername()).isEqualTo("alice@test.com");
    }

    @Test
    void throwsForUnknownEmail() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmailIgnoreCase("nobody@test.com")).thenReturn(Optional.empty());

        FairShareUserDetailsService service = new FairShareUserDetailsService(repository);

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}