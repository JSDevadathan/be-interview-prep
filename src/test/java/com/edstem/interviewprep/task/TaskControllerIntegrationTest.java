package com.edstem.interviewprep.task;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TaskControllerIntegrationTest.FixedClockConfig.class)
class TaskControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String TODAY = "2026-01-15";
    private static final String YESTERDAY = "2026-01-14";
    private static final String NEXT_WEEK = "2026-01-22";
    private static final long UNKNOWN_ID = 999_999L;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createReturnsCreatedTaskWithDefaultStatusAndCreatedDate() throws Exception {
        postTask("""
                {"title": "Write report", "description": "Quarterly numbers", "dueDate": "%s"}
                """.formatted(NEXT_WEEK))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.description").value("Quarterly numbers"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value(NEXT_WEEK))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void createAcceptsDueDateOfToday() throws Exception {
        postTask("""
                {"title": "Due today", "dueDate": "%s"}
                """.formatted(TODAY))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectsInvalidFieldsWithOneMessagePerField() throws Exception {
        postTask("""
                {"title": " ", "dueDate": "%s"}
                """.formatted(YESTERDAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/tasks"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must not be in the past"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("title is required"));
    }

    @Test
    void createRejectsTitleLongerThanLimit() throws Exception {
        String tooLongTitle = "a".repeat(Task.TITLE_MAX_LENGTH + 1);

        postTask("""
                {"title": "%s"}
                """.formatted(tooLongTitle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("title must be at most 100 characters"));
    }

    @Test
    void createRejectsMalformedJsonInTheSameErrorFormat() throws Exception {
        postTask("{\"title\": \"Unclosed\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void getReturnsTaskById() throws Exception {
        long id = createTask("Read me");

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Read me"));
    }

    @Test
    void getReturnsNotFoundForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", UNKNOWN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task with id 999999 was not found"))
                .andExpect(jsonPath("$.instance").value("/api/tasks/999999"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        createTask("Still to do");
        postTask("""
                {"title": "Already done", "status": "DONE"}
                """)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Already done"));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listRejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateReplacesTaskFields() throws Exception {
        long id = createTask("Draft");

        putTask(id, """
                {"title": "Final", "description": "Reviewed", "status": "IN_PROGRESS", "dueDate": "%s"}
                """.formatted(NEXT_WEEK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final"))
                .andExpect(jsonPath("$.description").value("Reviewed"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").value(NEXT_WEEK))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void updateRequiresStatus() throws Exception {
        long id = createTask("Draft");

        putTask(id, """
                {"title": "Final"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("status is required"));
    }

    @Test
    void updateReturnsNotFoundForUnknownTask() throws Exception {
        putTask(UNKNOWN_ID, """
                {"title": "Final", "status": "DONE"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTask() throws Exception {
        long id = createTask("Disposable");

        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNotFoundForUnknownTask() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", UNKNOWN_ID))
                .andExpect(status().isNotFound());
    }

    private long createTask(String title) throws Exception {
        String body = postTask("""
                {"title": "%s"}
                """.formatted(title))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions postTask(String json) throws Exception {
        return mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putTask(long id, String json) throws Exception {
        return mockMvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
