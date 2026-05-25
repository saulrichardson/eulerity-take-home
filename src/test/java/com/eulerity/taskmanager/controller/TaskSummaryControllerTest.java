package com.eulerity.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.eulerity.taskmanager.ai.AiTaskException;
import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;
import com.eulerity.taskmanager.ai.MissingOpenAiConfigurationException;
import com.eulerity.taskmanager.ai.TaskAiClient;
import com.eulerity.taskmanager.ai.TaskAiTokenCounter;
import com.eulerity.taskmanager.dto.TaskSummaryResponse;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;
import com.eulerity.taskmanager.repository.TaskRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TaskSummaryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TaskRepository taskRepository;

	@MockitoBean
	private TaskAiClient taskAiClient;

	@MockitoBean
	private TaskAiTokenCounter tokenCounter;

	@BeforeEach
	void cleanDatabase() {
		this.taskRepository.deleteAll();
		lenient().when(this.tokenCounter.countTaskSummaryInputTokens(any())).thenReturn(100L);
	}

	@Test
	void summarizeTasksReturnsMockedAiSummary() throws Exception {
		saveTask("Submit quarterly report");
		when(this.taskAiClient.summarizeTasks(any()))
			.thenReturn(new TaskSummaryResponse("The quarterly report needs attention.",
					List.of("Finish the quarterly report first.", "Then review lower-priority work.")));

		this.mockMvc.perform(post("/tasks/summary"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary").value("The quarterly report needs attention."))
			.andExpect(jsonPath("$.plan[0]").value("Finish the quarterly report first."))
			.andExpect(jsonPath("$.plan[1]").value("Then review lower-priority work."));
	}

	@Test
	void summarizeTasksReturnsStructuredErrorWhenOpenAiConfigurationIsMissing() throws Exception {
		saveTask("Submit quarterly report");
		when(this.tokenCounter.countTaskSummaryInputTokens(any())).thenThrow(new MissingOpenAiConfigurationException());

		this.mockMvc.perform(post("/tasks/summary"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.error").value("AI_CONFIGURATION_MISSING"))
			.andExpect(jsonPath("$.message").value("AI configuration is missing. Set OPENAI_API_KEY to enable AI task features."));
	}

	@Test
	void summarizeTasksReturnsStructuredAiFailureForProviderFailure() throws Exception {
		saveTask("Submit quarterly report");
		when(this.taskAiClient.summarizeTasks(any()))
			.thenThrow(new AiTaskException("OpenAI task summary request failed"));

		this.mockMvc.perform(post("/tasks/summary"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.status").value(502))
			.andExpect(jsonPath("$.error").value("AI_TASK_FAILED"))
			.andExpect(jsonPath("$.message").value("OpenAI task summary request failed"));
	}

	@Test
	void summarizeTasksReturnsStructuredInvalidOutputErrorForMalformedModelOutput() throws Exception {
		saveTask("Submit quarterly report");
		when(this.taskAiClient.summarizeTasks(any()))
			.thenThrow(new AiTaskInvalidOutputException("AI response did not include a task summary"));

		this.mockMvc.perform(post("/tasks/summary"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.status").value(502))
			.andExpect(jsonPath("$.error").value("AI_TASK_OUTPUT_INVALID"))
			.andExpect(jsonPath("$.message").value("AI response did not include a task summary"));
	}

	private void saveTask(String title) {
		Task task = new Task();
		task.setTitle(title);
		task.setDescription("Created for summary controller test");
		task.setDueDate(LocalDate.of(2026, 5, 29));
		task.setPriority(TaskPriority.HIGH);
		task.setStatus(TaskStatus.TODO);
		this.taskRepository.save(task);
	}
}
