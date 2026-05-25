package com.eulerity.taskmanager.dto;

import com.eulerity.taskmanager.model.TaskFieldLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskSuggestionRequest(
		@NotBlank(message = "description is required")
		@Size(max = TaskFieldLimits.AI_REQUEST_DESCRIPTION_MAX_LENGTH,
				message = "description must be 12000 characters or fewer")
		String description) {
}
