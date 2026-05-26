package com.eulerity.taskmanager.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eulerity.taskmanager.dto.TaskCreateRequest;
import com.eulerity.taskmanager.dto.TaskResponse;
import com.eulerity.taskmanager.dto.TaskStatusUpdateRequest;
import com.eulerity.taskmanager.dto.TaskUpdateRequest;
import com.eulerity.taskmanager.exception.TaskNotFoundException;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.model.TaskListSort;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;
import com.eulerity.taskmanager.repository.TaskRepository;

@Service
@Transactional
public class TaskService {

	private final TaskRepository taskRepository;

	public TaskService(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	public TaskResponse createTask(TaskCreateRequest request) {
		Task task = new Task();
		apply(task, request.title(), request.description(), request.dueDate(), request.priority(), request.status());
		return toResponse(this.taskRepository.save(task));
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> listTasks(TaskStatus status, TaskPriority priority, TaskListSort sort) {
		return this.taskRepository.findAll()
			.stream()
			.filter(task -> status == null || task.getStatus() == status)
			.filter(task -> priority == null || task.getPriority() == priority)
			.sorted(comparatorFor(sort))
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public TaskResponse getTask(Long id) {
		return toResponse(findTask(id));
	}

	public TaskResponse updateTask(Long id, TaskUpdateRequest request) {
		Task task = findTask(id);
		apply(task, request.title(), request.description(), request.dueDate(), request.priority(), request.status());
		return toResponse(this.taskRepository.save(task));
	}

	public TaskResponse updateTaskStatus(Long id, TaskStatusUpdateRequest request) {
		Task task = findTask(id);
		task.setStatus(request.status());
		return toResponse(this.taskRepository.save(task));
	}

	public void deleteTask(Long id) {
		if (!this.taskRepository.existsById(id)) {
			throw new TaskNotFoundException(id);
		}
		this.taskRepository.deleteById(id);
	}

	private Task findTask(Long id) {
		return this.taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
	}

	private void apply(Task task, String title, String description, java.time.LocalDate dueDate,
			TaskPriority priority, TaskStatus status) {
		task.setTitle(title);
		task.setDescription(description);
		task.setDueDate(dueDate);
		task.setPriority(priority);
		task.setStatus(status);
	}

	private TaskResponse toResponse(Task task) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getDueDate(),
				task.getPriority(), task.getStatus());
	}

	private Comparator<Task> comparatorFor(TaskListSort sort) {
		return switch (sort) {
			case DUE_DATE -> Comparator.comparing(Task::getDueDate).thenComparing(Task::getId);
			case PRIORITY -> Comparator.comparingInt((Task task) -> priorityOrder(task.getPriority()))
				.thenComparing(Task::getDueDate)
				.thenComparing(Task::getId);
			case ID -> Comparator.comparing(Task::getId);
		};
	}

	private int priorityOrder(TaskPriority priority) {
		return switch (priority) {
			case HIGH -> 0;
			case MEDIUM -> 1;
			case LOW -> 2;
		};
	}
}
