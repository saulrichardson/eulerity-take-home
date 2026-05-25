package com.eulerity.taskmanager.ai;

public interface TaskAiTokenCounter {

	long countTaskSuggestionInputTokens(AiTaskSuggestionPrompt prompt);

	long countTaskSummaryInputTokens(AiTaskSummaryPrompt prompt);
}
