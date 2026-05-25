package com.eulerity.taskmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Validated
@ConfigurationProperties(prefix = "ai.summary")
public class AiSummaryProperties {

	public static final int MAX_CANDIDATE_TASK_CAP = 30;

	@Positive(message = "ai.summary.application-input-token-budget must be positive")
	private int applicationInputTokenBudget = 6000;

	@Min(value = 1, message = "ai.summary.candidate-task-cap must be at least 1")
	@Max(value = MAX_CANDIDATE_TASK_CAP, message = "ai.summary.candidate-task-cap must be no greater than 30")
	private int candidateTaskCap = MAX_CANDIDATE_TASK_CAP;

	@PositiveOrZero(message = "ai.summary.per-task-description-prompt-budget must be zero or positive")
	private int perTaskDescriptionPromptBudget = 1200;

	@Positive(message = "ai.summary.max-summary-length must be positive")
	private int maxSummaryLength = 1200;

	@Positive(message = "ai.summary.max-plan-items must be positive")
	private int maxPlanItems = 5;

	@Positive(message = "ai.summary.max-plan-item-length must be positive")
	private int maxPlanItemLength = 300;

	public int getApplicationInputTokenBudget() {
		return this.applicationInputTokenBudget;
	}

	public void setApplicationInputTokenBudget(int applicationInputTokenBudget) {
		this.applicationInputTokenBudget = applicationInputTokenBudget;
	}

	public int getCandidateTaskCap() {
		return this.candidateTaskCap;
	}

	public void setCandidateTaskCap(int candidateTaskCap) {
		this.candidateTaskCap = candidateTaskCap;
	}

	public int getPerTaskDescriptionPromptBudget() {
		return this.perTaskDescriptionPromptBudget;
	}

	public void setPerTaskDescriptionPromptBudget(int perTaskDescriptionPromptBudget) {
		this.perTaskDescriptionPromptBudget = perTaskDescriptionPromptBudget;
	}

	public int getMaxSummaryLength() {
		return this.maxSummaryLength;
	}

	public void setMaxSummaryLength(int maxSummaryLength) {
		this.maxSummaryLength = maxSummaryLength;
	}

	public int getMaxPlanItems() {
		return this.maxPlanItems;
	}

	public void setMaxPlanItems(int maxPlanItems) {
		this.maxPlanItems = maxPlanItems;
	}

	public int getMaxPlanItemLength() {
		return this.maxPlanItemLength;
	}

	public void setMaxPlanItemLength(int maxPlanItemLength) {
		this.maxPlanItemLength = maxPlanItemLength;
	}
}
