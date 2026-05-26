import { api } from "./api.js";
import {
	dom,
	errorMessage,
	fillTaskForm,
	getFilters,
	getTaskFormPayload,
	renderSuggestion,
	renderSummary,
	renderTasks,
	resetTaskForm,
	setEditorMode,
	showMessage
} from "./ui.js";

const state = {
	editingTaskId: null,
	latestSuggestion: null
};

async function loadTasks() {
	try {
		const filters = getFilters();
		const tasks = await api.listTasks(filters);
		renderTasks(tasks, {
			hasActiveFilters: Boolean(filters.status || filters.priority),
			onEdit: startEditingTask,
			onMarkDone: markTaskDone,
			onDelete: deleteTask
		});
		dom.statusLine.textContent = "Ready";
	}
	catch (error) {
		dom.statusLine.textContent = "Task load failed";
		showMessage(dom.taskList, errorMessage(error), "error");
	}
}

async function saveTask(event) {
	event.preventDefault();
	const payload = getTaskFormPayload();

	try {
		if (state.editingTaskId) {
			await api.updateTask(state.editingTaskId, payload);
			showMessage(dom.formMessage, "Task updated.");
		}
		else {
			await api.createTask(payload);
			showMessage(dom.formMessage, "Task created.");
		}
		state.editingTaskId = null;
		resetTaskForm();
		setEditorMode(false);
		await loadTasks();
	}
	catch (error) {
		showMessage(dom.formMessage, errorMessage(error), "error");
	}
}

function startEditingTask(task) {
	state.editingTaskId = task.id;
	state.latestSuggestion = null;
	dom.suggestionOutput.innerHTML = "";
	fillTaskForm(task);
	setEditorMode(true);
	showMessage(dom.formMessage, "Editing task.");
	dom.title.focus();
}

function cancelEditingOrReset() {
	state.editingTaskId = null;
	resetTaskForm();
	setEditorMode(false);
	showMessage(dom.formMessage, "");
}

async function deleteTask(id) {
	try {
		await api.deleteTask(id);
		if (state.editingTaskId === id) {
			state.editingTaskId = null;
			resetTaskForm();
			setEditorMode(false);
		}
		showMessage(dom.formMessage, "Task deleted.");
		await loadTasks();
	}
	catch (error) {
		showMessage(dom.formMessage, errorMessage(error), "error");
	}
}

async function markTaskDone(id) {
	try {
		await api.updateTaskStatus(id, "DONE");
		await loadTasks();
	}
	catch (error) {
		showMessage(dom.formMessage, errorMessage(error), "error");
	}
}

async function suggestTask(event) {
	event.preventDefault();
	const description = dom.suggestDescription.value.trim();
	dom.suggestButton.disabled = true;
	dom.suggestButton.textContent = "Suggesting...";

	try {
		state.latestSuggestion = await api.suggestTask(description);
		renderSuggestion(state.latestSuggestion, {
			onCreate: createTaskFromSuggestion,
			onEdit: fillFormFromSuggestion
		});
	}
	catch (error) {
		state.latestSuggestion = null;
		showMessage(dom.suggestionOutput, errorMessage(error), "error");
	}
	finally {
		dom.suggestButton.disabled = false;
		dom.suggestButton.textContent = "Suggest Task";
	}
}

async function createTaskFromSuggestion(button) {
	if (!state.latestSuggestion || !state.latestSuggestion.dueDate) {
		return;
	}

	button.disabled = true;
	button.textContent = "Creating...";

	try {
		await api.createTask({
			title: state.latestSuggestion.title,
			description: state.latestSuggestion.description || null,
			dueDate: state.latestSuggestion.dueDate,
			priority: state.latestSuggestion.priority,
			status: state.latestSuggestion.status
		});
		state.latestSuggestion = null;
		dom.suggestionOutput.innerHTML = "";
		showMessage(dom.suggestionOutput, "Task created from suggestion.");
		await loadTasks();
	}
	catch (error) {
		showMessage(dom.suggestionOutput, errorMessage(error), "error");
		button.disabled = false;
		button.textContent = "Create Task";
	}
}

function fillFormFromSuggestion() {
	if (!state.latestSuggestion) {
		return;
	}
	state.editingTaskId = null;
	fillTaskForm({
		title: state.latestSuggestion.title || "",
		description: state.latestSuggestion.description || "",
		dueDate: state.latestSuggestion.dueDate || "",
		priority: state.latestSuggestion.priority || "MEDIUM",
		status: state.latestSuggestion.status || "TODO"
	});
	setEditorMode(false);
	showMessage(dom.formMessage, "Suggestion copied into the task form.");
	dom.title.focus();
}

async function summarizeTasks() {
	dom.summaryButton.disabled = true;
	dom.summaryButton.textContent = "Summarizing...";

	try {
		const response = await api.summarizeTasks();
		renderSummary(response);
	}
	catch (error) {
		showMessage(dom.summaryOutput, errorMessage(error), "error");
	}
	finally {
		dom.summaryButton.disabled = false;
		dom.summaryButton.textContent = "Summarize Tasks";
	}
}

function clearSuggestion() {
	state.latestSuggestion = null;
	dom.suggestionOutput.innerHTML = "";
}

function wireEvents() {
	dom.taskForm.addEventListener("submit", saveTask);
	dom.resetForm.addEventListener("click", cancelEditingOrReset);
	dom.suggestForm.addEventListener("submit", suggestTask);
	dom.clearSuggestion.addEventListener("click", clearSuggestion);
	dom.summaryButton.addEventListener("click", summarizeTasks);
	dom.statusFilter.addEventListener("change", loadTasks);
	dom.priorityFilter.addEventListener("change", loadTasks);
	dom.taskSort.addEventListener("change", loadTasks);
}

wireEvents();
loadTasks();
