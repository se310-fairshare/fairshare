package nz.ac.auckland.se310.fairshare.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import nz.ac.auckland.se310.fairshare.UserService;
import nz.ac.auckland.se310.fairshare.dto.LoginRequest;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.security.FairShareUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final CurrentUserProvider currentUserProvider;

  public UserController(
          UserService userService,
          AuthenticationManager authenticationManager,
          SecurityContextRepository securityContextRepository,
          CurrentUserProvider currentUserProvider
  ) {
    this.userService = userService;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.currentUserProvider = currentUserProvider;
  }

  @PostMapping("/register")
  public ResponseEntity<String> register(@RequestBody User user) {
        try {
            userService.register(user);
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already in use");
        }
    }

  @PostMapping("/login")
  public ResponseEntity<?> login(
          @RequestBody LoginRequest request,
          HttpServletRequest httpRequest,
          HttpServletResponse httpResponse) {

    try {
      Authentication authentication = authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.email(), request.password()));

      // Rotate the session id on authentication to prevent session fixation.
      if (httpRequest.getSession(false) != null) {
        httpRequest.changeSessionId();
      }

      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, httpRequest, httpResponse);

      if (!(authentication.getPrincipal() instanceof FairShareUserDetails principal)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
      }
      User user = userService.getUserById(principal.getId());

      return ResponseEntity.ok(Map.of(
              "message", "Login successful",
              "user", serializeUser(user)));

    } catch (AuthenticationException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
  }

  @GetMapping("/me")
  public ResponseEntity<?> getCurrentUser() {
    User user = userService.getUserById(currentUserProvider.currentUserId());
    return ResponseEntity.ok(Map.of("user", serializeUser(user)));
  }

  @PutMapping("/me")
  public ResponseEntity<?> updateCurrentUser(@RequestBody User updatedUser) {
    try {
      User user = userService.updateUser(currentUserProvider.currentUserId(), updatedUser);
      return ResponseEntity.ok(Map.of(
              "message", "Profile updated successfully",
              "user", serializeUser(user)));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  private Map<String, Object> serializeUser(User user) {
    return Map.of(
        "id", user.getId(),
        "username", user.getUsername(),
        "email", user.getEmail(),
        "country", user.getCountry() == null ? null : user.getCountry().name(),
        "currency", user.getCurrency() == null ? null : user.getCurrency().name()
    );
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }
}
