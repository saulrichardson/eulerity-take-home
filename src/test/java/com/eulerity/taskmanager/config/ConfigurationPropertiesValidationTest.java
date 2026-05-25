package com.eulerity.taskmanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class ConfigurationPropertiesValidationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void validAiBudgetPropertiesBind() {
		this.contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(OpenAiProperties.class).hardContextCeilingTokens()).isEqualTo(360000);
			assertThat(context.getBean(AiSummaryProperties.class).getCandidateTaskCap()).isEqualTo(30);
		});
	}

	@Test
	void invalidContextUsageRatioFailsStartup() {
		this.contextRunner.withPropertyValues("openai.context-usage-ratio=1.0").run(context -> {
			assertThat(context).hasFailed();
			assertStartupFailureContains(context.getStartupFailure(), "openai.context-usage-ratio");
		});
	}

	@Test
	void invalidOpenAiTokenLimitsFailStartup() {
		this.contextRunner.withPropertyValues("openai.model-context-window-tokens=0").run(context -> {
			assertThat(context).hasFailed();
			assertStartupFailureContains(context.getStartupFailure(), "openai.model-context-window-tokens");
		});
		this.contextRunner.withPropertyValues("openai.summary-max-output-tokens=0").run(context -> {
			assertThat(context).hasFailed();
			assertStartupFailureContains(context.getStartupFailure(), "openai.summary-max-output-tokens");
		});
	}

	@Test
	void invalidSummaryBudgetPropertiesFailStartup() {
		this.contextRunner.withPropertyValues("ai.summary.candidate-task-cap=31").run(context -> {
			assertThat(context).hasFailed();
			assertStartupFailureContains(context.getStartupFailure(), "ai.summary.candidate-task-cap");
		});
		this.contextRunner.withPropertyValues("ai.summary.application-input-token-budget=0").run(context -> {
			assertThat(context).hasFailed();
			assertStartupFailureContains(context.getStartupFailure(), "ai.summary.application-input-token-budget");
		});
	}

	private static void assertStartupFailureContains(Throwable failure, String text) {
		assertThat(failure).isNotNull();
		assertThat(causeMessages(failure)).contains(text);
	}

	private static String causeMessages(Throwable failure) {
		StringBuilder messages = new StringBuilder();
		Throwable current = failure;
		while (current != null) {
			messages.append(current.getMessage()).append('\n');
			current = current.getCause();
		}
		return messages.toString();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties({ OpenAiProperties.class, AiSummaryProperties.class })
	static class TestConfiguration {
	}
}
