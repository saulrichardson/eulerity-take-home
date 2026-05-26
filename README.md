# Eulerity Task Manager

Eulerity Task Manager is a local Java 17 Spring Boot application for managing
personal tasks and using OpenAI to turn plain-language intent into structured
task drafts. It also provides an AI focus-plan endpoint that summarizes the
current task list into a short workload summary and action plan.

The app is designed for a reviewer to understand quickly:

- create, view, edit, complete, delete, filter, and sort tasks
- use a small static dashboard without a REST client
- ask AI for a structured task suggestion from natural language
- ask AI for a task-list summary and focus plan
- run everything locally with H2 in-memory persistence

The Codex build session that drove this project is included at
[docs/codex-session/eulerity-codex-session.md](docs/codex-session/eulerity-codex-session.md).
It shows the prompt, review, debugging, and verification loop used to build the
submission.

## Product Walkthrough

The core object is a task:

```json
{
  "id": 1,
  "title": "Submit quarterly report",
  "description": "Finalize and send the report to finance.",
  "dueDate": "2026-05-29",
  "priority": "HIGH",
  "status": "TODO"
}
```

Tasks have an auto-generated `id`, a required `title`, an optional
`description`, a required `dueDate`, a `priority`, and a `status`.

Priorities are:

- `LOW`
- `MEDIUM`
- `HIGH`

Statuses are:

- `TODO`
- `IN_PROGRESS`
- `DONE`

A typical workflow looks like this:

1. Create a task with a title, optional description, due date, priority, and
   status.
2. View the task list in the dashboard or through `GET /tasks`.
3. Filter the list by status or priority when the list grows.
4. Sort by created order, due date, or priority.
5. Edit the full task when the details change.
6. Mark a task done from the list with the focused status endpoint.
7. Delete completed or unwanted tasks.
8. Use AI suggestions to convert rough language into a task draft.
9. Use AI summaries to turn the stored task list into a practical focus plan.

The dashboard exposes those flows directly. The API provides the same behavior
through explicit JSON endpoints.

## Task Management Behavior

Task creation and update use the same required task fields. A stored task is a
real record, so it always has a due date.

Example create request:

```json
{
  "title": "Prepare client update",
  "description": "Summarize launch progress and blockers.",
  "dueDate": "2026-06-03",
  "priority": "HIGH",
  "status": "TODO"
}
```

Example response:

```json
{
  "id": 1,
  "title": "Prepare client update",
  "description": "Summarize launch progress and blockers.",
  "dueDate": "2026-06-03",
  "priority": "HIGH",
  "status": "TODO"
}
```

The task list can be filtered and sorted:

```text
GET /tasks?status=TODO&priority=HIGH&sort=dueDate
```

Supported sort values are:

- `id`
- `dueDate`
- `priority`

Priority sorting orders tasks as `HIGH`, then `MEDIUM`, then `LOW`.

Completing a task uses a focused endpoint:

```json
{
  "status": "DONE"
}
```

That updates only the status while preserving the title, description, due date,
and priority.

## Static Dashboard

The frontend is a static HTML/CSS/JavaScript dashboard served by Spring Boot at
`/`.

The dashboard supports:

- creating a task
- editing a task in the form
- marking a task done from the list
- deleting a task
- filtering by status and priority
- sorting by created order, due date, or priority
- requesting an AI task suggestion
- creating a task from an AI suggestion when the suggestion includes a due date
- copying a no-date AI suggestion into the form so the user can add a due date
- requesting an AI focus plan for the current task list

The UI is intentionally direct: a form, a task list, an AI suggestion panel, and
an AI summary panel.

## AI Task Suggestions

`POST /tasks/suggest` accepts plain-language task intent, rough notes, email
excerpts, or meeting context. It returns one structured task suggestion.

The suggestion is a draft. It becomes a stored task only when the user creates
one from it.

Example request:

```json
{
  "description": "remind me to submit the quarterly report before Friday"
}
```

Example response, assuming the server-local date is Monday, 2026-05-25:

```json
{
  "title": "Submit quarterly report",
  "description": "Submit the quarterly report before Friday.",
  "dueDate": "2026-05-28",
  "priority": "MEDIUM",
  "status": "TODO"
}
```

The input can be longer than a stored task description because it may contain
raw context. The suggestion endpoint accepts up to `12000` characters of input
and asks the model to distill that context into one bounded task draft. Returned
suggestions use the stored task limits: title up to `255` characters and
description up to `8000` characters.

