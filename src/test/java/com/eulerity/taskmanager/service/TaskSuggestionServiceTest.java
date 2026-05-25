package com.eulerity.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eulerity.taskmanager.ai.AiTaskException;
import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;
import com.eulerity.taskmanager.ai.AiTaskSuggestionPrompt;
import com.eulerity.taskmanager.ai.TaskAiClient;
import com.eulerity.taskmanager.ai.TaskAiTokenCounter;
import com.eulerity.taskmanager.config.OpenAiProperties;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

@ExtendWith(MockitoExtension.class)
class TaskSuggestionServiceTest {

	private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 5, 25);

	@Mock
	private TaskAiClient taskAiClient;

	@Mock
	private TaskAiTokenCounter tokenCounter;

	private OpenAiProperties openAiProperties;

	private TaskSuggestionService taskSuggestionService;

	@BeforeEach
	void setUp() {
		this.openAiProperties = new OpenAiProperties();
		this.taskSuggestionService = new TaskSuggestionService(this.taskAiClient, this.tokenCounter,
				this.openAiProperties, fixedClock());
		lenient().when(this.tokenCounter.countTaskSuggestionInputTokens(any(AiTaskSuggestionPrompt.class)))
			.thenReturn(100L);
	}

	@Test
	void suggestTaskReturnsCompleteSuggestion() {
		TaskSuggestionResponse suggestion = suggestion("Submit quarterly report", LocalDate.of(2026, 5, 29));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", null)))
			.thenReturn(suggestion);

		TaskSuggestionResponse response = this.taskSuggestionService
			.suggestTask("submit the quarterly report before Friday");

		assertThat(response).isEqualTo(suggestion);
		ArgumentCaptor<AiTaskSuggestionPrompt> tokenPromptCaptor = ArgumentCaptor.forClass(AiTaskSuggestionPrompt.class);
		ArgumentCaptor<AiTaskSuggestionPrompt> clientPromptCaptor =
				ArgumentCaptor.forClass(AiTaskSuggestionPrompt.class);
		verify(this.tokenCounter).countTaskSuggestionInputTokens(tokenPromptCaptor.capture());
		verify(this.taskAiClient).suggestTask(clientPromptCaptor.capture());
		assertThat(clientPromptCaptor.getValue()).isEqualTo(tokenPromptCaptor.getValue());
		assertThat(clientPromptCaptor.getValue()).isEqualTo(prompt("submit the quarterly report before Friday", null));
	}

	@Test
	void suggestTaskRetriesOnceWhenSuggestionIsIncomplete() {
		TaskSuggestionResponse incomplete = new TaskSuggestionResponse(null, "Missing title", LocalDate.of(2026, 5, 29),
				TaskPriority.MEDIUM, TaskStatus.TODO);
		TaskSuggestionResponse complete = suggestion("Submit quarterly report", LocalDate.of(2026, 5, 29));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", null)))
			.thenReturn(incomplete);
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", "title was missing")))
			.thenReturn(complete);

		TaskSuggestionResponse response = this.taskSuggestionService
			.suggestTask("submit the quarterly report before Friday");

		assertThat(response).isEqualTo(complete);
		verify(this.tokenCounter)
			.countTaskSuggestionInputTokens(prompt("submit the quarterly report before Friday", null));
		verify(this.tokenCounter)
			.countTaskSuggestionInputTokens(prompt("submit the quarterly report before Friday", "title was missing"));
	}

	@Test
	void suggestTaskRetriesOnceWhenClientMarksOutputInvalid() {
		TaskSuggestionResponse complete = suggestion("Submit quarterly report", LocalDate.of(2026, 5, 29));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", null)))
			.thenThrow(new AiTaskInvalidOutputException("invalid model output"));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", "invalid model output")))
			.thenReturn(complete);

		TaskSuggestionResponse response = this.taskSuggestionService
			.suggestTask("submit the quarterly report before Friday");

		assertThat(response).isEqualTo(complete);
	}

	@Test
	void suggestTaskFailsWhenRetryIsStillIncomplete() {
		TaskSuggestionResponse incomplete = new TaskSuggestionResponse("Submit quarterly report", null, null,
				null, TaskStatus.TODO);
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report", null))).thenReturn(incomplete);
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report", "priority was missing")))
			.thenReturn(incomplete);

		assertThatThrownBy(() -> this.taskSuggestionService.suggestTask("submit the quarterly report"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response did not include valid task data after retry: priority was missing");
	}

	@Test
	void suggestTaskRetriesOnceWhenTitleIsTooLong() {
		TaskSuggestionResponse overlong = suggestion("a".repeat(256), LocalDate.of(2026, 5, 29));
		TaskSuggestionResponse complete = suggestion("Submit quarterly report", LocalDate.of(2026, 5, 29));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", null)))
			.thenReturn(overlong);
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday",
				"title exceeded 255 characters")))
			.thenReturn(complete);

		TaskSuggestionResponse response = this.taskSuggestionService
			.suggestTask("submit the quarterly report before Friday");

		assertThat(response).isEqualTo(complete);
	}

	@Test
	void suggestTaskRetriesOnceWhenDescriptionIsTooLong() {
		TaskSuggestionResponse overlong = new TaskSuggestionResponse("Submit quarterly report", "a".repeat(8001),
				LocalDate.of(2026, 5, 29), TaskPriority.MEDIUM, TaskStatus.TODO);
		TaskSuggestionResponse complete = suggestion("Submit quarterly report", LocalDate.of(2026, 5, 29));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday", null)))
			.thenReturn(overlong);
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report before Friday",
				"description exceeded 8000 characters")))
			.thenReturn(complete);

		TaskSuggestionResponse response = this.taskSuggestionService
			.suggestTask("submit the quarterly report before Friday");

		assertThat(response).isEqualTo(complete);
	}

	@Test
	void suggestTaskAllowsMissingDueDateWhenNoDueDateIsImplied() {
		TaskSuggestionResponse suggestion = suggestion("Review launch notes", null);
		when(this.taskAiClient.suggestTask(prompt("review these launch notes", null))).thenReturn(suggestion);

		TaskSuggestionResponse response = this.taskSuggestionService.suggestTask("review these launch notes");

		assertThat(response).isEqualTo(suggestion);
		verify(this.tokenCounter).countTaskSuggestionInputTokens(prompt("review these launch notes", null));
		verify(this.taskAiClient).suggestTask(prompt("review these launch notes", null));
		verifyNoMoreInteractions(this.taskAiClient);
	}

	@Test
	void suggestTaskRejectsOversizedReturnedFieldsAfterRetry() {
		TaskSuggestionResponse overlong = suggestion("a".repeat(256), LocalDate.of(2026, 5, 29));
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report", null))).thenReturn(overlong);
		when(this.taskAiClient.suggestTask(prompt("submit the quarterly report", "title exceeded 255 characters")))
			.thenReturn(overlong);

		assertThatThrownBy(() -> this.taskSuggestionService.suggestTask("submit the quarterly report"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response did not include valid task data after retry: title exceeded 255 characters");
	}

	@Test
	void suggestTaskFailsBeforeModelCallWhenPreflightExceedsContextBudget() {
		this.openAiProperties.setModelContextWindowTokens(100);
		this.openAiProperties.setContextUsageRatio(0.90);
		this.openAiProperties.setSuggestionMaxOutputTokens(80);
		this.openAiProperties.setRequestOverheadReserveTokens(20);
		when(this.tokenCounter.countTaskSuggestionInputTokens(prompt("submit the quarterly report", null)))
			.thenReturn(1L);

		assertThatThrownBy(() -> this.taskSuggestionService.suggestTask("submit the quarterly report"))
			.isInstanceOf(AiTaskException.class)
			.hasMessage("AI task suggestion request exceeds configured model context budget");
		verifyNoInteractions(this.taskAiClient);
	}

	@Test
	void suggestTaskWrapsUnexpectedTokenCounterFailure() {
		when(this.tokenCounter.countTaskSuggestionInputTokens(prompt("submit the quarterly report", null)))
			.thenThrow(new IllegalStateException("counter failed"));

		assertThatThrownBy(() -> this.taskSuggestionService.suggestTask("submit the quarterly report"))
			.isInstanceOf(AiTaskException.class)
			.hasMessage("AI task suggestion request failed");
		verifyNoInteractions(this.taskAiClient);
	}

	private static TaskSuggestionResponse suggestion(String title, LocalDate dueDate) {
		return new TaskSuggestionResponse(title, "Finish and submit the report", dueDate, TaskPriority.MEDIUM,
				TaskStatus.TODO);
	}

	private static AiTaskSuggestionPrompt prompt(String description, String previousValidationFailure) {
		return new AiTaskSuggestionPrompt(description, previousValidationFailure, CURRENT_DATE);
	}

	private static Clock fixedClock() {
		return Clock.fixed(CURRENT_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
	}
}
