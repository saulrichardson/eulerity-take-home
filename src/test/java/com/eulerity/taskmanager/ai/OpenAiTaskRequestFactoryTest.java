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

import com.eulerity.taskmanager.ai.dates.AiDueDateRuleParser;
import com.eulerity.taskmanager.ai.dates.AiDueDateRuleResolver;
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
		assertThat(instructions).contains("Return one structured task suggestion with title, description, dueDateRule, priority, and status.");
		assertThat(instructions).contains("dueDateRule must be exactly one supported date-rule expression with no spaces");
		assertThat(instructions).contains("none()");
		assertThat(instructions).contains("date(YYYY-MM-DD)");
		assertThat(instructions).contains("month_day(MM-DD)");
		assertThat(instructions).contains("plus_days(N)");
		assertThat(instructions).contains("plus_days(EXPR,N)");
		assertThat(instructions).contains("plus_weeks(N)");
		assertThat(instructions).contains("plus_weeks(EXPR,N)");
		assertThat(instructions).contains("minus_days(EXPR,N)");
		assertThat(instructions).contains("minus_weeks(EXPR,N)");
		assertThat(instructions).contains("next_or_same(WEEKDAY)");
		assertThat(instructions).contains("next(WEEKDAY)");
		assertThat(instructions).contains("nth_next(WEEKDAY,N)");
		assertThat(instructions).contains("end_of_week()");
		assertThat(instructions).contains("start_of_next_week()");
		assertThat(instructions).contains("end_of_month()");
		assertThat(instructions).contains("start_of_next_month()");
		assertThat(instructions).contains("end_of_next_month()");
		assertThat(instructions).contains("Use today's date as context for relative dates: 2026-05-25.");
		assertThat(instructions).contains("Use none() when no due date is stated or strongly implied.");
		assertThat(instructions).contains("Use none() when the due-date phrase is unsupported, ambiguous, business-day-specific, holiday-specific, recurrence-based, or cannot be represented by exactly one supported expression.");
		assertThat(instructions).contains("For \"today\", use date(2026-05-25). Do not return plus_days(0) or today().");
		assertThat(instructions).contains("Use month_day(MM-DD) when the user gives a month/day without a year; the backend chooses the next upcoming matching date.");
		assertThat(instructions).contains("Use plus_days(EXPR,N), plus_weeks(EXPR,N), minus_days(EXPR,N), or minus_weeks(EXPR,N) for simple composition. Max nesting depth is 3.");
		assertThat(instructions).contains("Use next_or_same(WEEKDAY) for bare WEEKDAY, this WEEKDAY, on WEEKDAY, or by WEEKDAY.");
		assertThat(instructions).contains("Use minus_days(next_or_same(WEEKDAY),1) for before WEEKDAY or before this WEEKDAY. Before excludes the named day.");
		assertThat(instructions).contains("Use next(WEEKDAY) only when the user explicitly says next WEEKDAY. In this app, next WEEKDAY means that weekday in the next Monday-Sunday calendar week.");
		assertThat(instructions).contains("Do not use next_or_same(WEEKDAY) for explicit next WEEKDAY phrases.");
		assertThat(instructions).contains("Use minus_days(next(WEEKDAY),1) for before next WEEKDAY.");
		assertThat(instructions).contains("Because today is Monday 2026-05-25, \"next Monday\" must use next(MONDAY), resolving to 2026-06-01; \"this Monday\", \"on Monday\", and \"by Monday\" use next_or_same(MONDAY).");
		assertThat(instructions).contains("For example, with today's date, \"next Friday\" must use next(FRIDAY), resolving to 2026-06-05; \"before Friday\" must use minus_days(next_or_same(FRIDAY),1), resolving to 2026-05-28; \"before next Friday\" must use minus_days(next(FRIDAY),1), resolving to 2026-06-04.");
		assertThat(instructions).contains("Use nth_next(WEEKDAY,2) for the WEEKDAY after next or two WEEKDAYs from now.");
		assertThat(instructions).contains("Use next(WEEKDAY) for not this WEEKDAY but the one after.");
		assertThat(instructions).contains("Use minus_days(nth_next(WEEKDAY,2),1) for before the WEEKDAY after next.");
		assertThat(instructions).contains("Use end_of_week() for end of week or EOW. End of week means Sunday.");
		assertThat(instructions).contains("Use minus_days(end_of_month(),N) for N days before the end of the month.");
		assertThat(instructions).contains("before Friday -> minus_days(next_or_same(FRIDAY),1)");
		assertThat(instructions).contains("next Friday -> next(FRIDAY)");
		assertThat(instructions).contains("before next Friday -> minus_days(next(FRIDAY),1)");
		assertThat(instructions).contains("not this Friday but the one after -> next(FRIDAY)");
		assertThat(instructions).contains("before the Friday after next -> minus_days(nth_next(FRIDAY,2),1)");
		assertThat(instructions).contains("For task due dates, \"by Friday\" means Friday; \"before Friday\" means the day before Friday.");
		assertThat(instructions).contains("Do not return natural-language date text in dueDateRule.");
		assertThat(instructions).contains("Do not invent a due date when none is stated or strongly implied.");
		assertThat(instructions).contains("Previous output was invalid: title exceeded 255 characters.");
		assertThat(instructions).contains("return one valid structured suggestion only");
		assertThat(instructions).doesNotContain("Return complete valid data for title, description, dueDate");
		assertThat(instructions).doesNotContain("Return one structured task suggestion with title, description, dueDate, priority");
		assertThat(instructions).doesNotContain("Set dueDate only");
	}

	@Test
	void suggestionSchemaDescriptionsIncludeFieldLimits() throws Exception {
		JsonPropertyDescription titleDescription =
				OpenAiTaskClient.TaskSuggestionPayload.class.getField("title")
					.getAnnotation(JsonPropertyDescription.class);
		JsonPropertyDescription descriptionDescription =
				OpenAiTaskClient.TaskSuggestionPayload.class.getField("description")
					.getAnnotation(JsonPropertyDescription.class);
		JsonPropertyDescription dueDateRuleDescription =
				OpenAiTaskClient.TaskSuggestionPayload.class.getField("dueDateRule")
					.getAnnotation(JsonPropertyDescription.class);

		assertThat(titleDescription.value()).contains(String.valueOf(TaskFieldLimits.TITLE_MAX_LENGTH));
		assertThat(descriptionDescription.value()).contains(String.valueOf(TaskFieldLimits.DESCRIPTION_MAX_LENGTH));
		assertThat(dueDateRuleDescription.value()).contains("none()")
			.contains("date(YYYY-MM-DD)")
			.contains("month_day(MM-DD)")
			.contains("plus_days(N)")
			.contains("plus_days(EXPR,N)")
			.contains("minus_days(EXPR,N)")
			.contains("next_or_same(WEEKDAY)")
			.contains("nth_next(WEEKDAY,N)")
			.contains("next(WEEKDAY) means that weekday in the next Monday-Sunday calendar week")
			.contains("Use minus_days(...,1) when before excludes the named date")
			.contains("start_of_next_month()")
			.contains("Do not return natural language")
			.contains("Return none() when no supported due date can be represented");
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
	void suggestionPayloadResolvesValidDueDateRule() {
		OpenAiTaskClient.TaskSuggestionPayload payload = new OpenAiTaskClient.TaskSuggestionPayload();
		payload.title = "Submit quarterly report";
		payload.description = "Submit the report";
		payload.dueDateRule = "next(FRIDAY)";
		payload.priority = TaskPriority.MEDIUM;
		payload.status = TaskStatus.TODO;

		assertThat(payload.toTaskSuggestionResponse(new AiDueDateRuleParser(), new AiDueDateRuleResolver(),
				LocalDate.of(2026, 5, 25)).dueDate())
			.isEqualTo(LocalDate.of(2026, 6, 5));
	}

	@Test
	void suggestionPayloadAllowsNoDateRule() {
		OpenAiTaskClient.TaskSuggestionPayload payload = new OpenAiTaskClient.TaskSuggestionPayload();
		payload.title = "Review launch notes";
		payload.description = "Review and extract follow-up items.";
		payload.dueDateRule = "none()";
		payload.priority = TaskPriority.MEDIUM;
		payload.status = TaskStatus.TODO;

		assertThat(payload.toTaskSuggestionResponse(new AiDueDateRuleParser(), new AiDueDateRuleResolver(),
				LocalDate.of(2026, 5, 25)).dueDate())
			.isNull();
	}

	@Test
	void suggestionPayloadRejectsMissingOrInvalidDueDateRule() {
		OpenAiTaskClient.TaskSuggestionPayload missing = new OpenAiTaskClient.TaskSuggestionPayload();
		missing.title = "Submit quarterly report";
		missing.description = "Submit the report";
		missing.dueDateRule = null;
		missing.priority = TaskPriority.MEDIUM;
		missing.status = TaskStatus.TODO;

		assertThatThrownBy(() -> missing.toTaskSuggestionResponse(new AiDueDateRuleParser(),
				new AiDueDateRuleResolver(), LocalDate.of(2026, 5, 25)))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule was missing");

		OpenAiTaskClient.TaskSuggestionPayload invalid = new OpenAiTaskClient.TaskSuggestionPayload();
		invalid.title = "Submit quarterly report";
		invalid.description = "Submit the report";
		invalid.dueDateRule = "next Friday";
		invalid.priority = TaskPriority.MEDIUM;
		invalid.status = TaskStatus.TODO;

		assertThatThrownBy(() -> invalid.toTaskSuggestionResponse(new AiDueDateRuleParser(),
				new AiDueDateRuleResolver(), LocalDate.of(2026, 5, 25)))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule was not a supported date-rule expression");
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
