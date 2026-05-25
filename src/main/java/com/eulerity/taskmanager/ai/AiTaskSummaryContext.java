package com.eulerity.taskmanager.ai;

import java.time.LocalDate;

import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

public record AiTaskSummaryContext(
		Long id,
		String title,
		String description,
		LocalDate dueDate,
		TaskPriority priority,
		TaskStatus status,
		String dueTiming) {

	public String toPromptRecord() {
		return new StringBuilder()
			.append("- id: ")
			.append(this.id)
			.append("\n  title: ")
			.append(this.title)
			.append("\n  dueDate: ")
			.append(this.dueDate)
			.append("\n  dueTiming: ")
			.append(this.dueTiming)
			.append("\n  priority: ")
			.append(this.priority)
			.append("\n  status: ")
			.append(this.status)
			.append("\n  description: ")
			.append(this.description == null || this.description.isBlank() ? "(none)" : this.description)
			.append('\n')
			.toString();
	}
}
