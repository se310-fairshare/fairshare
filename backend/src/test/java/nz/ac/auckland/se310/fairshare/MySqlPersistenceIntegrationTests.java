package nz.ac.auckland.se310.fairshare;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MySqlPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(
            DockerImageName.parse("mysql:8.4"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesMigrationsAndPersistsData() {
        jdbcTemplate.update(
                "INSERT INTO persistence_probe (message) VALUES (?)",
                "connected");

        String storedMessage = jdbcTemplate.queryForObject(
                "SELECT message FROM persistence_probe WHERE id = 1",
                String.class);

        assertThat(storedMessage).isEqualTo("connected");

        var appliedVersions = jdbcTemplate.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank
                """,
                String.class);

        assertThat(appliedVersions).contains("1", "9999");
    }
}
