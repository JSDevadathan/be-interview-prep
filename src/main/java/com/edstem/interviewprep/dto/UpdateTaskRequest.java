package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.common.validation.EnumValue;
import com.edstem.interviewprep.common.validation.IsoDate;
import com.edstem.interviewprep.entity.Task;
import com.edstem.interviewprep.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = Task.TITLE_MAX_LENGTH, message = "title must be at most " + Task.TITLE_MAX_LENGTH + " characters")
        String title,

        @Size(max = Task.DESCRIPTION_MAX_LENGTH, message = "description must be at most " + Task.DESCRIPTION_MAX_LENGTH + " characters")
        String description,

        @NotNull(message = "status is required")
        @EnumValue(enumClass = TaskStatus.class, message = "status must be one of {allowedValues}")
        String status,

        @IsoDate(message = "dueDate must be a date in yyyy-MM-dd format")
        String dueDate) {
}
