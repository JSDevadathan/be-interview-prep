package com.edstem.interviewprep.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.entity.Task;
import com.edstem.interviewprep.enums.TaskStatus;
import com.edstem.interviewprep.repository.TaskRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TaskControllerIntegrationTest.FixedClockConfig.class)
@WithMockUser
class TaskControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00.123456789Z");
    private static final String STORED_CREATED_AT = "2026-01-15T10:00:00.123456Z";
    private static final String TODAY = "2026-01-15";
    private static final String YESTERDAY = "2026-01-14";
    private static final String LAST_WEEK = "2026-01-08";
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

    @Autowired
    private TaskRepository taskRepository;

    @AfterEach
    void deleteAllTasks() {
        taskRepository.deleteAll();
    }

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
                .andExpect(jsonPath("$.createdAt").value(STORED_CREATED_AT));
    }

    @Test
    void createdAtReturnedOnCreateMatchesStoredValue() throws Exception {
        long id = createTask("Precise");

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(STORED_CREATED_AT));
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
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/tasks"));
    }

    @Test
    void createRejectsUnknownStatusValueWithFieldMessage() throws Exception {
        postTask("""
                {"title": "Typo", "status": "FINISHED"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("status must be one of [TODO, IN_PROGRESS, DONE]"));
    }

    @Test
    void createReportsEveryInvalidFieldInOneResponse() throws Exception {
        postTask("""
                {"title": " ", "status": "FINISHED", "dueDate": "not-a-date"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must be a date in yyyy-MM-dd format"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("status must be one of [TODO, IN_PROGRESS, DONE]"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[2].message").value("title is required"));
    }

    @Test
    void createRejectsNumericDueDateInsteadOfReadingItAsDays() throws Exception {
        postTask("""
                {"title": "Numeric date", "dueDate": 30000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must be a date in yyyy-MM-dd format"));
    }

    @Test
    void createRejectsImpossibleCalendarDate() throws Exception {
        postTask("""
                {"title": "No such day", "dueDate": "2026-02-30"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must be a date in yyyy-MM-dd format"));
    }

    @Test
    void createRejectsObjectAsTitleWithFieldMessage() throws Exception {
        postTask("""
                {"title": {"text": "nested"}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("title has an invalid value"));
    }

    @Test
    void createRejectsInvalidDateWithFieldMessage() throws Exception {
        postTask("""
                {"title": "Bad date", "dueDate": "2026-13-45"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must be a date in yyyy-MM-dd format"));
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
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/tasks"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("status must be one of [TODO, IN_PROGRESS, DONE]"));
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
                .andExpect(jsonPath("$.createdAt").value(STORED_CREATED_AT));

        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateKeepsExistingOverdueDueDate() throws Exception {
        Task overdue = taskRepository.save(
                new Task("Overdue", null, TaskStatus.TODO, LocalDate.parse(YESTERDAY), NOW));

        putTask(overdue.getId(), """
                {"title": "Overdue", "status": "DONE", "dueDate": "%s"}
                """.formatted(YESTERDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.dueDate").value(YESTERDAY));
    }

    @Test
    void updateRejectsMovingOverdueDueDateToAnotherPastDate() throws Exception {
        Task overdue = taskRepository.save(
                new Task("Overdue", null, TaskStatus.TODO, LocalDate.parse(YESTERDAY), NOW));

        putTask(overdue.getId(), """
                {"title": "Overdue", "status": "TODO", "dueDate": "%s"}
                """.formatted(LAST_WEEK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must not be in the past"));
    }

    @Test
    void updateAllowsClearingOverdueDueDate() throws Exception {
        Task overdue = taskRepository.save(
                new Task("Overdue", null, TaskStatus.TODO, LocalDate.parse(YESTERDAY), NOW));

        putTask(overdue.getId(), """
                {"title": "Overdue", "status": "TODO"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void updateRejectsMovingDueDateIntoThePast() throws Exception {
        long id = createTask("Draft");

        putTask(id, """
                {"title": "Draft", "status": "TODO", "dueDate": "%s"}
                """.formatted(LAST_WEEK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/tasks/" + id))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate must not be in the past"));
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

    static Stream<Arguments> requestsRejectedByTheFramework() {
        return Stream.of(
                Arguments.of(get("/api/tasks/abc"), 400, "Bad Request", "/api/tasks/abc"),
                Arguments.of(patch("/api/tasks/1"), 405, "Method Not Allowed", "/api/tasks/1"),
                Arguments.of(
                        post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("title"),
                        415,
                        "Unsupported Media Type",
                        "/api/tasks"),
                Arguments.of(get("/api/unknown"), 404, "Not Found", "/api/unknown"));
    }

    @ParameterizedTest
    @MethodSource("requestsRejectedByTheFramework")
    void frameworkErrorsUseTheSameProblemFormat(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedTitle,
            String expectedInstance) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.instance").value(expectedInstance));
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
