export const dom = {
	taskForm: document.querySelector("#taskForm"),
	suggestForm: document.querySelector("#suggestForm"),
	taskList: document.querySelector("#taskList"),
	taskCount: document.querySelector("#taskCount"),
	statusLine: document.querySelector("#statusLine"),
	formMessage: document.querySelector("#formMessage"),
	suggestionOutput: document.querySelector("#suggestionOutput"),
	summaryOutput: document.querySelector("#summaryOutput"),
	summaryButton: document.querySelector("#summaryButton"),
	statusFilter: document.querySelector("#statusFilter"),
	priorityFilter: document.querySelector("#priorityFilter"),
	taskSort: document.querySelector("#taskSort"),
	title: document.querySelector("#title"),
	description: document.querySelector("#description"),
	dueDate: document.querySelector("#dueDate"),
	priority: document.querySelector("#priority"),
	status: document.querySelector("#status"),
	createTitle: document.querySelector("#createTitle"),
	taskSubmitButton: document.querySelector("#taskSubmitButton"),
	resetForm: document.querySelector("#resetForm"),
	suggestDescription: document.querySelector("#suggestDescription"),
	suggestButton: document.querySelector("#suggestButton"),
	clearSuggestion: document.querySelector("#clearSuggestion")
};

export function getFilters() {
	return {
		status: dom.statusFilter.value,
		priority: dom.priorityFilter.value,
		sort: dom.taskSort.value
	};
}

export function getTaskFormPayload() {
	return {
		title: dom.title.value.trim(),
		description: dom.description.value.trim() || null,
		dueDate: dom.dueDate.value,
		priority: dom.priority.value,
		status: dom.status.value
	};
}

export function resetTaskForm() {
	dom.taskForm.reset();
	dom.priority.value = "MEDIUM";
	dom.status.value = "TODO";
}

export function fillTaskForm(task) {
	dom.title.value = task.title || "";
	dom.description.value = task.description || "";
	dom.dueDate.value = task.dueDate || "";
	dom.priority.value = task.priority || "MEDIUM";
	dom.status.value = task.status || "TODO";
}

export function setEditorMode(isEditing) {
	dom.createTitle.textContent = isEditing ? "Edit Task" : "New Task";
	dom.taskSubmitButton.textContent = isEditing ? "Save Changes" : "Create Task";
	dom.resetForm.textContent = isEditing ? "Cancel Edit" : "Reset";
}

export function showMessage(container, message, variant = "info") {
	container.innerHTML = "";
	if (!message) {
		return;
	}
	const notice = document.createElement("div");
	notice.className = `notice ${variant}`;
	notice.textContent = message;
	container.appendChild(notice);
}

export function errorMessage(error) {
	if (error && Array.isArray(error.fieldErrors) && error.fieldErrors.length > 0) {
		return error.fieldErrors.map((fieldError) => `${fieldError.field}: ${fieldError.message}`).join("\n");
	}
	return error && error.message ? error.message : "Request failed";
}

export function renderTasks(tasks, options) {
	dom.taskCount.textContent = `${tasks.length} ${tasks.length === 1 ? "task" : "tasks"}`;
	dom.taskList.innerHTML = "";

	if (tasks.length === 0) {
		const empty = document.createElement("div");
		empty.className = "empty-state";
		empty.textContent = options.hasActiveFilters ? "No tasks match these filters." : "No tasks yet.";
		dom.taskList.appendChild(empty);
		return;
	}

	for (const task of tasks) {
		dom.taskList.appendChild(taskItem(task, options));
	}
}

