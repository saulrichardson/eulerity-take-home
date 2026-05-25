package com.eulerity.taskmanager.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.inputtokens.InputTokenCountParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eulerity.taskmanager.config.OpenAiProperties;
import com.eulerity.taskmanager.model.TaskFieldLimits;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

class OpenAiTaskRequestFactoryTest {

	private OpenAiProperties openAiProperties;

	private OpenAiTaskRequestFactory requestFactory;

	@BeforeEach
	void setUp() {
		this.openAiProperties = new OpenAiProperties();
		this.requestFactory = new OpenAiTaskRequestFactory(this.openAiProperties);
	}

	@Test
	void suggestionPromptIncludesFieldLimitsRetryReasonAndDeterministicDate() {
		String instructions = this.requestFactory.suggestionInstructions("title exceeded 255 characters",
				LocalDate.of(2026, 5, 25));

		assertThat(instructions).contains("title must be no longer than 255 characters");
		assertThat(instructions).contains("description must be no longer than 8000 characters");
		assertThat(instructions).contains("Set dueDate only when the user states or strongly implies a due date.");
		assertThat(instructions).contains("When set, dueDate must be an ISO-8601 date string in yyyy-MM-dd format.");
		assertThat(instructions).contains("leave dueDate empty instead of inventing one");
		assertThat(instructions).contains("Use today's date as context for relative dates: 2026-05-25.");
		assertThat(instructions).contains("Previous output was invalid: title exceeded 255 characters.");
		assertThat(instructions).contains("return one valid structured suggestion only");
		assertThat(instructions).doesNotContain("Return complete valid data for title, description, dueDate");
	}

	@Test
	void suggestionSchemaDescriptionsIncludeFieldLimits() throws Exception {
		JsonPropertyDescription titleDescription =
				OpenAiTaskClient.TaskSuggestionPayload.class.getField("title")
					.getAnnotation(JsonPropertyDescription.class);
		JsonPropertyDescription descriptionDescription =
				OpenAiTaskClient.TaskSuggestionPayload.class.getField("description")
					.getAnnotation(JsonPropertyDescription.class);

		assertThat(titleDescription.value()).contains(String.valueOf(TaskFieldLimits.TITLE_MAX_LENGTH));
		assertThat(descriptionDescription.value()).contains(String.valueOf(TaskFieldLimits.DESCRIPTION_MAX_LENGTH));
	}

	@Test
	void openAiCreateRequestsSetOutputTokenCaps() {
		this.openAiProperties.setSuggestionMaxOutputTokens(123);
		this.openAiProperties.setSummaryMaxOutputTokens(456);

		assertThat(this.requestFactory.suggestionCreateParams(suggestionPrompt("raw source text", null))
			.rawParams()
			.maxOutputTokens())
			.contains(123L);
		assertThat(this.requestFactory.summaryCreateParams(summaryPrompt()).rawParams().maxOutputTokens())
			.contains(456L);
	}

	@Test
	void suggestionCountRequestMatchesFinalCreateRequestShape() {
		AiTaskSuggestionPrompt prompt = suggestionPrompt("raw source text", null);
		ResponseCreateParams createParams = this.requestFactory.suggestionCreateParams(prompt)
			.rawParams();
		InputTokenCountParams countParams = this.requestFactory.suggestionCountParams(prompt);

		assertThat(countParams.instructions()).isEqualTo(createParams.instructions());
		assertThat(countParams.input()).isPresent();
		assertThat(countParams.input().orElseThrow().asString()).isEqualTo(createParams.input().orElseThrow().asText());
		assertThat(countParams.model()).contains(this.openAiProperties.getModel());
		assertThat(countParams.text()).isPresent();
		assertThat(countParams.text().orElseThrow().format()).isEqualTo(createParams.text()
			.orElseThrow()
			.format());
	}

	@Test
	void summaryCountRequestMatchesFinalCreateRequestShape() {
		AiTaskSummaryPrompt prompt = summaryPrompt();
		ResponseCreateParams createParams = this.requestFactory.summaryCreateParams(prompt).rawParams();
		InputTokenCountParams countParams = this.requestFactory.summaryCountParams(prompt);

		assertThat(countParams.instructions()).isEqualTo(createParams.instructions());
		assertThat(countParams.input()).isPresent();
		assertThat(countParams.input().orElseThrow().asString()).isEqualTo(createParams.input().orElseThrow().asText());
		assertThat(countParams.model()).contains(this.openAiProperties.getModel());
		assertThat(countParams.text()).isPresent();
		assertThat(countParams.text().orElseThrow().format()).isEqualTo(createParams.text()
			.orElseThrow()
			.format());
	}

	@Test
	void suggestionPayloadRejectsMalformedDueDate() {
		OpenAiTaskClient.TaskSuggestionPayload payload = new OpenAiTaskClient.TaskSuggestionPayload();
		payload.title = "Submit quarterly report";
		payload.description = "Submit the report";
		payload.dueDate = "next Friday";
		payload.priority = TaskPriority.MEDIUM;
		payload.status = TaskStatus.TODO;

		assertThatThrownBy(payload::toTaskSuggestionResponse)
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDate was not a valid ISO-8601 date");
	}

	@Test
	void suggestionPayloadAllowsMissingDueDate() {
		OpenAiTaskClient.TaskSuggestionPayload payload = new OpenAiTaskClient.TaskSuggestionPayload();
		payload.title = "Review launch notes";
		payload.description = "Review and extract follow-up items.";
		payload.dueDate = null;
		payload.priority = TaskPriority.MEDIUM;
		payload.status = TaskStatus.TODO;

		assertThat(payload.toTaskSuggestionResponse().dueDate()).isNull();
	}

	@Test
	void summaryPayloadRejectsExcessiveSummaryOrPlanOutput() {
		OpenAiTaskClient.TaskSummaryPayload longSummary = new OpenAiTaskClient.TaskSummaryPayload();
		longSummary.summary = "a".repeat(11);
		longSummary.plan = List.of("Do the task.");

		assertThatThrownBy(() -> longSummary.toTaskSummaryResponse(10, 5, 50))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response summary exceeded 10 characters");

		OpenAiTaskClient.TaskSummaryPayload tooManyItems = new OpenAiTaskClient.TaskSummaryPayload();
		tooManyItems.summary = "Summary";
		tooManyItems.plan = List.of("One", "Two", "Three");

		assertThatThrownBy(() -> tooManyItems.toTaskSummaryResponse(100, 2, 50))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response action plan exceeded 2 items");

		OpenAiTaskClient.TaskSummaryPayload longItem = new OpenAiTaskClient.TaskSummaryPayload();
		longItem.summary = "Summary";
		longItem.plan = List.of("a".repeat(51));

		assertThatThrownBy(() -> longItem.toTaskSummaryResponse(100, 2, 50))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response action plan item exceeded 50 characters");
	}

	private static AiTaskSummaryPrompt summaryPrompt() {
		AiTaskSummaryContext context = new AiTaskSummaryContext(1L, "Submit report", "Finish report",
				LocalDate.of(2026, 5, 29), TaskPriority.HIGH, TaskStatus.TODO, "due in 4 days");
		return new AiTaskSummaryPrompt(List.of(context));
	}

	private static AiTaskSuggestionPrompt suggestionPrompt(String description, String previousValidationFailure) {
		return new AiTaskSuggestionPrompt(description, previousValidationFailure, LocalDate.of(2026, 5, 25));
	}
}
