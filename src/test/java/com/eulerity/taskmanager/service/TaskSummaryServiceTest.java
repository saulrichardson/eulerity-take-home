package com.eulerity.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class TaskSummaryServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 5, 25);

	@Mock
	private TaskRepository taskRepository;

	@Mock
	private TaskAiClient taskAiClient;

	@Mock
	private TaskAiTokenCounter tokenCounter;

	private OpenAiProperties openAiProperties;

	private AiSummaryProperties summaryProperties;

	private TaskSummaryService taskSummaryService;

	@BeforeEach
	void setUp() {
		this.openAiProperties = new OpenAiProperties();
		this.summaryProperties = new AiSummaryProperties();
		this.taskSummaryService = new TaskSummaryService(this.taskRepository, this.taskAiClient, this.tokenCounter,
				fixedClock(), this.openAiProperties, this.summaryProperties);
		lenient().when(this.tokenCounter.countTaskSummaryInputTokens(any())).thenReturn(100L);
	}

	@Test
	void summarizeTasksReturnsLocalEmptyStateWithoutCallingAi() {
		when(this.taskRepository.findAll()).thenReturn(List.of());

		TaskSummaryResponse response = this.taskSummaryService.summarizeTasks();

		assertThat(response.summary()).isEqualTo("You do not have any tasks yet.");
		assertThat(response.plan()).containsExactly("Create a task to start planning your work.");
		verifyNoInteractions(this.taskAiClient, this.tokenCounter);
	}

	@Test
	void summarizeTasksSendsThirtyOrFewerTasksToAi() {
		List<Task> tasks = IntStream.rangeClosed(1, 30)
			.mapToObj(index -> task(index, "Task " + index, TODAY.plusDays(index), TaskPriority.MEDIUM,
					TaskStatus.TODO))
			.toList();
		when(this.taskRepository.findAll()).thenReturn(tasks);
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		assertThat(capturedSummaryPrompt().tasks()).hasSize(30);
	}

	@Test
	void summarizeTasksCapsLargeTaskListsAtThirtyAndPrioritizesRelevantOpenWork() {
		List<Task> tasks = new ArrayList<>();
		for (int index = 1; index <= 30; index++) {
			tasks.add(task(index, "Done low " + index, TODAY.plusDays(30L + index), TaskPriority.LOW,
					TaskStatus.DONE));
		}
		for (int index = 31; index <= 35; index++) {
			tasks.add(task(index, "Open high " + index, TODAY.minusDays(index - 30L), TaskPriority.HIGH,
					TaskStatus.TODO));
		}
		when(this.taskRepository.findAll()).thenReturn(tasks);
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		List<AiTaskSummaryContext> contexts = capturedSummaryPrompt().tasks();
		assertThat(contexts).hasSize(AiSummaryProperties.MAX_CANDIDATE_TASK_CAP);
		assertThat(contexts.stream().map(AiTaskSummaryContext::id).limit(5))
			.containsExactly(35L, 34L, 33L, 32L, 31L);
		assertThat(contexts).extracting(AiTaskSummaryContext::id).contains(31L, 32L, 33L, 34L, 35L);
		assertThat(contexts).extracting(AiTaskSummaryContext::id).doesNotContain(26L, 27L, 28L, 29L, 30L);
	}

	@Test
	void summarizeTasksComputesDueTimingInDaysAndWholeWeeks() {
		List<Task> tasks = List.of(
				task(1, "Due today", TODAY, TaskPriority.MEDIUM, TaskStatus.TODO),
				task(2, "Overdue days", TODAY.minusDays(2), TaskPriority.MEDIUM, TaskStatus.TODO),
				task(3, "Overdue weeks", TODAY.minusWeeks(3), TaskPriority.MEDIUM, TaskStatus.TODO),
				task(4, "Due days", TODAY.plusDays(4), TaskPriority.MEDIUM, TaskStatus.TODO),
				task(5, "Due weeks", TODAY.plusWeeks(2), TaskPriority.MEDIUM, TaskStatus.TODO));
		when(this.taskRepository.findAll()).thenReturn(tasks);
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		Map<String, AiTaskSummaryContext> contextsByTitle = capturedSummaryPrompt()
			.tasks()
			.stream()
			.collect(Collectors.toMap(AiTaskSummaryContext::title, Function.identity()));
		assertThat(contextsByTitle.get("Due today").dueTiming()).isEqualTo("due today");
		assertThat(contextsByTitle.get("Overdue days").dueTiming()).isEqualTo("overdue by 2 days");
		assertThat(contextsByTitle.get("Overdue weeks").dueTiming()).isEqualTo("overdue by 3 weeks");
		assertThat(contextsByTitle.get("Due days").dueTiming()).isEqualTo("due in 4 days");
		assertThat(contextsByTitle.get("Due weeks").dueTiming()).isEqualTo("due in 2 weeks");
	}

	@Test
	void summarizeTasksSendsFewerTasksWhenBudgetIsConstrained() {
		this.summaryProperties.setApplicationInputTokenBudget(100);
		List<Task> tasks = IntStream.rangeClosed(1, 5)
			.mapToObj(index -> task(index, "Task " + index, TODAY.plusDays(index), TaskPriority.MEDIUM,
					TaskStatus.TODO))
			.toList();
		when(this.taskRepository.findAll()).thenReturn(tasks);
		when(this.tokenCounter.countTaskSummaryInputTokens(any()))
			.thenAnswer(invocation -> ((AiTaskSummaryPrompt) invocation.getArgument(0)).tasks().size() * 50L);
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		assertThat(capturedSummaryPrompt().tasks()).hasSize(2);
	}

	@Test
	void summarizeTasksKeepsHigherRelevanceUnderBudgetPressure() {
		this.summaryProperties.setApplicationInputTokenBudget(200);
		List<Task> tasks = List.of(
				task(1, "Done low", TODAY.plusDays(20), TaskPriority.LOW, TaskStatus.DONE),
				task(2, "Open high overdue", TODAY.minusDays(2), TaskPriority.HIGH, TaskStatus.TODO),
				task(3, "Open medium due soon", TODAY.plusDays(2), TaskPriority.MEDIUM, TaskStatus.TODO));
		when(this.taskRepository.findAll()).thenReturn(tasks);
		when(this.tokenCounter.countTaskSummaryInputTokens(any()))
			.thenAnswer(invocation -> ((AiTaskSummaryPrompt) invocation.getArgument(0)).tasks().size() * 200L);
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		assertThat(capturedSummaryPrompt().tasks()).extracting(AiTaskSummaryContext::title)
			.contains("Open high overdue")
			.doesNotContain("Done low");
	}

	@Test
	void summarizeTasksShortensLongDescriptionsAtParagraphBoundaryAndPreservesMetadata() {
		this.summaryProperties.setPerTaskDescriptionPromptBudget(80);
		Task task = task(10, "Launch campaign", TODAY.plusDays(2), TaskPriority.HIGH, TaskStatus.IN_PROGRESS);
		task.setDescription("""
				Call Sam about campaign launch.

				Confirm budget before Friday. This second paragraph should not fit in the prompt description budget.
				""");
		when(this.taskRepository.findAll()).thenReturn(List.of(task));
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		AiTaskSummaryContext context = capturedSummaryPrompt().tasks().get(0);
		assertThat(context.id()).isEqualTo(10L);
		assertThat(context.title()).isEqualTo("Launch campaign");
		assertThat(context.dueDate()).isEqualTo(TODAY.plusDays(2));
		assertThat(context.dueTiming()).isEqualTo("due in 2 days");
		assertThat(context.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(context.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(context.description()).isEqualTo("Call Sam about campaign launch. [description shortened]");
	}

	@Test
	void summarizeTasksUsesWholeRecordsAndAuthoritativeTokenBudget() {
		this.openAiProperties.setModelContextWindowTokens(300);
		this.openAiProperties.setContextUsageRatio(0.90);
		this.openAiProperties.setSummaryMaxOutputTokens(20);
		this.openAiProperties.setRequestOverheadReserveTokens(30);
		this.summaryProperties.setApplicationInputTokenBudget(1000);
		when(this.taskRepository.findAll()).thenReturn(List.of(
				task(1, "Open high overdue", TODAY.minusDays(1), TaskPriority.HIGH, TaskStatus.TODO),
				task(2, "Done low later", TODAY.plusDays(30), TaskPriority.LOW, TaskStatus.DONE)));
		when(this.tokenCounter.countTaskSummaryInputTokens(any()))
			.thenAnswer(invocation -> ((AiTaskSummaryPrompt) invocation.getArgument(0)).tasks().size() == 2 ? 221L : 100L);
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		AiTaskSummaryPrompt prompt = capturedSummaryPrompt();
		assertThat(this.openAiProperties.hardContextCeilingTokens()).isEqualTo(270);
		assertThat(prompt.tasks()).extracting(AiTaskSummaryContext::title)
			.containsExactly("Open high overdue");
	}

	@Test
	void summarizeTasksDropsDescriptionsBeforeDroppingTaskMetadata() {
		this.summaryProperties.setApplicationInputTokenBudget(200);
		Task task = task(1, "Open high", TODAY.plusDays(1), TaskPriority.HIGH, TaskStatus.TODO);
		task.setDescription("Detailed context that does not fit.");
		when(this.taskRepository.findAll()).thenReturn(List.of(task));
		when(this.tokenCounter.countTaskSummaryInputTokens(any())).thenAnswer(invocation -> {
			AiTaskSummaryPrompt prompt = invocation.getArgument(0);
			boolean hasDescription = prompt.tasks().stream().anyMatch(context -> context.description() != null);
			return hasDescription ? 1000L : 100L;
		});
		when(this.taskAiClient.summarizeTasks(any())).thenReturn(summaryResponse());

		this.taskSummaryService.summarizeTasks();

		AiTaskSummaryContext context = capturedSummaryPrompt().tasks().get(0);
		assertThat(context.title()).isEqualTo("Open high");
		assertThat(context.dueDate()).isEqualTo(TODAY.plusDays(1));
		assertThat(context.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(context.status()).isEqualTo(TaskStatus.TODO);
		assertThat(context.description()).isNull();
	}

	@Test
	void summarizeTasksFailsWhenNoCoherentRecordFitsAuthoritativeTokenBudget() {
		this.summaryProperties.setApplicationInputTokenBudget(10);
		when(this.taskRepository.findAll()).thenReturn(List.of(
				task(1, "Open high", TODAY.plusDays(1), TaskPriority.HIGH, TaskStatus.TODO)));
		when(this.tokenCounter.countTaskSummaryInputTokens(any())).thenReturn(100L);

		assertThatThrownBy(() -> this.taskSummaryService.summarizeTasks())
			.isInstanceOf(AiTaskException.class)
			.hasMessage("AI task summary context budget could not fit a coherent task record");
		verifyNoInteractions(this.taskAiClient);
	}

	@Test
	void summarizeTasksWrapsUnexpectedTokenCounterFailure() {
		when(this.taskRepository.findAll()).thenReturn(List.of(
				task(1, "Open high", TODAY.plusDays(1), TaskPriority.HIGH, TaskStatus.TODO)));
		when(this.tokenCounter.countTaskSummaryInputTokens(any())).thenThrow(new IllegalStateException("counter failed"));

		assertThatThrownBy(() -> this.taskSummaryService.summarizeTasks())
			.isInstanceOf(AiTaskException.class)
			.hasMessage("AI task summary token preflight failed");
		verifyNoInteractions(this.taskAiClient);
	}

	private AiTaskSummaryPrompt capturedSummaryPrompt() {
		ArgumentCaptor<AiTaskSummaryPrompt> captor = ArgumentCaptor.captor();
		verify(this.taskAiClient).summarizeTasks(captor.capture());
		return captor.getValue();
	}

	private static TaskSummaryResponse summaryResponse() {
		return new TaskSummaryResponse("You have tasks to work through.", List.of("Start with the most urgent task."));
	}

	private static Clock fixedClock() {
		return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
	}

	private static Task task(long id, String title, LocalDate dueDate, TaskPriority priority, TaskStatus status) {
		Task task = new Task();
		task.setId(id);
		task.setTitle(title);
		task.setDescription("Description for " + title);
		task.setDueDate(dueDate);
		task.setPriority(priority);
		task.setStatus(status);
		return task;
	}
}
