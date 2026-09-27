package com.chronosq.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronosq.security.TestJwtKeyConfiguration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@Import(TestJwtKeyConfiguration.class)
class JobRequeueIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM job_requeues").update();
        jdbcClient.sql("DELETE FROM job_executions").update();
        jdbcClient.sql("DELETE FROM jobs").update();
        jdbcClient.sql("DELETE FROM worker_nodes").update();
    }

    @Test
    void requeueCreatesFreshJobAndAuditsOperatorWithoutChangingSource() throws Exception {
        UUID sourceId = insertJob("DEAD_LETTERED", 3);
        var result = mockMvc.perform(post("/api/v1/jobs/{jobId}/requeue", sourceId)
                        .with(jwt().jwt(token -> token.subject("operator-1"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_jobs.requeue")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Upstream recovered\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceJobId").value(sourceId.toString()))
                .andExpect(jsonPath("$.job.status").value("READY"))
                .andExpect(jsonPath("$.job.attemptCount").value(0))
                .andExpect(jsonPath("$.job.maxAttempts").value(3))
                .andExpect(jsonPath("$.audit.actor").value("operator-1"))
                .andExpect(jsonPath("$.audit.reason").value("Upstream recovered"))
                .andReturn();

        UUID newId = UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("job").get("id").asText());
        assertThat(newId).isNotEqualTo(sourceId);
        assertThat(jdbcClient.sql("SELECT status FROM jobs WHERE id = :id")
                .param("id", sourceId).query(String.class).single()).isEqualTo("DEAD_LETTERED");
        assertThat(jdbcClient.sql("SELECT count(*) FROM job_requeues WHERE source_job_id = :id AND new_job_id = :newId")
                .param("id", sourceId).param("newId", newId).query(Integer.class).single()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/jobs/{jobId}/requeues", sourceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_jobs.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newJobId").value(newId.toString()));

        mockMvc.perform(post("/api/v1/jobs/{jobId}/requeue", sourceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_jobs.requeue")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Second copy\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_ALREADY_REQUEUED"));
    }

    @Test
    void rejectsNonDeadLetteredJobAndMissingOperatorScope() throws Exception {
        UUID readyId = insertJob("READY", 0);
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requeue", readyId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_jobs.requeue")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not failed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_REQUEUEABLE"));

        UUID deadId = insertJob("DEAD_LETTERED", 3);
        mockMvc.perform(post("/api/v1/jobs/{jobId}/requeue", deadId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_jobs.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No permission\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/jobs/{jobId}/requeue", deadId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_jobs.requeue")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requeuedCronOccurrenceIsPersistedAsOneOffJob() throws Exception {
        UUID sourceId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcClient.sql("""
                INSERT INTO jobs (id, queue_name, job_type, payload, status, priority,
                    available_at, schedule_type, cron_expression, cron_timezone,
                    missed_execution_policy, attempt_count, max_attempts,
                    timeout_seconds, created_at, updated_at, completed_at, version)
                VALUES (:id, 'requeue-test', 'PRINT_MESSAGE', '{"message":"retry me"}'::jsonb,
                    'DEAD_LETTERED', 0, :now, 'CRON', '0 0 9 * * *', 'UTC',
                    'SKIP', 3, 3, 30, :now, :now, :now, 0)
                """)
                .param("id", sourceId)
                .param("now", now)
                .update();

        var result = mockMvc.perform(post("/api/v1/jobs/{jobId}/requeue", sourceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_jobs.requeue")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Retry failed occurrence\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job.scheduleType").value("IMMEDIATE"))
                .andReturn();

        UUID retryId = UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("job").get("id").asText());
        assertThat(jdbcClient.sql("""
                SELECT schedule_type = 'IMMEDIATE'
                   AND interval_seconds IS NULL
                   AND cron_expression IS NULL
                   AND cron_timezone IS NULL
                   AND missed_execution_policy IS NULL
                FROM jobs WHERE id = :id
                """)
                .param("id", retryId).query(Boolean.class).single()).isTrue();
        assertThat(jdbcClient.sql("SELECT schedule_type FROM jobs WHERE id = :id")
                .param("id", sourceId).query(String.class).single()).isEqualTo("CRON");
    }

    private UUID insertJob(String status, int attempts) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcClient.sql("""
                INSERT INTO jobs (id, queue_name, job_type, payload, status, priority,
                    available_at, schedule_type, attempt_count, max_attempts,
                    timeout_seconds, created_at, updated_at, completed_at, version)
                VALUES (:id, 'requeue-test', 'PRINT_MESSAGE', '{"message":"retry me"}'::jsonb,
                    :status, 0, :now, 'IMMEDIATE', :attempts, 3, 30,
                    :now, :now, :completedAt, 0)
                """)
                .param("id", id)
                .param("status", status)
                .param("now", now)
                .param("attempts", attempts)
                .param("completedAt", status.equals("DEAD_LETTERED") ? now : null)
                .update();
        return id;
    }
}
