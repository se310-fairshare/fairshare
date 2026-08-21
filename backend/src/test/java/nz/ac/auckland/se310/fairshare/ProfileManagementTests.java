package nz.ac.auckland.se310.fairshare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import nz.ac.auckland.se310.fairshare.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProfileManagementTests {

  @Test
  void testGetUserByIdReturnsStoredUser() {
    UserRepository repository = mock(UserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    User user = new User(
        "testuser",
        encoder.encode("password123"),
        "test@example.com",
        User.Country.NEW_ZEALAND,
        User.Currency.NZD);

    when(repository.findById(7L)).thenReturn(Optional.of(user));

    UserService userService = new UserService(repository, encoder);
    User result = userService.getUserById(7L);

    assertEquals("testuser", result.getUsername());
    assertEquals("test@example.com", result.getEmail());
  }

  @Test
  void testUpdateUserChangesProfileInformation() {
    UserRepository repository = mock(UserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    User existingUser = new User(
        "oldname",
        encoder.encode("password123"),
        "old@example.com",
        User.Country.NEW_ZEALAND,
        User.Currency.NZD);

    User updatedInfo = new User();
    updatedInfo.setUsername("newname");
    updatedInfo.setEmail("new@example.com");
    updatedInfo.setCountry(User.Country.AUSTRALIA);
    updatedInfo.setCurrency(User.Currency.AUD);
    updatedInfo.setPassword("newpassword");

    when(repository.findById(3L)).thenReturn(Optional.of(existingUser));
    when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(repository.save(existingUser)).thenReturn(existingUser);

    UserService userService = new UserService(repository, encoder);
    User result = userService.updateUser(3L, updatedInfo);

    assertEquals("newname", result.getUsername());
    assertEquals("new@example.com", result.getEmail());
    assertEquals(User.Country.AUSTRALIA, result.getCountry());
    assertEquals(User.Currency.AUD, result.getCurrency());
    assertTrue(encoder.matches("newpassword", result.getPassword()));
  }
}
