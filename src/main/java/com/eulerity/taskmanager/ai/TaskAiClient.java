package com.eulerity.taskmanager.ai;

import com.eulerity.taskmanager.dto.TaskSummaryResponse;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;

public interface TaskAiClient {

	TaskSuggestionResponse suggestTask(AiTaskSuggestionPrompt prompt);

	TaskSummaryResponse summarizeTasks(AiTaskSummaryPrompt prompt);
}
