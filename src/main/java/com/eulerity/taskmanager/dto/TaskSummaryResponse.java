package com.eulerity.taskmanager.dto;

import java.util.List;

public record TaskSummaryResponse(
		String summary,
		List<String> plan) {
}
