package com.eulerity.taskmanager.ai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.inputtokens.InputTokenCountParams;
import org.springframework.stereotype.Component;

import com.eulerity.taskmanager.config.OpenAiProperties;
import com.eulerity.taskmanager.model.TaskFieldLimits;

@Component
class OpenAiTaskRequestFactory {

	private final OpenAiProperties properties;

	OpenAiTaskRequestFactory(OpenAiProperties properties) {
		this.properties = properties;
	}

	StructuredResponseCreateParams<OpenAiTaskClient.TaskSuggestionPayload> suggestionCreateParams(
			AiTaskSuggestionPrompt prompt) {
		return ResponseCreateParams.builder()
			.instructions(suggestionInstructions(prompt.previousValidationFailure(), prompt.currentDate()))
			.input(prompt.plainLanguageDescription())
			.text(OpenAiTaskClient.TaskSuggestionPayload.class)
			.model(this.properties.getModel())
			.maxOutputTokens(this.properties.getSuggestionMaxOutputTokens())
			.build();
	}

	StructuredResponseCreateParams<OpenAiTaskClient.TaskSummaryPayload> summaryCreateParams(AiTaskSummaryPrompt prompt) {
		return ResponseCreateParams.builder()
			.instructions(summaryInstructions())
			.input(summaryInput(prompt))
			.text(OpenAiTaskClient.TaskSummaryPayload.class)
			.model(this.properties.getModel())
			.maxOutputTokens(this.properties.getSummaryMaxOutputTokens())
			.build();
	}

	InputTokenCountParams suggestionCountParams(AiTaskSuggestionPrompt prompt) {
		return countParams(suggestionCreateParams(prompt).rawParams());
	}

	InputTokenCountParams summaryCountParams(AiTaskSummaryPrompt prompt) {
		return countParams(summaryCreateParams(prompt).rawParams());
	}

