package com.eulerity.taskmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.eulerity.taskmanager.model.TaskFieldLimits;

class StaticUiLimitsTest {

	@Test
	void staticUiLimitsMatchBackendLimits() throws Exception {
		String html = Files.readString(Path.of("src/main/resources/static/index.html"));

		assertThat(html).contains("id=\"title\" name=\"title\" required maxlength=\"%d\""
			.formatted(TaskFieldLimits.TITLE_MAX_LENGTH));
		assertThat(html).contains("id=\"description\" name=\"description\" maxlength=\"%d\""
			.formatted(TaskFieldLimits.DESCRIPTION_MAX_LENGTH));
		assertThat(html).contains("id=\"suggestDescription\" name=\"suggestDescription\" required maxlength=\"%d\""
			.formatted(TaskFieldLimits.AI_REQUEST_DESCRIPTION_MAX_LENGTH));
	}

	@Test
	void staticUiBlocksDirectCreateFromNoDateSuggestion() throws Exception {
		String html = Files.readString(Path.of("src/main/resources/static/index.html"));

		assertThat(html).contains("suggestion.dueDate || \"No due date\"");
		assertThat(html).contains("createButton.disabled = !hasDueDate");
		assertThat(html).contains("Add a due date in the task form before creating this task.");
		assertThat(html).contains("if (!latestSuggestion.dueDate)");
	}
}
