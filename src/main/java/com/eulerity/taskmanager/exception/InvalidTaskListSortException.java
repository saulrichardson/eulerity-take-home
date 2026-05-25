package com.eulerity.taskmanager.exception;

public class InvalidTaskListSortException extends RuntimeException {

	public InvalidTaskListSortException(String sort) {
		super("sort must be one of id, dueDate, or priority");
	}
}
