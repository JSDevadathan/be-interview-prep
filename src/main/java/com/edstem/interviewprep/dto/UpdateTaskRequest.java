package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.Task;
import com.edstem.interviewprep.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = Task.TITLE_MAX_LENGTH, message = "title must be at most " + Task.TITLE_MAX_LENGTH + " characters")
        String title,

        @Size(max = Task.DESCRIPTION_MAX_LENGTH, message = "description must be at most " + Task.DESCRIPTION_MAX_LENGTH + " characters")
        String description,

        @NotNull(message = "status is required")
        TaskStatus status,

        LocalDate dueDate) {
}