	String suggestionInstructions(String previousValidationFailure, LocalDate currentDate) {
		String retryInstruction = previousValidationFailure != null && !previousValidationFailure.isBlank()
				? "Previous output was invalid: %s. Correct that issue and return one valid structured suggestion only."
					.formatted(previousValidationFailure)
				: "";
		DayOfWeek currentWeekday = currentDate.getDayOfWeek();
		String currentWeekdayName = displayWeekday(currentWeekday);
		LocalDate nextCurrentWeekday = nextCalendarWeekday(currentWeekday, currentDate);
		LocalDate nextFriday = nextCalendarWeekday(DayOfWeek.FRIDAY, currentDate);
		LocalDate beforeFriday = currentDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).minusDays(1);
		LocalDate beforeNextFriday = nextFriday.minusDays(1);
		return """
				You convert plain-language task intent, notes, or pasted context into one structured task suggestion.
				Distill the user's input into one task.
				Preserve important details.
				Do not copy long source text verbatim unless it is directly useful.
				Return one structured task suggestion with title, description, dueDateRule, priority, and status.
				title must be no longer than %d characters.
				description must be no longer than %d characters.
				dueDateRule must be exactly one supported date-rule expression with no spaces:
				- none()
				- date(YYYY-MM-DD)
				- month_day(MM-DD)
				- plus_days(N)
				- plus_days(EXPR,N)
				- plus_weeks(N)
				- plus_weeks(EXPR,N)
				- minus_days(EXPR,N)
				- minus_weeks(EXPR,N)
				- next_or_same(WEEKDAY)
				- next(WEEKDAY)
				- nth_next(WEEKDAY,N)
				- end_of_week()
				- start_of_next_week()
				- end_of_month()
				- start_of_next_month()
				- end_of_next_month()
				Allowed weekdays are:
				MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.
				Use today's date as context for relative dates: %s.
				Use none() when no due date is stated or strongly implied.
				Use none() when the due-date phrase is unsupported, ambiguous, business-day-specific, holiday-specific, recurrence-based, or cannot be represented by exactly one supported expression.
				Use date(YYYY-MM-DD) when the user states a specific full date. Past explicit dates are allowed.
				For "today", use date(%s). Do not return plus_days(0) or today().
				Use month_day(MM-DD) when the user gives a month/day without a year; the backend chooses the next upcoming matching date.
				Use plus_days(N) for phrases like tomorrow or in N days from today.
				Use plus_weeks(N) for phrases like in N weeks from today.
				Use plus_days(EXPR,N), plus_weeks(EXPR,N), minus_days(EXPR,N), or minus_weeks(EXPR,N) for simple composition. Max nesting depth is 3.
				Use next_or_same(WEEKDAY) for bare WEEKDAY, this WEEKDAY, on WEEKDAY, or by WEEKDAY.
				Use minus_days(next_or_same(WEEKDAY),1) for before WEEKDAY or before this WEEKDAY. Before excludes the named day.
				Use next(WEEKDAY) only when the user explicitly says next WEEKDAY. In this app, next WEEKDAY means that weekday in the next Monday-Sunday calendar week.
				Do not use next_or_same(WEEKDAY) for explicit next WEEKDAY phrases.
				Use minus_days(next(WEEKDAY),1) for before next WEEKDAY.
				Because today is %s %s, "next %s" must use next(%s), resolving to %s; "this %s", "on %s", and "by %s" use next_or_same(%s).
				For example, with today's date, "next Friday" must use next(FRIDAY), resolving to %s; "before Friday" must use minus_days(next_or_same(FRIDAY),1), resolving to %s; "before next Friday" must use minus_days(next(FRIDAY),1), resolving to %s.
				Use nth_next(WEEKDAY,2) for the WEEKDAY after next or two WEEKDAYs from now.
				Use next(WEEKDAY) for not this WEEKDAY but the one after.
				Use minus_days(nth_next(WEEKDAY,2),1) for before the WEEKDAY after next.
				Use end_of_week() for end of week or EOW. End of week means Sunday.
				Use start_of_next_week() for early next week or start of next week.
				Use end_of_month() for end of month.
				Use start_of_next_month() for start of next month.
				Use end_of_next_month() for end of next month.
				Use minus_days(end_of_month(),N) for N days before the end of the month.
				For task due dates, "by Friday" means Friday; "before Friday" means the day before Friday.
				Examples:
				- tomorrow -> plus_days(1)
				- June 5 -> month_day(06-05)
				- before Friday -> minus_days(next_or_same(FRIDAY),1)
				- next Friday -> next(FRIDAY)
				- before next Friday -> minus_days(next(FRIDAY),1)
				- Monday after next -> nth_next(MONDAY,2)
				- not this Friday but the one after -> next(FRIDAY)
				- before the Friday after next -> minus_days(nth_next(FRIDAY,2),1)
				- two days before end of month -> minus_days(end_of_month(),2)
				Do not return natural-language date text in dueDateRule.
				Do not invent a due date when none is stated or strongly implied.
				priority must be one of LOW, MEDIUM, or HIGH.
				status must be one of TODO, IN_PROGRESS, or DONE.
				%s
					""".formatted(TaskFieldLimits.TITLE_MAX_LENGTH, TaskFieldLimits.DESCRIPTION_MAX_LENGTH,
				currentDate, currentDate, currentWeekdayName, currentDate, currentWeekdayName, currentWeekday,
				nextCurrentWeekday, currentWeekdayName, currentWeekdayName, currentWeekdayName, currentWeekday,
				nextFriday, beforeFriday, beforeNextFriday, retryInstruction);
	}

	String summaryInstructions() {
		return """
				You are a concise task-management assistant.
				Write in a practical, calm, direct, specific style.
				Do not be overly cheerful, verbose, or alarmist.
				Summarize only what is supported by the provided task data.
				Do not invent tasks, deadlines, blockers, priorities, or project context.
				Use the provided dueTiming field for overdue and due-soon reasoning; do not calculate dates independently.
				Focus on what needs attention next using visible task data: dueTiming, status, and priority.
				Produce a short action plan the user can act on immediately, usually 3 to 5 steps.
				Reference task titles when useful.
				Avoid generic productivity advice unless it is tied to the task list.
				Do not mention implementation details, OpenAI, prompts, JSON schema, or backend logic.
				Return this exact response shape:
				{
				  "summary": "A concise natural-language workload summary.",
				  "plan": [
				    "A concrete next step based on the task list.",
				    "Another concrete next step based on the task list."
				  ]
				}
				""";
	}

	String summaryInput(AiTaskSummaryPrompt prompt) {
		StringBuilder input = new StringBuilder("Stored task data to summarize:\n");
		input.append("Summarize only these selected task records. Do not infer omitted tasks.\n");
		for (AiTaskSummaryContext task : prompt.tasks()) {
			input.append(task.toPromptRecord());
		}
		return input.toString();
	}

	private InputTokenCountParams countParams(ResponseCreateParams createParams) {
		InputTokenCountParams.Builder builder = InputTokenCountParams.builder().model(this.properties.getModel());
		createParams.instructions().ifPresent(builder::instructions);
		createParams.input().ifPresent(input -> {
			if (input.isText()) {
				builder.input(input.asText());
			}
			else {
				builder.inputOfResponseInputItems(input.asResponse());
			}
		});
		createParams.text()
			.flatMap(ResponseTextConfig::format)
			.ifPresent(format -> builder.text(InputTokenCountParams.Text.builder().format(format).build()));
		return builder.build();
	}

	private String displayWeekday(DayOfWeek weekday) {
		String lower = weekday.name().toLowerCase(Locale.ROOT);
		return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
	}

	private LocalDate nextCalendarWeekday(DayOfWeek weekday, LocalDate currentDate) {
		return currentDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
			.plusDays(weekday.getValue() - DayOfWeek.MONDAY.getValue());
	}
}