### Drafts And Due Dates

Stored tasks require `dueDate`. AI suggestions are drafts, so they can return
`dueDate: null` when the user did not state or strongly imply a date.

Example request:

```json
{
  "description": "review the launch notes and capture follow-up items"
}
```

Example response:

```json
{
  "title": "Review launch notes",
  "description": "Review the launch notes and capture follow-up items.",
  "dueDate": null,
  "priority": "MEDIUM",
  "status": "TODO"
}
```

The dashboard shows that suggestion, labels the date as `No due date`, and
disables direct task creation from that card. The user can choose `Edit in Form`,
add the required due date, and save the real task from the normal form.

That preserves the product rule:

```text
AI suggestions are drafts. Persisted tasks are records.
Drafts may omit a due date. Records include a due date.
```

### How AI Date Handling Works

The public API keeps date output simple:

```json
{
  "dueDate": "2026-05-28"
}
```

or:

```json
{
  "dueDate": null
}
```

Internally, the model classifies due-date intent into a small supported date
expression. The backend resolves that expression against the server-local date
captured for the suggestion request. This keeps relative date math in
application code and keeps the public response easy to use.

Supported expression ideas include:

- a specific date, such as `date(2026-06-05)`
- a month/day without a year, such as `month_day(06-05)`
- a day offset, such as `plus_days(1)`
- a week offset, such as `plus_weeks(2)`
- a weekday, such as `next_or_same(FRIDAY)`
- the next calendar-week weekday, such as `next(FRIDAY)`
- the second upcoming weekday, such as `nth_next(FRIDAY,2)`
- a month or week boundary, such as `end_of_week()` or `end_of_month()`
- simple date arithmetic, such as `minus_days(end_of_month(),2)`
- no supported date, represented internally as `none()`

The model returns the internal expression to the application, and the
application returns the resolved `dueDate` to the caller.

### Date Examples

These examples assume the server-local date is Monday, 2026-05-25.

| User phrase | Intended due date |
|---|---:|
| `today` | `2026-05-25` |
| `tomorrow` | `2026-05-26` |
| `June 5` | `2026-06-05` |
| `Friday` | `2026-05-29` |
| `this Friday` | `2026-05-29` |
| `by Friday` | `2026-05-29` |
| `before Friday` | `2026-05-28` |
| `next Friday` | `2026-06-05` |
| `before next Friday` | `2026-06-04` |
| `not this Friday but the one after` | `2026-06-05` |
| `the Friday after next` | `2026-06-12` |
| `end of week` | `2026-05-31` |
| `two days before end of month` | `2026-05-29` |

The app uses a Monday-Sunday calendar week for `next Friday`. End of week means
Sunday.

If the model returns a syntactically valid date that clearly contradicts one of
the documented weekday phrases, the suggestion service retries once with a
specific correction message. For example, if the user says `next Friday` and
the model produces the current week's Friday, the service asks for the next
calendar-week Friday.

If the phrase cannot be represented by the supported date language, the model
is instructed to produce no due date so the user can add one in the form.

### Suggestion Validation

The suggestion response is checked before it is returned:

- title must be present
- title must fit the task title limit
- description must fit the task description limit when provided
- priority must be `LOW`, `MEDIUM`, or `HIGH`
- status must be `TODO`, `IN_PROGRESS`, or `DONE`
- supported date expressions must resolve to the documented date behavior

Invalid or incomplete model output is retried once with a concrete validation
reason. After that, the API returns a structured AI error response.

## AI Task Summaries

`POST /tasks/summary` reads the stored task list and returns a concise workload
summary plus a short action plan.

The endpoint sends selected task facts to the model:

- `id`
- `title`
- `description`
- `dueDate`
- due timing text, such as `due today`, `overdue by 2 days`, or `due in 2 weeks`
- `priority`
- `status`

The backend computes due timing before the model call. The model receives
grounded task context and writes a summary from that data.

Example stored tasks:

```json
[
  {
    "id": 1,
    "title": "Submit quarterly report",
    "description": "Finish revisions and send to finance.",
    "dueDate": "2026-05-23",
    "priority": "HIGH",
    "status": "TODO"
  },
  {
    "id": 2,
    "title": "Review launch checklist",
    "description": "Confirm owner and next steps.",
    "dueDate": "2026-05-27",
    "priority": "MEDIUM",
    "status": "IN_PROGRESS"
  }
]
```

Example summary response:

```json
{
  "summary": "The quarterly report needs the most attention because it is high priority and overdue. The launch checklist is already in progress and due soon.",
  "plan": [
    "Finish and submit the quarterly report first.",
    "Review the launch checklist next and confirm the owner.",
    "Update task statuses after those two items move forward."
  ]
}
```

When the task list is empty, the endpoint returns a local response:

```json
{
  "summary": "You do not have any tasks yet.",
  "plan": [
    "Create a task to start planning your work."
  ]
}
```

### Summary Selection And Budgeting

The summary endpoint ranks tasks before sending context to the model. It favors:

- open tasks
- in-progress tasks
- high-priority tasks
- overdue tasks
- tasks due soon

The default candidate cap is `30` tasks.

The prompt builder preserves complete task records first. When task descriptions
are long, it reduces context in record-aware steps:

1. Keep task identity and core metadata.
2. Shorten descriptions at paragraph, sentence, or word boundaries.
3. Mark shortened descriptions with `[description shortened]`.
4. Drop descriptions before dropping task metadata.
5. Omit lower-relevance task records as whole records when needed.

This keeps prompt reduction understandable and avoids arbitrary mid-record
cuts.

### Token Preflight

Both AI endpoints perform provider-backed token preflight before calling the
model.

The app builds the final OpenAI request shape, including instructions, input,
model, and structured-output format. It then counts input tokens through the
OpenAI Responses input-token count API. The model call proceeds when counted
input tokens plus configured output tokens plus the request overhead reserve
fit inside the configured model context budget.

The default model context configuration is:

```text
400000 context window tokens * 0.90 usage ratio = 360000 token hard ceiling
```

The summary endpoint also has a smaller application prompt budget of `6000`
counted input tokens. That keeps summaries focused even though the configured
model context window is much larger.

Automated tests mock the OpenAI client and token counter boundaries, so the
test suite exercises application behavior without live provider calls.

## API Reference

### Task Endpoints

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/tasks` | Create a task |
| `GET` | `/tasks` | List tasks, with optional filters and sorting |
| `GET` | `/tasks/{id}` | Get one task |
| `PUT` | `/tasks/{id}` | Replace one task |
| `PATCH` | `/tasks/{id}/status` | Update task status |
| `DELETE` | `/tasks/{id}` | Delete one task |

Create:

```sh
curl -i -X POST http://localhost:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Submit quarterly report",
    "description": "Finalize and send the report",
    "dueDate": "2026-05-29",
    "priority": "MEDIUM",
    "status": "TODO"
  }'
```

List:

```sh
curl -i http://localhost:8080/tasks
```

List with controls:

```sh
curl -i 'http://localhost:8080/tasks?status=TODO&priority=HIGH&sort=dueDate'
```

Get one:

```sh
curl -i http://localhost:8080/tasks/1
```

Update:

```sh
curl -i -X PUT http://localhost:8080/tasks/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Submit final report",
    "description": "Include updated financials",
    "dueDate": "2026-06-01",
    "priority": "HIGH",
    "status": "IN_PROGRESS"
  }'
```

Mark done:

```sh
curl -i -X PATCH http://localhost:8080/tasks/1/status \
  -H 'Content-Type: application/json' \
  -d '{ "status": "DONE" }'
```

Delete:

```sh
curl -i -X DELETE http://localhost:8080/tasks/1
```

### AI Endpoints

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/tasks/suggest` | Convert plain-language task intent into one structured task draft |
| `POST` | `/tasks/summary` | Summarize the stored task list into a workload summary and plan |

Suggestion:

```sh
curl -i -X POST http://localhost:8080/tasks/suggest \
  -H 'Content-Type: application/json' \
  -d '{
    "description": "remind me to submit the quarterly report before Friday"
  }'
```

Summary:

```sh
curl -i -X POST http://localhost:8080/tasks/summary
```

## Error Responses

Errors use one JSON shape:

```json
{
  "timestamp": "2026-05-25T04:00:00Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/tasks",
  "fieldErrors": [
    {
      "field": "title",
      "message": "title is required"
    }
  ]
}
```

Common error codes:

| Code | Meaning |
|---|---|
| `VALIDATION_FAILED` | A request body field failed validation |
| `BAD_REQUEST` | The request body, parameter, enum, date, or sort value was invalid |
| `TASK_NOT_FOUND` | The requested task id was not present |
| `AI_CONFIGURATION_MISSING` | An AI endpoint needs `OPENAI_API_KEY` for a model-backed response |
| `AI_TASK_FAILED` | Token preflight or provider-backed AI work failed in a controlled way |
| `AI_TASK_OUTPUT_INVALID` | The model returned unusable structured output after validation and retry |

