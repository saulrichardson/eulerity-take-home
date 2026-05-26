async function requestJson(url, options = {}) {
	const response = await fetch(url, {
		headers: {
			"Content-Type": "application/json",
			...(options.headers || {})
		},
		...options
	});

	if (response.status === 204) {
		return null;
	}

	const payload = await response.json().catch(() => null);
	if (!response.ok) {
		throw payload || {
			message: `Request failed with status ${response.status}`
		};
	}
	return payload;
}

export const api = {
	listTasks(filters) {
		const params = new URLSearchParams();
		if (filters.status) {
			params.set("status", filters.status);
		}
		if (filters.priority) {
			params.set("priority", filters.priority);
		}
		if (filters.sort && filters.sort !== "id") {
			params.set("sort", filters.sort);
		}
		const query = params.toString();
		return requestJson(query ? `/tasks?${query}` : "/tasks");
	},

	createTask(task) {
		return requestJson("/tasks", {
			method: "POST",
			body: JSON.stringify(task)
		});
	},

	updateTask(id, task) {
		return requestJson(`/tasks/${id}`, {
			method: "PUT",
			body: JSON.stringify(task)
		});
	},

	deleteTask(id) {
		return requestJson(`/tasks/${id}`, {
			method: "DELETE"
		});
	},

	updateTaskStatus(id, status) {
		return requestJson(`/tasks/${id}/status`, {
			method: "PATCH",
			body: JSON.stringify({ status })
		});
	},

	suggestTask(description) {
		return requestJson("/tasks/suggest", {
			method: "POST",
			body: JSON.stringify({ description })
		});
	},

	summarizeTasks() {
		return requestJson("/tasks/summary", {
			method: "POST"
		});
	}
};
