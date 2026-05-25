package com.eulerity.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;
import com.eulerity.taskmanager.ai.AiTaskSuggestionPrompt;
import com.eulerity.taskmanager.ai.MissingOpenAiConfigurationException;
import com.eulerity.taskmanager.ai.TaskAiClient;
import com.eulerity.taskmanager.ai.TaskAiTokenCounter;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

@SpringBootTest
@AutoConfigureMockMvc
class TaskSuggestionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskAiClient taskAiClient;

	@MockitoBean
	private TaskAiTokenCounter tokenCounter;

	@BeforeEach
	void setUpTokenCounter() {
		lenient().when(this.tokenCounter.countTaskSuggestionInputTokens(any(AiTaskSuggestionPrompt.class)))
			.thenReturn(100L);
	}

	@Test
	void suggestTaskReturnsMockedAiSuggestion() throws Exception {
		when(this.taskAiClient.suggestTask(any(AiTaskSuggestionPrompt.class)))
			.thenReturn(new TaskSuggestionResponse("Submit quarterly report", "Submit the quarterly report",
					LocalDate.of(2026, 5, 29), TaskPriority.MEDIUM, TaskStatus.TODO));

		this.mockMvc.perform(post("/tasks/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "description": "remind me to submit the quarterly report before Friday"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Submit quarterly report"))
			.andExpect(jsonPath("$.description").value("Submit the quarterly report"))
			.andExpect(jsonPath("$.dueDate").value("2026-05-29"))
			.andExpect(jsonPath("$.priority").value("MEDIUM"))
			.andExpect(jsonPath("$.status").value("TODO"));
	}

	@Test
	void suggestTaskReturnsStructuredErrorWhenOpenAiConfigurationIsMissing() throws Exception {
		when(this.tokenCounter.countTaskSuggestionInputTokens(any(AiTaskSuggestionPrompt.class)))
			.thenThrow(new MissingOpenAiConfigurationException());

		this.mockMvc.perform(post("/tasks/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "description": "remind me to submit the quarterly report before Friday"
						}
						"""))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.error").value("AI_CONFIGURATION_MISSING"))
			.andExpect(jsonPath("$.message").value("AI configuration is missing. Set OPENAI_API_KEY to enable AI task features."));
	}

	@Test
	void suggestTaskAcceptsMaximumLengthDescription() throws Exception {
		String description = "a".repeat(12000);
		when(this.taskAiClient.suggestTask(any(AiTaskSuggestionPrompt.class)))
			.thenReturn(new TaskSuggestionResponse("Review pasted context", "Distilled detail", null,
					TaskPriority.MEDIUM, TaskStatus.TODO));

		this.mockMvc.perform(post("/tasks/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "description": "%s"
						}
						""".formatted(description)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Review pasted context"));
	}

	@Test
	void suggestTaskReturnsStructuredValidationErrorForOverlongDescription() throws Exception {
		this.mockMvc.perform(post("/tasks/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "description": "%s"
						}
						""".formatted("a".repeat(12001))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("description"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("description must be 12000 characters or fewer"));
	}

	@Test
	void suggestTaskReturnsStructuredAiFailureForUnexpectedClientFailure() throws Exception {
		when(this.taskAiClient.suggestTask(any(AiTaskSuggestionPrompt.class)))
			.thenThrow(new IllegalStateException("unexpected client failure"));

		this.mockMvc.perform(post("/tasks/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "description": "remind me to submit the quarterly report before Friday"
						}
						"""))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.status").value(502))
			.andExpect(jsonPath("$.error").value("AI_TASK_FAILED"))
			.andExpect(jsonPath("$.message").value("AI task suggestion request failed"));
	}

	@Test
	void suggestTaskReturnsStructuredInvalidOutputErrorForMalformedModelOutput() throws Exception {
		when(this.taskAiClient.suggestTask(any(AiTaskSuggestionPrompt.class)))
			.thenThrow(new AiTaskInvalidOutputException("AI response dueDate was not a valid ISO-8601 date"));

		this.mockMvc.perform(post("/tasks/suggest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "description": "remind me to submit the quarterly report before Friday"
						}
						"""))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.status").value(502))
			.andExpect(jsonPath("$.error").value("AI_TASK_OUTPUT_INVALID"))
			.andExpect(jsonPath("$.message").value("AI response dueDate was not a valid ISO-8601 date"));
	}
}
