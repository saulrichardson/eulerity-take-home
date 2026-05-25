package com.eulerity.taskmanager.dto;

import java.time.LocalDate;

import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;

public record TaskResponse(
		Long id,
		String title,
		String description,
		LocalDate dueDate,
		TaskPriority priority,
		TaskStatus status) {
}
