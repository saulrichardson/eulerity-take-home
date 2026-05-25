package com.eulerity.taskmanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eulerity.taskmanager.ai.AiTaskException;
import com.eulerity.taskmanager.ai.AiTaskSummaryContext;
import com.eulerity.taskmanager.ai.AiTaskSummaryPrompt;
import com.eulerity.taskmanager.ai.TaskAiClient;
import com.eulerity.taskmanager.ai.TaskAiTokenCounter;
import com.eulerity.taskmanager.config.AiSummaryProperties;
import com.eulerity.taskmanager.config.OpenAiProperties;
import com.eulerity.taskmanager.dto.TaskSummaryResponse;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;
import com.eulerity.taskmanager.repository.TaskRepository;

@Service
public class TaskSummaryService {

	private final TaskRepository taskRepository;

	private final TaskAiClient taskAiClient;

	private final TaskAiTokenCounter tokenCounter;

	private final Clock clock;

	private final OpenAiProperties openAiProperties;

	private final AiSummaryProperties summaryProperties;

	public TaskSummaryService(TaskRepository taskRepository, TaskAiClient taskAiClient, TaskAiTokenCounter tokenCounter,
			Clock clock, OpenAiProperties openAiProperties, AiSummaryProperties summaryProperties) {
		this.taskRepository = taskRepository;
		this.taskAiClient = taskAiClient;
		this.tokenCounter = tokenCounter;
		this.clock = clock;
		this.openAiProperties = openAiProperties;
		this.summaryProperties = summaryProperties;
	}

	@Transactional(readOnly = true)
	public TaskSummaryResponse summarizeTasks() {
		List<Task> tasks = this.taskRepository.findAll();
		if (tasks.isEmpty()) {
			return new TaskSummaryResponse("You do not have any tasks yet.",
					List.of("Create a task to start planning your work."));
		}

		LocalDate today = LocalDate.now(this.clock);
		try {
			AiTaskSummaryPrompt prompt = buildPrompt(tasks, today);
			return this.taskAiClient.summarizeTasks(prompt);
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("AI task summary request failed", ex);
		}
	}

	private AiTaskSummaryPrompt buildPrompt(List<Task> tasks, LocalDate today) {
		List<Task> candidates = tasks.stream()
			.sorted(relevanceComparator(today))
			.limit(this.summaryProperties.getCandidateTaskCap())
			.toList();

		AiTaskSummaryPrompt metadataOnlyPrompt = null;
		for (int descriptionBudget : descriptionReductionBudgets()) {
			AiTaskSummaryPrompt prompt = promptFor(candidates, today, descriptionBudget);
			if (fitsSummaryBudget(prompt)) {
				return prompt;
			}
			if (descriptionBudget == 0) {
				metadataOnlyPrompt = prompt;
			}
		}

		if (metadataOnlyPrompt == null) {
			metadataOnlyPrompt = promptFor(candidates, today, 0);
		}
		List<AiTaskSummaryContext> metadataOnlyTasks = metadataOnlyPrompt.tasks();
		for (int size = metadataOnlyTasks.size() - 1; size >= 1; size--) {
			AiTaskSummaryPrompt prompt = new AiTaskSummaryPrompt(metadataOnlyTasks.subList(0, size));
			if (fitsSummaryBudget(prompt)) {
				return prompt;
			}
		}

		throw new AiTaskException("AI task summary context budget could not fit a coherent task record");
	}

	private AiTaskSummaryPrompt promptFor(List<Task> tasks, LocalDate today, int descriptionBudget) {
		return new AiTaskSummaryPrompt(tasks.stream()
			.map(task -> toSummaryContext(task, today, descriptionForPrompt(task.getDescription(), descriptionBudget)))
			.toList());
	}

	private AiTaskSummaryContext toSummaryContext(Task task, LocalDate today, String description) {
		return new AiTaskSummaryContext(task.getId(), task.getTitle(), description, task.getDueDate(),
				task.getPriority(), task.getStatus(), dueTiming(task.getDueDate(), today));
	}

	private boolean fitsSummaryBudget(AiTaskSummaryPrompt prompt) {
		if (prompt.tasks().isEmpty()) {
			return false;
		}
		long inputTokens = countSummaryInputTokens(prompt);
		return inputTokens <= this.summaryProperties.getApplicationInputTokenBudget()
				&& inputTokens
						+ this.openAiProperties.getSummaryMaxOutputTokens()
						+ this.openAiProperties.getRequestOverheadReserveTokens()
				<= this.openAiProperties.hardContextCeilingTokens();
	}

	private long countSummaryInputTokens(AiTaskSummaryPrompt prompt) {
		try {
			return this.tokenCounter.countTaskSummaryInputTokens(prompt);
		}
		catch (AiTaskException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new AiTaskException("AI task summary token preflight failed", ex);
		}
	}

