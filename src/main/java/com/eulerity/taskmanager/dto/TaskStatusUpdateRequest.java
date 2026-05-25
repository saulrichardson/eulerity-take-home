package com.eulerity.taskmanager.dto;

import com.eulerity.taskmanager.model.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(
		@NotNull(message = "status is required") TaskStatus status) {
}
