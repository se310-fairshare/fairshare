package nz.ac.auckland.se310.fairshare.service;

import jakarta.validation.constraints.Null;
import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.security.FairShareUserDetails;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class FairShareUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public FairShareUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(FairShareUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
    }
}
