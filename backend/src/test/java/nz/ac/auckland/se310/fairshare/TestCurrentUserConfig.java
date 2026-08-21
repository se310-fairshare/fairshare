package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestCurrentUserConfig {

    public static class SwitchableCurrentUserProvider implements CurrentUserProvider {
        private Long userId;
        public void setUserId(Long userId) { this.userId = userId; }
        @Override public Long currentUserId() { return userId; }
    }

    @Bean
    @Primary
    public SwitchableCurrentUserProvider currentUserProvider() {
        return new SwitchableCurrentUserProvider();
    }
}
