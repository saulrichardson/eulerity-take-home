package com.eulerity.taskmanager.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eulerity.taskmanager.repository.TaskRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TaskCrudIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TaskRepository taskRepository;

	@BeforeEach
	void cleanDatabase() {
		this.taskRepository.deleteAll();
	}

	@Test
	void crudEndpointsWorkEndToEnd() throws Exception {
		MvcResult createResult = this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Submit quarterly report",
						  "description": "Finish and submit the report",
						  "dueDate": "2026-05-29",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						"""))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.title").value("Submit quarterly report"))
			.andReturn();
		Number id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

		this.mockMvc.perform(get("/tasks"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(id.intValue()));

		this.mockMvc.perform(get("/tasks/{id}", id.longValue()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("Finish and submit the report"));

		this.mockMvc.perform(put("/tasks/{id}", id.longValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Submit final report",
						  "description": "Include updated financials",
						  "dueDate": "2026-06-01",
						  "priority": "MEDIUM",
						  "status": "IN_PROGRESS"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Submit final report"))
			.andExpect(jsonPath("$.priority").value("MEDIUM"))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		this.mockMvc.perform(patch("/tasks/{id}/status", id.longValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "status": "DONE"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Submit final report"))
			.andExpect(jsonPath("$.description").value("Include updated financials"))
			.andExpect(jsonPath("$.dueDate").value("2026-06-01"))
			.andExpect(jsonPath("$.priority").value("MEDIUM"))
			.andExpect(jsonPath("$.status").value("DONE"));

		this.mockMvc.perform(delete("/tasks/{id}", id.longValue())).andExpect(status().isNoContent());

		this.mockMvc.perform(get("/tasks/{id}", id.longValue()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("TASK_NOT_FOUND"));
	}

	@Test
	void validationErrorsAreStructured() throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "",
						  "description": "Missing required fields"
						}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void createWithoutDueDateReturnsStructuredValidationError() throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Missing date",
						  "description": "Task records require a due date",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate is required"));
	}

	@Test
	void updateWithoutDueDateReturnsStructuredValidationError() throws Exception {
		MvcResult createResult = this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Create before invalid update",
						  "description": "Original task",
						  "dueDate": "2026-05-29",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		Number id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

		this.mockMvc.perform(put("/tasks/{id}", id.longValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Invalid update",
						  "description": "Missing due date on full replacement",
						  "priority": "MEDIUM",
						  "status": "IN_PROGRESS"
						}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate is required"));
	}

	@Test
	void statusUpdateValidationErrorsAreStructured() throws Exception {
		MvcResult createResult = this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Validate status update",
						  "description": "Status update validation",
						  "dueDate": "2026-05-29",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		Number id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

		this.mockMvc.perform(patch("/tasks/{id}/status", id.longValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("status is required"));
	}

	@Test
	void listTasksFiltersByStatusAndPriority() throws Exception {
		createTask("Low todo", "2026-06-03", "LOW", "TODO");
		createTask("High done", "2026-06-01", "HIGH", "DONE");
		createTask("High todo", "2026-06-02", "HIGH", "TODO");

		this.mockMvc.perform(get("/tasks").param("status", "TODO"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].status").value("TODO"))
			.andExpect(jsonPath("$[1].status").value("TODO"));

		this.mockMvc.perform(get("/tasks").param("priority", "HIGH"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].priority").value("HIGH"))
			.andExpect(jsonPath("$[1].priority").value("HIGH"));

		this.mockMvc.perform(get("/tasks").param("status", "TODO").param("priority", "HIGH"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].title").value("High todo"));
	}

	@Test
	void listTasksSortsByDueDate() throws Exception {
		createTask("Third due", "2026-06-03", "LOW", "TODO");
		createTask("First due", "2026-06-01", "HIGH", "TODO");
		createTask("Second due", "2026-06-02", "MEDIUM", "TODO");

		this.mockMvc.perform(get("/tasks").param("sort", "dueDate"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("First due"))
			.andExpect(jsonPath("$[1].title").value("Second due"))
			.andExpect(jsonPath("$[2].title").value("Third due"));
	}

	@Test
	void listTasksSortsByPriority() throws Exception {
		createTask("Low priority", "2026-06-01", "LOW", "TODO");
		createTask("High priority", "2026-06-03", "HIGH", "TODO");
		createTask("Medium priority", "2026-06-02", "MEDIUM", "TODO");

		this.mockMvc.perform(get("/tasks").param("sort", "priority"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("High priority"))
			.andExpect(jsonPath("$[1].title").value("Medium priority"))
			.andExpect(jsonPath("$[2].title").value("Low priority"));
	}

	@Test
	void overlongTitleReturnsStructuredValidationError() throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "%s",
						  "description": "Valid description",
						  "dueDate": "2026-05-29",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						""".formatted("a".repeat(256))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("title must be 255 characters or fewer"));
	}

	@Test
	void overlongDescriptionReturnsStructuredValidationError() throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Valid title",
						  "description": "%s",
						  "dueDate": "2026-05-29",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						""".formatted("a".repeat(8001))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("description"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("description must be 8000 characters or fewer"));
	}

	@Test
	void createAndUpdateAcceptMaximumLengthTaskText() throws Exception {
		String initialDescription = "a".repeat(8000);
		MvcResult createResult = this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "%s",
						  "description": "%s",
						  "dueDate": "2026-05-29",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						""".formatted("t".repeat(255), initialDescription)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("t".repeat(255)))
			.andExpect(jsonPath("$.description").value(initialDescription))
			.andReturn();
		Number id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
		String updatedDescription = "b".repeat(8000);

		this.mockMvc.perform(put("/tasks/{id}", id.longValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "%s",
						  "description": "%s",
						  "dueDate": "2026-06-01",
						  "priority": "MEDIUM",
						  "status": "IN_PROGRESS"
						}
						""".formatted("u".repeat(255), updatedDescription)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("u".repeat(255)))
			.andExpect(jsonPath("$.description").value(updatedDescription));
	}

	@Test
	void invalidPriorityReturnsStructuredBadRequest() throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Valid title",
						  "description": "Valid description",
						  "dueDate": "2026-05-29",
						  "priority": "URGENT",
						  "status": "TODO"
						}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.message").value("Request body or parameters are invalid"));
	}

	@Test
	void malformedDueDateReturnsStructuredBadRequest() throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "Valid title",
						  "description": "Valid description",
						  "dueDate": "next Friday",
						  "priority": "HIGH",
						  "status": "TODO"
						}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.message").value("Request body or parameters are invalid"));
	}

	@Test
	void missingTaskReturnsStructuredNotFound() throws Exception {
		this.mockMvc.perform(get("/tasks/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.error").value("TASK_NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("Task 999 was not found"));
	}

	private void createTask(String title, String dueDate, String priority, String taskStatus) throws Exception {
		this.mockMvc.perform(post("/tasks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "%s",
						  "description": "Created for list workflow test",
						  "dueDate": "%s",
						  "priority": "%s",
						  "status": "%s"
						}
						""".formatted(title, dueDate, priority, taskStatus)))
			.andExpect(status().isCreated());
	}
}
