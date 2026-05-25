package com.eulerity.taskmanager.dto;

import java.time.LocalDate;

import com.eulerity.taskmanager.model.TaskFieldLimits;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskCreateRequest(
		@NotBlank(message = "title is required")
		@Size(max = TaskFieldLimits.TITLE_MAX_LENGTH, message = "title must be 255 characters or fewer")
		String title,
		@Size(max = TaskFieldLimits.DESCRIPTION_MAX_LENGTH, message = "description must be 8000 characters or fewer")
		String description,
		@NotNull(message = "dueDate is required") LocalDate dueDate,
		@NotNull(message = "priority is required") TaskPriority priority,
		@NotNull(message = "status is required") TaskStatus status) {
}
