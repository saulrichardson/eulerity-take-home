package com.eulerity.taskmanager.ai;

import java.time.LocalDate;
import java.util.Objects;

public record AiTaskSuggestionPrompt(
		String plainLanguageDescription,
		String previousValidationFailure,
		LocalDate currentDate) {

	public AiTaskSuggestionPrompt {
		Objects.requireNonNull(plainLanguageDescription, "plainLanguageDescription must not be null");
		Objects.requireNonNull(currentDate, "currentDate must not be null");
	}
}
