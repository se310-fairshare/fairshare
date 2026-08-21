package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

  public enum Country {
    NEW_ZEALAND,
    AUSTRALIA
    // Add more countries as needed
  }

  public enum Currency {
    NZD,
    AUD
    // Add more currencies as needed
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String username;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false, unique = true)
  private String email;

  @Enumerated(EnumType.STRING)
  private Country country;

  @Enumerated(EnumType.STRING)
  private Currency currency;

  public User() {}

  public User(String username, String password, String email, Country country, Currency currency) {
    this.username = username;
    this.password = password;
    this.email = email;
    this.country = country;
    this.currency = currency;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getEmail() {
    return email;
  }

  public Country getCountry() {
    return country;
  }

  public Currency getCurrency() {
    return currency;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setCountry(Country country) {
    this.country = country;
  }

  public void setCurrency(Currency currency) {
    this.currency = currency;
  }

  public Long getId() {return id; }
}
