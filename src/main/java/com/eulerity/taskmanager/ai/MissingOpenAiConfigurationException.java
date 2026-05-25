package com.eulerity.taskmanager.ai;

public class MissingOpenAiConfigurationException extends AiTaskException {

	public MissingOpenAiConfigurationException() {
		super("AI configuration is missing. Set OPENAI_API_KEY to enable AI task features.");
	}
}
