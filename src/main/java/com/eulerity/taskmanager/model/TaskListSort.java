package com.eulerity.taskmanager.model;

import java.util.Optional;

public enum TaskListSort {

	ID("id"),
	DUE_DATE("dueDate"),
	PRIORITY("priority");

	private final String queryValue;

	TaskListSort(String queryValue) {
		this.queryValue = queryValue;
	}

	public static Optional<TaskListSort> findByQueryValue(String value) {
		if (value == null || value.isBlank()) {
			return Optional.of(ID);
		}
		for (TaskListSort sort : values()) {
			if (sort.queryValue.equalsIgnoreCase(value)) {
				return Optional.of(sort);
			}
		}
		return Optional.empty();
	}
}
