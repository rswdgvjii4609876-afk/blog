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
    void v4PreservesLegacyPollDeadlineAndExistingVotes() throws Exception {
        try (var dataSource = new com.zaxxer.hikari.HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:timed-poll-upgrade;DB_CLOSE_DELAY=-1"); dataSource.setUsername("sa");
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).target("3").load().migrate();
            var db = new JdbcTemplate(dataSource);
            db.update("insert into reader_polls(question,options,active_slot,created_at,version) values('旧投票',?,1,CURRENT_TIMESTAMP,0)", "甲\n乙");
            db.update("insert into poll_votes(poll_id,reader_id,option_index) values(1,?,1)",java.util.UUID.randomUUID().toString());
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).load().migrate();
            assertThat(db.queryForObject("select ends_at from reader_polls where id=1",java.time.OffsetDateTime.class)).isNull();
            assertThat(db.queryForObject("select other_enabled from reader_polls where id=1",Boolean.class)).isFalse();
            assertThat(db.queryForObject("select option_index from poll_votes where poll_id=1",Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("select is_other from poll_votes where poll_id=1",Boolean.class)).isFalse();
            assertThat(db.queryForObject("select other_text from poll_votes where poll_id=1",String.class)).isNull();
        }
    }

    @Test
    void upgradeFromV1PreservesArticlesAndCreatesCascadingFeedbackTables() {
        try (var dataSource = new com.zaxxer.hikari.HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:reader-upgrade;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            // Match production's pooled Flyway connections; keep DDL sessions alive.
            try (var connection = dataSource.getConnection()) {
            var old = org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                .target("1").load();
            old.migrate();
            connection.createStatement().executeUpdate("insert into posts(title,summary,content,status,created_at,version) values('旧文章','摘要','原文','PUBLISHED',CURRENT_TIMESTAMP,0)");
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).load().migrate();
            var result = connection.createStatement().executeQuery("select title from posts");
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("旧文章");
            connection.createStatement().executeUpdate("insert into reader_comments(post_id,nickname,body,status,created_at,version) values(1,'访客','评论','PENDING',CURRENT_TIMESTAMP,0)");
            connection.createStatement().executeUpdate("insert into reader_ratings(post_id,reader_id,stars) values(1,'visitor',5)");
            connection.createStatement().executeUpdate("delete from posts where id=1");
            for (String table : new String[]{"reader_comments", "reader_ratings"}) {
                var count = connection.createStatement().executeQuery("select count(*) from " + table);
                count.next(); assertThat(count.getInt(1)).isZero();
            }
            }
        } catch (java.sql.SQLException e) { throw new AssertionError(e); }
    }

    @Test
    void migrationAndHibernateValidationAgreeOnSchemaAndSeedExactlyOnce() {
        assertThat(jdbc.queryForObject("select count(*) from posts", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from \"flyway_schema_history\" where \"success\" = true and \"version\" = '1'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from post_revisions", Integer.class)).isZero();
    }
}
