package com.eulerity.taskmanager.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import com.eulerity.taskmanager.config.OpenAiProperties;

class OpenAiClientProviderTest {

	@Test
	void getClientFailsWhenApiKeyIsMissing() {
		OpenAiProperties properties = new OpenAiProperties();
		OpenAiClientProvider provider = new OpenAiClientProvider(properties);

		assertThatThrownBy(provider::getClient)
			.isInstanceOf(MissingOpenAiConfigurationException.class)
			.hasMessage("AI configuration is missing. Set OPENAI_API_KEY to enable AI task features.");
	}

	@Test
	void getClientMemoizesConfiguredClient() {
		OpenAiProperties properties = new OpenAiProperties();
		properties.setApiKey("test-key");
		OpenAiClientProvider provider = new OpenAiClientProvider(properties);

		OpenAIClient firstClient = provider.getClient();

		assertThat(provider.getClient()).isSameAs(firstClient);
	}
}
