package nz.ac.auckland.se310.fairshare;

import static org.junit.jupiter.api.Assertions.*;

import nz.ac.auckland.se310.fairshare.model.User;
import org.junit.jupiter.api.Test;

class UserTests {

  @Test
  void testConstructor() {
    User user =
        new User(
            "testuser",
            "password123",
            "test@example.com",
            User.Country.NEW_ZEALAND,
            User.Currency.NZD);
    assertAll(
        () -> assertEquals("testuser", user.getUsername()),
        () -> assertEquals("password123", user.getPassword()),
        () -> assertEquals("test@example.com", user.getEmail()),
        () -> assertEquals(User.Country.NEW_ZEALAND, user.getCountry()),
        () -> assertEquals(User.Currency.NZD, user.getCurrency()));
  }

  @Test
  void testDefaultConstructor() {
    User user = new User();
    assertAll(
        () -> assertNull(user.getUsername()),
        () -> assertNull(user.getPassword()),
        () -> assertNull(user.getEmail()),
        () -> assertNull(user.getCountry()),
        () -> assertNull(user.getCurrency()));
  }

  @Test
  void testSetUsername() {
    User user = new User();

    user.setUsername("testuser");

    assertEquals("testuser", user.getUsername());
  }

  @Test
  void testSetPassword() {
    User user = new User();

    user.setPassword("password123");

    assertEquals("password123", user.getPassword());
  }

  @Test
  void testSetEmail() {
    User user = new User();

    user.setEmail("test@example.com");

    assertEquals("test@example.com", user.getEmail());
  }

  @Test
  void testSetCountry() {
    User user = new User();

    user.setCountry(User.Country.NEW_ZEALAND);

    assertEquals(User.Country.NEW_ZEALAND, user.getCountry());
  }

  @Test
  void testSetCurrency() {
    User user = new User();

    user.setCurrency(User.Currency.NZD);

    assertEquals(User.Currency.NZD, user.getCurrency());
  }
}