	private List<Integer> descriptionReductionBudgets() {
		int initialBudget = this.summaryProperties.getPerTaskDescriptionPromptBudget();
		List<Integer> budgets = new ArrayList<>();
		addBudget(budgets, initialBudget);
		for (int reducedBudget : List.of(1200, 600, 300, 120, 40, 0)) {
			if (reducedBudget < initialBudget) {
				addBudget(budgets, reducedBudget);
			}
		}
		addBudget(budgets, 0);
		return budgets;
	}

	private void addBudget(List<Integer> budgets, int budget) {
		if (!budgets.contains(budget)) {
			budgets.add(budget);
		}
	}

	private String descriptionForPrompt(String description, int maxCharacters) {
		if (description == null || description.isBlank() || maxCharacters <= 0) {
			return null;
		}
		if (description.length() <= maxCharacters) {
			return description;
		}

		String marker = " [description shortened]";
		int contentBudget = maxCharacters - marker.length();
		if (contentBudget <= 0) {
			return null;
		}

		String shortened = naturalPrefix(description, contentBudget).trim();
		if (shortened.isBlank()) {
			return null;
		}
		return shortened + marker;
	}

	private String naturalPrefix(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}

		int boundary = lastParagraphBoundary(text, maxLength);
		if (boundary <= 0) {
			boundary = lastSentenceBoundary(text, maxLength);
		}
		if (boundary <= 0) {
			boundary = lastWordBoundary(text, maxLength);
		}
		if (boundary <= 0) {
			boundary = maxLength;
		}
		return text.substring(0, boundary);
	}

	private int lastParagraphBoundary(String text, int maxLength) {
		int boundary = text.lastIndexOf("\n\n", maxLength);
		if (boundary <= 0) {
			boundary = text.lastIndexOf("\r\n\r\n", maxLength);
		}
		return boundary;
	}

	private int lastSentenceBoundary(String text, int maxLength) {
		int boundary = -1;
		int limit = Math.min(maxLength, text.length() - 1);
		for (int index = 0; index <= limit; index++) {
			char value = text.charAt(index);
			if ((value == '.' || value == '!' || value == '?')
					&& (index == text.length() - 1 || Character.isWhitespace(text.charAt(index + 1)))) {
				boundary = index + 1;
			}
		}
		return boundary;
	}

	private int lastWordBoundary(String text, int maxLength) {
		int limit = Math.min(maxLength, text.length() - 1);
		for (int index = limit; index > 0; index--) {
			if (Character.isWhitespace(text.charAt(index))) {
				return index;
			}
		}
		return -1;
	}

	private String dueTiming(LocalDate dueDate, LocalDate today) {
		long daysAway = ChronoUnit.DAYS.between(today, dueDate);
		if (daysAway == 0) {
			return "due today";
		}
		if (daysAway < 0) {
			long overdueDays = Math.abs(daysAway);
			if (overdueDays < 7) {
				return "overdue by " + overdueDays + " " + plural(overdueDays, "day");
			}
			long weeks = overdueDays / 7;
			return "overdue by " + weeks + " " + plural(weeks, "week");
		}
		if (daysAway < 7) {
			return "due in " + daysAway + " " + plural(daysAway, "day");
		}
		long weeks = daysAway / 7;
		return "due in " + weeks + " " + plural(weeks, "week");
	}

	private String plural(long value, String unit) {
		return value == 1 ? unit : unit + "s";
	}

	private Comparator<Task> relevanceComparator(LocalDate today) {
		return Comparator.comparingInt((Task task) -> relevanceScore(task, today))
			.reversed()
			.thenComparing(Task::getDueDate)
			.thenComparingInt(task -> priorityOrder(task.getPriority()))
			.thenComparingInt(task -> statusOrder(task.getStatus()))
			.thenComparing(Task::getId);
	}

	private int relevanceScore(Task task, LocalDate today) {
		int score = 0;
		if (task.getStatus() != TaskStatus.DONE) {
			score += 1000;
		}
		if (task.getStatus() == TaskStatus.IN_PROGRESS) {
			score += 250;
		}
		if (task.getStatus() == TaskStatus.TODO) {
			score += 100;
		}
		score += switch (task.getPriority()) {
			case HIGH -> 300;
			case MEDIUM -> 150;
			case LOW -> 50;
		};
		long daysAway = ChronoUnit.DAYS.between(today, task.getDueDate());
		if (daysAway < 0) {
			score += 400;
		}
		else if (daysAway == 0) {
			score += 350;
		}
		else if (daysAway < 7) {
			score += 250;
		}
		else if (daysAway < 14) {
			score += 100;
		}
		return score;
	}

	private int priorityOrder(TaskPriority priority) {
		return switch (priority) {
			case HIGH -> 0;
			case MEDIUM -> 1;
			case LOW -> 2;
		};
	}

	private int statusOrder(TaskStatus status) {
		return switch (status) {
			case IN_PROGRESS -> 0;
			case TODO -> 1;
			case DONE -> 2;
		};
	}
}
