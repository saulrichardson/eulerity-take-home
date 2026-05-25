package com.eulerity.taskmanager.ai;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import org.springframework.stereotype.Component;

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

	public OpenAiTaskClient(OpenAiClientProvider clientProvider, OpenAiTaskRequestFactory requestFactory,
			AiSummaryProperties summaryProperties) {
		this.clientProvider = clientProvider;
		this.requestFactory = requestFactory;
		this.summaryProperties = summaryProperties;
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
			return payload.toTaskSuggestionResponse();
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

		@JsonPropertyDescription("Due date in yyyy-MM-dd format. Empty when no due date is stated or strongly implied.")
		public String dueDate;

		@JsonPropertyDescription("Task priority. Must be LOW, MEDIUM, or HIGH.")
		public TaskPriority priority;

		@JsonPropertyDescription("Task status. Must be TODO, IN_PROGRESS, or DONE.")
		public TaskStatus status;

		TaskSuggestionResponse toTaskSuggestionResponse() {
			LocalDate parsedDueDate = null;
			if (this.dueDate != null && !this.dueDate.isBlank()) {
				try {
					parsedDueDate = LocalDate.parse(this.dueDate);
				}
				catch (DateTimeParseException ex) {
					throw new AiTaskInvalidOutputException("AI response dueDate was not a valid ISO-8601 date", ex);
				}
			}
			return new TaskSuggestionResponse(this.title, this.description, parsedDueDate, this.priority, this.status);
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
