package com.eulerity.taskmanager.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.stereotype.Component;

import com.eulerity.taskmanager.config.OpenAiProperties;

@Component
class OpenAiClientProvider {

	private final OpenAiProperties properties;

	private volatile OpenAIClient client;

	OpenAiClientProvider(OpenAiProperties properties) {
		this.properties = properties;
	}

	OpenAIClient getClient() {
		if (!this.properties.hasApiKey()) {
			throw new MissingOpenAiConfigurationException();
		}
		OpenAIClient existingClient = this.client;
		if (existingClient != null) {
			return existingClient;
		}
		synchronized (this) {
			if (this.client == null) {
				this.client = OpenAIOkHttpClient.builder().apiKey(this.properties.getApiKey()).build();
			}
			return this.client;
		}
	}
}
