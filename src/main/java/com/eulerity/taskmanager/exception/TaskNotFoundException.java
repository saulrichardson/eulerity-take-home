package com.eulerity.taskmanager.exception;

public class TaskNotFoundException extends RuntimeException {

	public TaskNotFoundException(Long taskId) {
		super("Task " + taskId + " was not found");
	}
}
