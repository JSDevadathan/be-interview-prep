package com.edstem.interviewprep.task.dto;

import com.edstem.interviewprep.task.Task;
import com.edstem.interviewprep.task.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = Task.TITLE_MAX_LENGTH, message = "title must be at most " + Task.TITLE_MAX_LENGTH + " characters")
        String title,

        @Size(max = Task.DESCRIPTION_MAX_LENGTH, message = "description must be at most " + Task.DESCRIPTION_MAX_LENGTH + " characters")
        String description,

        TaskStatus status,

        @FutureOrPresent(message = "dueDate must not be in the past")
        LocalDate dueDate) {
}
