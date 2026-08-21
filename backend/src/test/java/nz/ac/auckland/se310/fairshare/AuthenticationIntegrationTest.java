package nz.ac.auckland.se310.fairshare;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

@Testcontainers
@SpringBootTest
class AuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String ALICE_EMAIL = "alice.auth@test.com";
    private static final String BOB_EMAIL = "bob.auth@test.com";
    private static final String PASSWORD = "password123";

    @Autowired
    WebApplicationContext context;
    @Autowired
    UserRepository userRepository;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(context, builder ->
                builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());

        if (userRepository.findByEmailIgnoreCase(ALICE_EMAIL).isEmpty()) {
            register("alice", ALICE_EMAIL);
            register("bob", BOB_EMAIL);
        }
    }

    private void register(String username, String email) {
        mvc.post().uri("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s","email":"%s",
                         "country":"NEW_ZEALAND","currency":"NZD"}
                        """.formatted(username, PASSWORD, email))
                .exchange();
    }

    /**
     * Logs in and returns the session the server established.
     */
    private MockHttpSession login(String email) {
        var result = mvc.post().uri("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .exchange();

        assertThat(result).hasStatusOk();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void unauthenticatedRequestsReturnUnauthorized() {
        assertThat(mvc.get().uri("/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/groups")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eachUserIsAuthenticatedAsTheirOwnAccount() {
        MockHttpSession aliceSession = login(ALICE_EMAIL);
        MockHttpSession bobSession = login(BOB_EMAIL);

        assertThat(mvc.get().uri("/users/me").session(aliceSession))
                .hasStatusOk()
                .bodyJson().extractingPath("$.user.email").isEqualTo(ALICE_EMAIL);

        assertThat(mvc.get().uri("/users/me").session(bobSession))
                .hasStatusOk()
                .bodyJson().extractingPath("$.user.email").isEqualTo(BOB_EMAIL);
    }

    @Test
    void logoutInvalidatesTheSession() {
        MockHttpSession session = login(ALICE_EMAIL);

        assertThat(mvc.get().uri("/users/me").session(session)).hasStatusOk();

        assertThat(mvc.post().uri("/users/logout").session(session))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/users/me").session(session))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedUserWithoutPermissionGetsForbiddenNotUnauthorized() throws Exception {
        MockHttpSession aliceSession = login(ALICE_EMAIL);

        // Alice creates a group; Bob is not a member of it.
        var created = mvc.post().uri("/groups")
                .session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice's flat\"}")
                .exchange();

        assertThat(created).hasStatus(HttpStatus.CREATED);

        Number groupId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        MockHttpSession bobSession = login(BOB_EMAIL);

        assertThat(mvc.get().uri("/groups/" + groupId + "/members").session(bobSession))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}