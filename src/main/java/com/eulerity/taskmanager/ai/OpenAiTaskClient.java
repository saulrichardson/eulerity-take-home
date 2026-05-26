package com.eulerity.taskmanager.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import org.springframework.stereotype.Component;

import com.eulerity.taskmanager.ai.dates.AiDueDateRule;
import com.eulerity.taskmanager.ai.dates.AiDueDateRuleParser;
import com.eulerity.taskmanager.ai.dates.AiDueDateRuleResolver;
import com.eulerity.taskmanager.config.AiSummaryProperties;
import com.eulerity.taskmanager.dto.TaskSummaryResponse;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

@Component
public class OpenAiTaskClient implements TaskAiClient {

	private final OpenAiClientProvider clientProvider;

	private final OpenAiTaskRequestFactory requestFactory;

	private final AiSummaryProperties summaryProperties;

	private final AiDueDateRuleParser dueDateRuleParser;

	private final AiDueDateRuleResolver dueDateRuleResolver;

	public OpenAiTaskClient(OpenAiClientProvider clientProvider, OpenAiTaskRequestFactory requestFactory,
			AiSummaryProperties summaryProperties, AiDueDateRuleParser dueDateRuleParser,
			AiDueDateRuleResolver dueDateRuleResolver) {
		this.clientProvider = clientProvider;
		this.requestFactory = requestFactory;
		this.summaryProperties = summaryProperties;
		this.dueDateRuleParser = dueDateRuleParser;
		this.dueDateRuleResolver = dueDateRuleResolver;
	}

	@Override
	public TaskSuggestionResponse suggestTask(AiTaskSuggestionPrompt prompt) {
		try {
			StructuredResponseCreateParams<TaskSuggestionPayload> params =
					this.requestFactory.suggestionCreateParams(prompt);
			StructuredResponse<TaskSuggestionPayload> response = this.clientProvider.getClient()
				.responses()
				.create(params);
			TaskSuggestionPayload payload = response.output()
				.stream()
				.flatMap(item -> item.message().stream())
				.flatMap(message -> message.content().stream())
				.flatMap(content -> content.outputText().stream())
				.findFirst()
				.orElseThrow(() -> new AiTaskInvalidOutputException("AI response did not include structured task data"));
			return payload.toTaskSuggestionResponse(this.dueDateRuleParser, this.dueDateRuleResolver,
					prompt.currentDate());
		}
		catch (AiTaskInvalidOutputException ex) {
			throw ex;
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (OpenAIException ex) {
			throw new AiTaskException("OpenAI task suggestion request failed", ex);
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("OpenAI task suggestion request failed", ex);
		}
	}

	@Override
	public TaskSummaryResponse summarizeTasks(AiTaskSummaryPrompt prompt) {
		try {
			StructuredResponseCreateParams<TaskSummaryPayload> params = this.requestFactory.summaryCreateParams(prompt);
			StructuredResponse<TaskSummaryPayload> response = this.clientProvider.getClient()
				.responses()
				.create(params);
			TaskSummaryPayload payload = response.output()
				.stream()
				.flatMap(item -> item.message().stream())
				.flatMap(message -> message.content().stream())
				.flatMap(content -> content.outputText().stream())
				.findFirst()
				.orElseThrow(() -> new AiTaskInvalidOutputException("AI response did not include a task summary"));
			return payload.toTaskSummaryResponse(this.summaryProperties.getMaxSummaryLength(),
					this.summaryProperties.getMaxPlanItems(), this.summaryProperties.getMaxPlanItemLength());
		}
		catch (AiTaskInvalidOutputException ex) {
			throw ex;
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (OpenAIException ex) {
			throw new AiTaskException("OpenAI task summary request failed", ex);
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("OpenAI task summary request failed", ex);
		}
	}

	public static class TaskSuggestionPayload {

		@JsonPropertyDescription("Short actionable task title, no longer than 255 characters.")
		public String title;

		@JsonPropertyDescription("Optional distilled task detail, no longer than 8000 characters.")
		public String description;

		@JsonPropertyDescription("""
				A due-date rule expression. Use exactly one supported expression:
				none(), date(YYYY-MM-DD), month_day(MM-DD), plus_days(N),
				plus_days(EXPR,N), plus_weeks(N), plus_weeks(EXPR,N),
				minus_days(EXPR,N), minus_weeks(EXPR,N), next_or_same(WEEKDAY),
				next(WEEKDAY), nth_next(WEEKDAY,N), end_of_week(),
				start_of_next_week(), end_of_month(), start_of_next_month(),
				or end_of_next_month().
				Allowed weekdays are MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, and SUNDAY.
				next(WEEKDAY) means that weekday in the next Monday-Sunday calendar week.
				Use minus_days(...,1) when before excludes the named date.
				Do not return natural language. Return none() when no supported due date can be represented.
				""")
		public String dueDateRule;

		@JsonPropertyDescription("Task priority. Must be LOW, MEDIUM, or HIGH.")
		public TaskPriority priority;

		@JsonPropertyDescription("Task status. Must be TODO, IN_PROGRESS, or DONE.")
		public TaskStatus status;

		TaskSuggestionResponse toTaskSuggestionResponse(AiDueDateRuleParser parser, AiDueDateRuleResolver resolver,
				java.time.LocalDate currentDate) {
			AiDueDateRule parsedRule = parser.parse(this.dueDateRule);
			java.time.LocalDate resolvedDueDate = resolver.resolve(parsedRule, currentDate);
			return new TaskSuggestionResponse(this.title, this.description, resolvedDueDate, this.priority,
					this.status);
		}
	}

	public static class TaskSummaryPayload {

		@JsonPropertyDescription("Concise natural-language workload summary grounded only in provided task data.")
		public String summary;

		@JsonPropertyDescription("Short concrete action plan, usually 3 to 5 steps, grounded only in provided task data.")
		public List<String> plan;

		TaskSummaryResponse toTaskSummaryResponse(int maxSummaryLength, int maxPlanItems, int maxPlanItemLength) {
			if (this.summary == null || this.summary.isBlank()) {
				throw new AiTaskInvalidOutputException("AI response did not include a summary");
			}
			if (this.summary.length() > maxSummaryLength) {
				throw new AiTaskInvalidOutputException("AI response summary exceeded " + maxSummaryLength + " characters");
			}
			if (this.plan == null || this.plan.isEmpty()) {
				throw new AiTaskInvalidOutputException("AI response did not include an action plan");
			}
			if (this.plan.size() > maxPlanItems) {
				throw new AiTaskInvalidOutputException("AI response action plan exceeded " + maxPlanItems + " items");
			}
			for (String item : this.plan) {
				if (item == null || item.isBlank()) {
					throw new AiTaskInvalidOutputException("AI response action plan included a blank item");
				}
				if (item.length() > maxPlanItemLength) {
					throw new AiTaskInvalidOutputException(
							"AI response action plan item exceeded " + maxPlanItemLength + " characters");
				}
			}
			return new TaskSummaryResponse(this.summary, this.plan);
		}
	}
}
