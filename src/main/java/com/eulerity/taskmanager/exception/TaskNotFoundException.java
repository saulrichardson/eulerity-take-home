package com.eulerity.taskmanager.exception;

public class TaskNotFoundException extends RuntimeException {

	private final Long taskId;

	public TaskNotFoundException(Long taskId) {
		super("Task " + taskId + " was not found");
		this.taskId = taskId;
	}

	public Long getTaskId() {
		return this.taskId;
	}
}
