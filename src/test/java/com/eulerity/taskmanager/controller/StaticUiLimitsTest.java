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
		String app = Files.readString(Path.of("src/main/resources/static/app.js"));
		String ui = Files.readString(Path.of("src/main/resources/static/ui.js"));

		assertThat(ui).contains("suggestion.dueDate || \"No due date\"");
		assertThat(ui).contains("createButton.disabled = !hasDueDate");
		assertThat(ui).contains("Add a due date in the task form before creating this task.");
		assertThat(app).contains("if (!state.latestSuggestion || !state.latestSuggestion.dueDate)");
	}
}
