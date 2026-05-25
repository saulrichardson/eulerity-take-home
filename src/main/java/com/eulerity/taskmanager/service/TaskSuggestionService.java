package com.eulerity.taskmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.eulerity.taskmanager.ai.AiTaskException;
import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;
import com.eulerity.taskmanager.ai.AiTaskSuggestionPrompt;
import com.eulerity.taskmanager.ai.TaskAiClient;
import com.eulerity.taskmanager.ai.TaskAiTokenCounter;
import com.eulerity.taskmanager.config.OpenAiProperties;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;
import com.eulerity.taskmanager.model.TaskFieldLimits;

@Service
public class TaskSuggestionService {

	private final TaskAiClient taskAiClient;

	private final TaskAiTokenCounter tokenCounter;

	private final OpenAiProperties openAiProperties;

	private final Clock clock;

	public TaskSuggestionService(TaskAiClient taskAiClient, TaskAiTokenCounter tokenCounter,
			OpenAiProperties openAiProperties, Clock clock) {
		this.taskAiClient = taskAiClient;
		this.tokenCounter = tokenCounter;
		this.openAiProperties = openAiProperties;
		this.clock = clock;
	}

	public TaskSuggestionResponse suggestTask(String plainLanguageDescription) {
		LocalDate currentDate = LocalDate.now(this.clock);
		String failureReason = null;
		try {
			TaskSuggestionResponse firstAttempt = callClient(
					new AiTaskSuggestionPrompt(plainLanguageDescription, null, currentDate));
			List<String> firstAttemptFailures = validationFailures(firstAttempt);
			if (firstAttemptFailures.isEmpty()) {
				return firstAttempt;
			}
			failureReason = String.join("; ", firstAttemptFailures);
		}
		catch (AiTaskInvalidOutputException ex) {
			failureReason = ex.getMessage();
		}

		TaskSuggestionResponse secondAttempt = callClient(
				new AiTaskSuggestionPrompt(plainLanguageDescription, failureReason, currentDate));
		List<String> secondAttemptFailures = validationFailures(secondAttempt);
		if (secondAttemptFailures.isEmpty()) {
			return secondAttempt;
		}

		throw new AiTaskInvalidOutputException(
				"AI response did not include valid task data after retry: " + String.join("; ", secondAttemptFailures));
	}

	private List<String> validationFailures(TaskSuggestionResponse suggestion) {
		List<String> failures = new ArrayList<>();
		if (suggestion == null) {
			failures.add("response was missing");
			return failures;
		}
		if (suggestion.title() == null || suggestion.title().isBlank()) {
			failures.add("title was missing");
		}
		else if (suggestion.title().length() > TaskFieldLimits.TITLE_MAX_LENGTH) {
			failures.add("title exceeded " + TaskFieldLimits.TITLE_MAX_LENGTH + " characters");
		}
		if (suggestion.description() != null
				&& suggestion.description().length() > TaskFieldLimits.DESCRIPTION_MAX_LENGTH) {
			failures.add("description exceeded " + TaskFieldLimits.DESCRIPTION_MAX_LENGTH + " characters");
		}
		if (suggestion.priority() == null) {
			failures.add("priority was missing");
		}
		if (suggestion.status() == null) {
			failures.add("status was missing");
		}
		return failures;
	}

	private TaskSuggestionResponse callClient(AiTaskSuggestionPrompt prompt) {
		try {
			preflightTokenBudget(prompt);
			return this.taskAiClient.suggestTask(prompt);
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("AI task suggestion request failed", ex);
		}
	}

	private void preflightTokenBudget(AiTaskSuggestionPrompt prompt) {
		long inputTokens = this.tokenCounter.countTaskSuggestionInputTokens(prompt);
		long totalReservedTokens = inputTokens
				+ this.openAiProperties.getSuggestionMaxOutputTokens()
				+ this.openAiProperties.getRequestOverheadReserveTokens();
		if (totalReservedTokens > this.openAiProperties.hardContextCeilingTokens()) {
			throw new AiTaskException("AI task suggestion request exceeds configured model context budget");
		}
	}
}
