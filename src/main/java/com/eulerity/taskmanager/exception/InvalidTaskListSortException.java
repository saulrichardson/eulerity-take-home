package com.eulerity.taskmanager.exception;

public class InvalidTaskListSortException extends RuntimeException {

	public InvalidTaskListSortException(String sort) {
		super("Unsupported sort value '%s'. sort must be one of id, dueDate, or priority".formatted(sort));
	}
}
