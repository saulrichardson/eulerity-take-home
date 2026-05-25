package com.eulerity.taskmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Validated
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

	private String apiKey = "";

	private String model = "gpt-5.4-nano";

	@Positive(message = "openai.model-context-window-tokens must be positive")
	private int modelContextWindowTokens = 400000;

	@DecimalMin(value = "0.0", inclusive = false, message = "openai.context-usage-ratio must be greater than 0")
	@DecimalMax(value = "0.90", message = "openai.context-usage-ratio must be no greater than 0.90")
	private double contextUsageRatio = 0.90;

	@Positive(message = "openai.suggestion-max-output-tokens must be positive")
	private int suggestionMaxOutputTokens = 2048;

	@Positive(message = "openai.summary-max-output-tokens must be positive")
	private int summaryMaxOutputTokens = 1024;

	@PositiveOrZero(message = "openai.request-overhead-reserve-tokens must be zero or positive")
	private int requestOverheadReserveTokens = 1000;

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		if (this.model == null || this.model.isBlank()) {
			return "gpt-5.4-nano";
		}
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getModelContextWindowTokens() {
		return this.modelContextWindowTokens;
	}

	public void setModelContextWindowTokens(int modelContextWindowTokens) {
		this.modelContextWindowTokens = modelContextWindowTokens;
	}

	public double getContextUsageRatio() {
		return this.contextUsageRatio;
	}

	public void setContextUsageRatio(double contextUsageRatio) {
		this.contextUsageRatio = contextUsageRatio;
	}

	public int getSuggestionMaxOutputTokens() {
		return this.suggestionMaxOutputTokens;
	}

	public void setSuggestionMaxOutputTokens(int suggestionMaxOutputTokens) {
		this.suggestionMaxOutputTokens = suggestionMaxOutputTokens;
	}

	public int getSummaryMaxOutputTokens() {
		return this.summaryMaxOutputTokens;
	}

	public void setSummaryMaxOutputTokens(int summaryMaxOutputTokens) {
		this.summaryMaxOutputTokens = summaryMaxOutputTokens;
	}

	public int getRequestOverheadReserveTokens() {
		return this.requestOverheadReserveTokens;
	}

	public void setRequestOverheadReserveTokens(int requestOverheadReserveTokens) {
		this.requestOverheadReserveTokens = requestOverheadReserveTokens;
	}

	public int hardContextCeilingTokens() {
		return (int) Math.floor(this.modelContextWindowTokens * this.contextUsageRatio);
	}

	public boolean hasApiKey() {
		return this.apiKey != null && !this.apiKey.isBlank();
	}
}
