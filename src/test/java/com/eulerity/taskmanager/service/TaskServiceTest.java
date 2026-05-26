package com.eulerity.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eulerity.taskmanager.dto.TaskCreateRequest;
import com.eulerity.taskmanager.dto.TaskResponse;
import com.eulerity.taskmanager.dto.TaskStatusUpdateRequest;
import com.eulerity.taskmanager.dto.TaskUpdateRequest;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.model.TaskListSort;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;
import com.eulerity.taskmanager.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

	@Mock
	private TaskRepository taskRepository;

	@InjectMocks
	private TaskService taskService;

	@Test
	void createTaskSavesAndReturnsTask() {
		TaskCreateRequest request = new TaskCreateRequest("Write report", "Quarterly report",
				LocalDate.of(2026, 5, 29), TaskPriority.HIGH, TaskStatus.TODO);
		when(this.taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
			Task saved = invocation.getArgument(0);
			saved.setId(1L);
			return saved;
		});

		TaskResponse response = this.taskService.createTask(request);

		assertThat(response.id()).isEqualTo(1L);
		assertThat(response.title()).isEqualTo("Write report");
		assertThat(response.description()).isEqualTo("Quarterly report");
		assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 5, 29));
		assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(response.status()).isEqualTo(TaskStatus.TODO);
	}

	@Test
	void listTasksReturnsTasksSortedById() {
		Task later = task(2L, "Later task", null, LocalDate.of(2026, 5, 31), TaskPriority.HIGH, TaskStatus.TODO);
		Task earlier = task(1L, "Plan sprint", null, LocalDate.of(2026, 5, 30), TaskPriority.MEDIUM,
				TaskStatus.IN_PROGRESS);
		when(this.taskRepository.findAll()).thenReturn(List.of(later, earlier));

		List<TaskResponse> responses = this.taskService.listTasks(null, null, TaskListSort.ID);

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).title()).isEqualTo("Plan sprint");
	}

	@Test
	void getTaskReturnsTaskById() {
		Task task = task(2L, "Pay invoice", "Vendor invoice", LocalDate.of(2026, 6, 1), TaskPriority.LOW,
				TaskStatus.TODO);
		when(this.taskRepository.findById(2L)).thenReturn(Optional.of(task));

		TaskResponse response = this.taskService.getTask(2L);

		assertThat(response.id()).isEqualTo(2L);
		assertThat(response.title()).isEqualTo("Pay invoice");
	}

	@Test
	void updateTaskReplacesStoredTask() {
		Task existing = task(3L, "Old title", "Old description", LocalDate.of(2026, 5, 28),
				TaskPriority.LOW, TaskStatus.TODO);
		TaskUpdateRequest request = new TaskUpdateRequest("New title", "New description",
				LocalDate.of(2026, 6, 3), TaskPriority.HIGH, TaskStatus.DONE);
		when(this.taskRepository.findById(3L)).thenReturn(Optional.of(existing));
		when(this.taskRepository.save(existing)).thenReturn(existing);

		TaskResponse response = this.taskService.updateTask(3L, request);

		assertThat(response.title()).isEqualTo("New title");
		assertThat(response.description()).isEqualTo("New description");
		assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 6, 3));
		assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(response.status()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void updateTaskStatusChangesOnlyStatus() {
		Task existing = task(3L, "Write report", "Quarterly report", LocalDate.of(2026, 5, 28),
				TaskPriority.LOW, TaskStatus.TODO);
		when(this.taskRepository.findById(3L)).thenReturn(Optional.of(existing));
		when(this.taskRepository.save(existing)).thenReturn(existing);

		TaskResponse response = this.taskService.updateTaskStatus(3L, new TaskStatusUpdateRequest(TaskStatus.DONE));

		assertThat(response.title()).isEqualTo("Write report");
		assertThat(response.description()).isEqualTo("Quarterly report");
		assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 5, 28));
		assertThat(response.priority()).isEqualTo(TaskPriority.LOW);
		assertThat(response.status()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void deleteTaskDeletesExistingTask() {
		when(this.taskRepository.existsById(anyLong())).thenReturn(true);

		this.taskService.deleteTask(4L);

		verify(this.taskRepository).deleteById(4L);
	}

	private static Task task(Long id, String title, String description, LocalDate dueDate, TaskPriority priority,
			TaskStatus status) {
		Task task = new Task();
		task.setId(id);
		task.setTitle(title);
		task.setDescription(description);
		task.setDueDate(dueDate);
		task.setPriority(priority);
		task.setStatus(status);
		return task;
	}
}
