package com.eulerity.taskmanager.ai;

import java.util.List;

public record AiTaskSummaryPrompt(List<AiTaskSummaryContext> tasks) {

	public AiTaskSummaryPrompt {
		tasks = List.copyOf(tasks);
	}
}
