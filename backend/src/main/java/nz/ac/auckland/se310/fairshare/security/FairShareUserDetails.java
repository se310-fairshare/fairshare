package nz.ac.auckland.se310.fairshare.security;

import java.util.Collection;
import java.util.List;
import nz.ac.auckland.se310.fairshare.model.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapts the {@link User} entity to Spring Security's {@code UserDetails}.
 *
 * <p>Note that {@code getUsername()} returns the email, since that is the login
 * identifier. The entity's own {@code username} is a display name.
 */

@NullMarked
public class FairShareUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;

    public FairShareUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPassword();
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
