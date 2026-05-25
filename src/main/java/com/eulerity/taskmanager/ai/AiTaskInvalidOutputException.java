package com.eulerity.taskmanager.ai;

public class AiTaskInvalidOutputException extends AiTaskException {

	public AiTaskInvalidOutputException(String message) {
		super(message);
	}

	public AiTaskInvalidOutputException(String message, Throwable cause) {
		super(message, cause);
	}
}
