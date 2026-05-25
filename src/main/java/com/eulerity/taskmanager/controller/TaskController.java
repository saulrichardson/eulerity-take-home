package com.eulerity.taskmanager.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.eulerity.taskmanager.dto.TaskCreateRequest;
import com.eulerity.taskmanager.dto.TaskResponse;
import com.eulerity.taskmanager.dto.TaskStatusUpdateRequest;
import com.eulerity.taskmanager.dto.TaskSummaryResponse;
import com.eulerity.taskmanager.dto.TaskSuggestionRequest;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;
import com.eulerity.taskmanager.dto.TaskUpdateRequest;
import com.eulerity.taskmanager.exception.InvalidTaskListSortException;
import com.eulerity.taskmanager.model.TaskListSort;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;
import com.eulerity.taskmanager.service.TaskService;
import com.eulerity.taskmanager.service.TaskSummaryService;
import com.eulerity.taskmanager.service.TaskSuggestionService;

@RestController
@RequestMapping("/tasks")
public class TaskController {

	private final TaskService taskService;

	private final TaskSuggestionService taskSuggestionService;

	private final TaskSummaryService taskSummaryService;

	public TaskController(TaskService taskService, TaskSuggestionService taskSuggestionService,
			TaskSummaryService taskSummaryService) {
		this.taskService = taskService;
		this.taskSuggestionService = taskSuggestionService;
		this.taskSummaryService = taskSummaryService;
	}

	@PostMapping
	public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest request) {
		TaskResponse created = this.taskService.createTask(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(created.id())
			.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<TaskResponse> listTasks(@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) TaskPriority priority,
			@RequestParam(required = false, defaultValue = "id") String sort) {
		TaskListSort taskListSort = TaskListSort.findByQueryValue(sort)
			.orElseThrow(() -> new InvalidTaskListSortException(sort));
		return this.taskService.listTasks(status, priority, taskListSort);
	}

	@GetMapping("/{id}")
	public TaskResponse getTask(@PathVariable Long id) {
		return this.taskService.getTask(id);
	}

	@PutMapping("/{id}")
	public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
		return this.taskService.updateTask(id, request);
	}

	@PatchMapping("/{id}/status")
	public TaskResponse updateTaskStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
		return this.taskService.updateTaskStatus(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
		this.taskService.deleteTask(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/suggest")
	public TaskSuggestionResponse suggestTask(@Valid @RequestBody TaskSuggestionRequest request) {
		return this.taskSuggestionService.suggestTask(request.description());
	}

	@PostMapping("/summary")
	public TaskSummaryResponse summarizeTasks() {
		return this.taskSummaryService.summarizeTasks();
	}
}
