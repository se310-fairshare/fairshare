package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder encoder;

  public UserService(UserRepository userRepository, PasswordEncoder encoder) {
    this.userRepository = userRepository;
    this.encoder = encoder;
  }

  public synchronized void register(User user) {

    if (userRepository.findByEmail(user.getEmail()).isPresent()) {
      throw new IllegalArgumentException("Email already in use");
    }

    String hashedPassword = encoder.encode(user.getPassword());
    user.setPassword(hashedPassword);
    user.setEmail(user.getEmail().trim().toLowerCase());

    userRepository.save(user);
  }

  public synchronized User getUserById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public synchronized User updateUser(Long id, User updatedUser) {
    User existingUser = getUserById(id);

    if (updatedUser.getUsername() != null && !updatedUser.getUsername().isBlank()) {
      existingUser.setUsername(updatedUser.getUsername().trim());
    }

    if (updatedUser.getEmail() != null && !updatedUser.getEmail().isBlank()) {
      String normalizedEmail = updatedUser.getEmail().trim().toLowerCase();
      if (!normalizedEmail.equals(existingUser.getEmail())
          && userRepository.findByEmail(normalizedEmail).isPresent()) {
        throw new IllegalArgumentException("Email already in use");
      }
      existingUser.setEmail(normalizedEmail);
    }

    if (updatedUser.getCountry() != null) {
      existingUser.setCountry(updatedUser.getCountry());
    }

    if (updatedUser.getCurrency() != null) {
      existingUser.setCurrency(updatedUser.getCurrency());
    }

    if (updatedUser.getPassword() != null && !updatedUser.getPassword().isBlank()) {
      existingUser.setPassword(encoder.encode(updatedUser.getPassword()));
    }

    return userRepository.save(existingUser);
  }
}