Example missing AI configuration response:

```json
{
  "timestamp": "2026-05-25T04:43:51.216688Z",
  "status": 503,
  "error": "AI_CONFIGURATION_MISSING",
  "message": "AI configuration is missing. Set OPENAI_API_KEY to enable AI task features.",
  "path": "/tasks/suggest",
  "fieldErrors": []
}
```

## Project Shape

The backend is a Spring Boot application in `com.eulerity.taskmanager`.

Important areas:

- `controller`: REST routes and request entry points
- `service`: task workflow rules, AI suggestion validation, summary selection
- `repository`: JPA access to H2-backed task records
- `model`: task entity, enums, and shared field limits
- `dto`: request and response shapes
- `ai`: OpenAI boundary, request construction, token counting, and date-rule handling
- `config`: OpenAI, summary, and clock configuration
- `exception`: structured API error mapping
- `src/main/resources/static`: static dashboard
- `src/test/java`: unit, integration, AI-boundary, and static UI tests

The runtime flow is:

```text
HTTP or UI action
  -> controller DTO validation
  -> service behavior
  -> repository or AI client boundary
  -> structured response or structured error
```

H2 stores tasks in memory for the active application process. Restarting the app
starts with a new task database.

## Local Run And Verification

### Requirements

- Java 17
- Maven Wrapper from this repository

The project is compiled with Java 17 release settings.

### Run The App

```sh
./mvnw spring-boot:run
```

Then open:

- Dashboard: `http://localhost:8080/`
- API base: `http://localhost:8080`

### Run Tests

```sh
./mvnw test
```

Current test coverage includes:

- Spring context startup
- task service happy paths
- task CRUD integration paths
- validation and structured errors
- filtering and sorting
- AI suggestion controller behavior with mocked AI boundaries
- AI summary controller behavior with mocked AI boundaries
- AI suggestion retry, validation, token preflight, and date semantics
- AI summary selection, prompt budgeting, and response validation
- OpenAI request-shape construction
- OpenAI client-provider configuration
- static dashboard source checks
- `GET /` static UI serving

Automated tests use `src/test/resources/application.properties`, which clears
local `.env` imports and keeps test runs deterministic.

### OpenAI Configuration

Task CRUD and the dashboard run with or without OpenAI credentials. Model-backed
AI suggestions and task summaries use `OPENAI_API_KEY`.

Configuration can come from environment variables or an ignored local `.env`
file copied from `.env.example`:

```sh
OPENAI_API_KEY=
OPENAI_MODEL=gpt-5.4-nano
OPENAI_MODEL_CONTEXT_WINDOW_TOKENS=400000
OPENAI_CONTEXT_USAGE_RATIO=0.90
OPENAI_SUGGESTION_MAX_OUTPUT_TOKENS=2048
OPENAI_SUMMARY_MAX_OUTPUT_TOKENS=1024
OPENAI_REQUEST_OVERHEAD_RESERVE_TOKENS=1000

AI_SUMMARY_INPUT_TOKEN_BUDGET=6000
AI_SUMMARY_CANDIDATE_TASK_CAP=30
AI_SUMMARY_PER_TASK_DESCRIPTION_PROMPT_BUDGET=1200
AI_SUMMARY_MAX_SUMMARY_LENGTH=1200
AI_SUMMARY_MAX_PLAN_ITEMS=5
AI_SUMMARY_MAX_PLAN_ITEM_LENGTH=300
```

Keep real API keys in the local shell environment or ignored `.env` file.

### Manual AI Smoke Checks

Use these checks when `OPENAI_API_KEY` is configured locally:

1. Submit `remind me to submit the quarterly report before Friday` to
   `/tasks/suggest` and confirm a structured task draft is returned.
2. Submit a prompt with no due date, such as `review the launch notes`, and
   confirm the response can use `dueDate: null`.
3. Submit `next Friday`, `before next Friday`, `end of week`, and
   `two days before end of month` examples and compare the dates to the
   server-local date semantics above.
4. Create a few tasks and call `/tasks/summary`; confirm the response contains
   a concise `summary` and a bounded `plan` array.
5. In the dashboard, copy a no-date suggestion into the task form, add a due
   date, and create the task.
