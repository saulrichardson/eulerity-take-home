package com.eulerity.taskmanager.ai;

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

	String suggestionInstructions(String previousValidationFailure, java.time.LocalDate currentDate) {
		String retryInstruction = previousValidationFailure != null && !previousValidationFailure.isBlank()
				? "Previous output was invalid: %s. Correct that issue and return one valid structured suggestion only."
					.formatted(previousValidationFailure)
				: "";
		return """
				You convert plain-language task intent, notes, or pasted context into one structured task suggestion.
				Distill the user's input into one task.
				Preserve important details.
				Do not copy long source text verbatim unless it is directly useful.
				Return one structured task suggestion with title, description, dueDate, priority, and status.
				title must be no longer than %d characters.
				description must be no longer than %d characters.
				Set dueDate only when the user states or strongly implies a due date.
				When set, dueDate must be an ISO-8601 date string in yyyy-MM-dd format.
				If no due date is stated or strongly implied, leave dueDate empty instead of inventing one.
				priority must be one of LOW, MEDIUM, or HIGH.
				status must be one of TODO, IN_PROGRESS, or DONE.
				Use today's date as context for relative dates: %s.
				%s
					""".formatted(TaskFieldLimits.TITLE_MAX_LENGTH, TaskFieldLimits.DESCRIPTION_MAX_LENGTH,
				currentDate, retryInstruction);
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
}
