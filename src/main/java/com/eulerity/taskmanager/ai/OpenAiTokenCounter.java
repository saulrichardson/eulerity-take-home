package com.eulerity.taskmanager.ai;

import com.openai.errors.OpenAIException;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTokenCounter implements TaskAiTokenCounter {

	private final OpenAiClientProvider clientProvider;

	private final OpenAiTaskRequestFactory requestFactory;

	public OpenAiTokenCounter(OpenAiClientProvider clientProvider, OpenAiTaskRequestFactory requestFactory) {
		this.clientProvider = clientProvider;
		this.requestFactory = requestFactory;
	}

	@Override
	public long countTaskSuggestionInputTokens(AiTaskSuggestionPrompt prompt) {
		try {
			return this.clientProvider.getClient()
				.responses()
				.inputTokens()
				.count(this.requestFactory.suggestionCountParams(prompt))
				.inputTokens();
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (OpenAIException ex) {
			throw new AiTaskException("OpenAI task suggestion token count request failed", ex);
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("OpenAI task suggestion token count request failed", ex);
		}
	}

	@Override
	public long countTaskSummaryInputTokens(AiTaskSummaryPrompt prompt) {
		try {
			return this.clientProvider.getClient()
				.responses()
				.inputTokens()
				.count(this.requestFactory.summaryCountParams(prompt))
				.inputTokens();
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (OpenAIException ex) {
			throw new AiTaskException("OpenAI task summary token count request failed", ex);
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("OpenAI task summary token count request failed", ex);
		}
	}
}
