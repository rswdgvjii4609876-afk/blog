package com.example.blog.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:migration-check;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class DatabaseMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationAndHibernateValidationAgreeOnSchemaAndSeedExactlyOnce() {
        assertThat(jdbc.queryForObject("select count(*) from posts", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from \"flyway_schema_history\" where \"success\" = true and \"version\" = '1'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from post_revisions", Integer.class)).isZero();
    }
}