function taskItem(task, options) {
	const item = document.createElement("article");
	item.className = "task-item";

	const body = document.createElement("div");
	body.className = "task-body";

	const titleRow = document.createElement("div");
	titleRow.className = "task-title-row";
	const title = document.createElement("h3");
	title.textContent = task.title;
	titleRow.appendChild(title);

	const meta = document.createElement("div");
	meta.className = "task-meta";
	meta.append(
		badge(task.priority, `priority-${task.priority.toLowerCase()}`),
		badge(task.status, `status-${task.status.toLowerCase().replace("_", "-")}`)
	);
	titleRow.appendChild(meta);
	body.appendChild(titleRow);

	const dueDate = document.createElement("p");
	dueDate.className = "task-due-date";
	dueDate.textContent = `Due ${task.dueDate}`;
	body.appendChild(dueDate);

	if (task.description) {
		const description = document.createElement("p");
		description.className = "task-description";
		description.textContent = task.description;
		body.appendChild(description);
	}

	const actions = document.createElement("div");
	actions.className = "task-actions";
	const editButton = document.createElement("button");
	editButton.className = "secondary";
	editButton.type = "button";
	editButton.textContent = "Edit";
	editButton.addEventListener("click", () => options.onEdit(task));
	actions.appendChild(editButton);

	if (task.status !== "DONE") {
		const doneButton = document.createElement("button");
		doneButton.className = "secondary";
		doneButton.type = "button";
		doneButton.textContent = "Mark Done";
		doneButton.addEventListener("click", () => options.onMarkDone(task.id));
		actions.appendChild(doneButton);
	}

	const deleteButton = document.createElement("button");
	deleteButton.className = "danger";
	deleteButton.type = "button";
	deleteButton.textContent = "Delete";
	deleteButton.addEventListener("click", () => options.onDelete(task.id));
	actions.appendChild(deleteButton);

	item.append(body, actions);
	return item;
}

export function renderSuggestion(suggestion, handlers) {
	dom.suggestionOutput.innerHTML = "";
	const wrapper = document.createElement("div");
	wrapper.className = "suggestion-card";
	const hasDueDate = Boolean(suggestion.dueDate);

	for (const [label, value] of [
		["Title", suggestion.title],
		["Description", suggestion.description || ""],
		["Due Date", suggestion.dueDate || "No due date"],
		["Priority", suggestion.priority],
		["Status", suggestion.status]
	]) {
		const row = document.createElement("div");
		row.className = "detail-row";
		const labelEl = document.createElement("span");
		labelEl.textContent = label;
		const valueEl = document.createElement("strong");
		valueEl.textContent = value;
		row.append(labelEl, valueEl);
		wrapper.appendChild(row);
	}

	if (!hasDueDate) {
		const note = document.createElement("div");
		note.className = "notice";
		note.textContent = "Add a due date in the task form before creating this task.";
		wrapper.appendChild(note);
	}

	const actions = document.createElement("div");
	actions.className = "actions";
	const createButton = document.createElement("button");
	createButton.type = "button";
	createButton.textContent = "Create Task";
	createButton.disabled = !hasDueDate;
	if (hasDueDate) {
		createButton.addEventListener("click", () => handlers.onCreate(createButton));
	}

	const editButton = document.createElement("button");
	editButton.className = "secondary";
	editButton.type = "button";
	editButton.textContent = "Edit in Form";
	editButton.addEventListener("click", handlers.onEdit);
	actions.append(createButton, editButton);
	wrapper.appendChild(actions);
	dom.suggestionOutput.appendChild(wrapper);
}

export function renderSummary(response) {
	dom.summaryOutput.innerHTML = "";
	const wrapper = document.createElement("div");
	wrapper.className = "summary-card";

	const summary = document.createElement("p");
	summary.textContent = response.summary;
	wrapper.appendChild(summary);

	const plan = document.createElement("ol");
	for (const item of response.plan || []) {
		const step = document.createElement("li");
		step.textContent = item;
		plan.appendChild(step);
	}
	wrapper.appendChild(plan);
	dom.summaryOutput.appendChild(wrapper);
}

function badge(text, className) {
	const span = document.createElement("span");
	span.className = `badge ${className}`;
	span.textContent = text;
	return span;
}
