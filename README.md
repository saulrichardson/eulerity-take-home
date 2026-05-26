# Eulerity Task Manager

A small Java 17 Spring Boot personal task manager with H2 in-memory persistence,
explicit REST endpoints, a minimal static UI, and an optional OpenAI-backed task
suggestion and task-summary endpoint.

The application runs locally without external services. OpenAI configuration is
only required when calling AI endpoints that need model output.

## Requirements

- Java 17 or newer
- No local Maven install required; use the included Maven Wrapper

## Run Locally

Start the application:

```sh
./mvnw spring-boot:run
```

Then open:

- UI: `http://localhost:8080/`
- API base: `http://localhost:8080`

H2 uses an in-memory database, so task data resets when the application stops.
Task list filtering/sorting and summary candidate selection are handled in
memory. That is acceptable for this local H2 take-home scope, but it is not a
production-scale task-list architecture.

## Run Tests

```sh
./mvnw test
```

The test suite covers the Spring context, task service methods, CRUD endpoints,
AI suggestion service retry behavior, AI task summary behavior, and AI endpoints
with the OpenAI client mocked. Tests do not call the real OpenAI API. Test
configuration is loaded from `src/test/resources/application.properties`, so
`./mvnw test` does not import or depend on a developer's local `.env` file.

## Configuration

OpenAI configuration is read from environment variables or an optional local
`.env` file:

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

`OPENAI_MODEL` defaults to `gpt-5.4-nano` when unset.
`OPENAI_MODEL_CONTEXT_WINDOW_TOKENS` defaults to `400000`, and
`OPENAI_CONTEXT_USAGE_RATIO` defaults to `0.90`, so the default hard context
ceiling is `360000` tokens before output and overhead reserves. The context
usage ratio is validated and cannot be configured above `0.90`.

The application starts and task CRUD works when `OPENAI_API_KEY` is missing.
Calling `/tasks/suggest` without a key returns a structured `503` error. Calling
`/tasks/summary` without a key returns the same structured configuration error
when there are tasks to summarize. If there are no tasks, `/tasks/summary`
returns a local empty-state response without calling OpenAI.

API keys and `.env` files are local-only and are not committed. Use
`.env.example` as the template for local configuration.

## Task API

Task fields:

- `id`: auto-generated number
- `title`: required string, maximum 255 characters
- `description`: optional string, maximum 8000 characters
- `dueDate`: required date in `YYYY-MM-DD` format
- `priority`: `LOW`, `MEDIUM`, or `HIGH`
- `status`: `TODO`, `IN_PROGRESS`, or `DONE`

### Create A Task

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

Returns `201 Created` with the created task.

### List Tasks

```sh
curl -i http://localhost:8080/tasks
```

Returns `200 OK` with an array of tasks.

Optional query parameters:

- `status`: `TODO`, `IN_PROGRESS`, or `DONE`
- `priority`: `LOW`, `MEDIUM`, or `HIGH`
- `sort`: `id`, `dueDate`, or `priority`

Examples:

```sh
curl -i 'http://localhost:8080/tasks?status=TODO&priority=HIGH&sort=dueDate'
curl -i 'http://localhost:8080/tasks?sort=priority'
```

### Get A Task

```sh
curl -i http://localhost:8080/tasks/1
```

Returns `200 OK` with the task, or structured `404` if it does not exist.

### Update A Task

`PUT /tasks/{id}` is a full replacement. Required fields are the same as create.

```sh
curl -i -X PUT http://localhost:8080/tasks/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Submit quarterly report",
    "description": "Sent to finance",
    "dueDate": "2026-05-29",
    "priority": "HIGH",
    "status": "DONE"
  }'
```

Returns `200 OK` with the updated task, or structured `404` if it does not exist.

### Update Task Status

`PATCH /tasks/{id}/status` updates only the task status. Use this for focused
completion flows such as marking a task done.

```sh
curl -i -X PATCH http://localhost:8080/tasks/1/status \
  -H 'Content-Type: application/json' \
  -d '{
    "status": "DONE"
  }'
```

Returns `200 OK` with the updated task, or structured `404` if it does not exist.

### Delete A Task

```sh
curl -i -X DELETE http://localhost:8080/tasks/1
```

Returns `204 No Content`, or structured `404` if it does not exist.

## AI Suggestion API

`POST /tasks/suggest` accepts plain-language task intent, rough notes, email
excerpts, or meeting context and returns one structured task suggestion. The
endpoint does not persist anything.

The request `description` is required and capped at 12000 characters. This is
larger than the stored task description limit because the AI request is raw
source material; the model should distill it into one valid task. Returned
suggestions are checked against the same task field limits used by task
creation: title is capped at 255 characters and description is capped at 8000
characters. AI suggestions are drafts, so `dueDate` may be `null` when the
input does not state or strongly imply a due date. Persisted task records still
require `dueDate`.

AI suggestions use an internal date-expression resolution step. The model
identifies one whitelisted due-date expression such as `next_or_same(FRIDAY)`,
`month_day(06-05)`, `minus_days(end_of_month(),2)`,
`next(FRIDAY)`, `minus_days(next(FRIDAY),1)`, or `none()`, and the backend
resolves that expression against the server-local date captured for the
suggestion request. The public API still returns only `dueDate` as `yyyy-MM-dd`
or `null`; it does not expose the internal expression. If the model cannot
represent a date phrase with the supported expression language, it should
return `none()` so the user can add the date manually. Explicit past dates are
allowed and become overdue task drafts rather than validation failures.

Weekday language follows a small app-owned convention:

- `by Friday`, `on Friday`, `Friday`, and `this Friday` resolve to the named
  upcoming-or-same weekday.
- `before Friday` and `before this Friday` resolve to the day before that
  upcoming-or-same weekday.
- `next Friday` means Friday in the next Monday-Sunday calendar week.
- `before next Friday` resolves to the day before that next-week Friday.
- `not this Friday but the one after` resolves to the next-week Friday.
- `the Friday after next` resolves to the second upcoming Friday.

Request:

```json
{
  "description": "remind me to submit the quarterly report before Friday"
}
```

Success response:

```json
{
  "title": "Submit quarterly report",
  "description": "Submit the quarterly report before Friday.",
  "dueDate": "2026-05-28",
  "priority": "MEDIUM",
  "status": "TODO"
}
```

The example above assumes the server-local date is Monday, 2026-05-25.

If no due date is stated or strongly implied, the endpoint may return:

```json
{
  "title": "Review launch notes",
  "description": "Review the launch notes and capture follow-up items.",
  "dueDate": null,
  "priority": "MEDIUM",
  "status": "TODO"
}
```

The static UI displays no-date suggestions as drafts. It disables direct
creation from that suggestion and guides the user to edit the suggestion in the
task form, add the required due date, and then save a real task.

When `OPENAI_API_KEY` is missing:

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

The OpenAI integration uses the OpenAI Responses API through a mockable
`TaskAiClient` boundary. OpenAI client setup and missing-key checks are
centralized in `OpenAiClientProvider`, which is shared by model calls and token
counting. Before calling the model, `/tasks/suggest` uses a mockable
`TaskAiTokenCounter` boundary backed by the OpenAI Responses input-token count
API to preflight the final request shape. The preflight counts the same
instructions, input text, model, and structured-output configuration that the
model call will use. If counted input tokens plus the configured suggestion
output cap and request overhead reserve exceed the configured model hard context
ceiling, the endpoint returns a structured `AI_TASK_FAILED` error instead of
calling the model. The server-local date used for relative due-date
interpretation is captured once per suggestion workflow and reused for both
token preflight, the model request, and internal date-rule resolution. Invalid
or incomplete model output, including unsupported due-date rules, is retried
once with a specific validation failure reason, then returned as
`AI_TASK_OUTPUT_INVALID` without fallback task defaults.

## AI Budgeting Policy

The application keeps four limits separate:

- persisted task limits define what can be stored
- prompt budgets define what can be sent to the model
- OpenAI output-token caps bound model generation
- deterministic validation defines what API responses can trust

The model-aware hard ceiling is:

```text
configured model context window tokens * configured context usage ratio
```

The default model context window is `400000` tokens and the default usage ratio
is `0.90`, producing a `360000` token hard ceiling. The app still uses a much
smaller default summary input budget of `6000` counted input tokens to keep
summary prompts readable and bounded.

Both AI endpoints perform provider-backed token preflight before model calls.
The production token counter calls the OpenAI Responses input-token count API.
Automated tests mock this boundary and never call OpenAI. The budget policy
assumes `OPENAI_MODEL_CONTEXT_WINDOW_TOKENS` accurately describes the configured
model; if that value is wrong for a custom `OPENAI_MODEL`, the application can
only enforce the configured ceiling, not the provider's real hidden limit.

Summary prompt construction never solves budget pressure by cutting the final
serialized prompt at an arbitrary boundary. It ranks tasks by relevance,
constructs coherent candidate task records, counts the final request shape,
then reduces context only through deterministic record-preserving steps:
shorten descriptions at paragraph, sentence, or word boundaries, mark shortened
descriptions with `[description shortened]`, drop descriptions before dropping
metadata, and omit lower-relevance task records as whole records when needed.

## AI Task Summary API

`POST /tasks/summary` loads the current stored tasks and returns a natural-
language workload summary with a short action plan. It does not persist the
summary or plan.

The endpoint sends only task data to OpenAI:

- `id`
- `title`
- `description`
- `dueDate`
- precomputed due timing context, such as `due today`, `overdue by 2 days`, or
  `due in 2 weeks`
- `priority`
- `status`

The default candidate cap is 30 tasks. Under the configured summary input budget,
the service deterministically selects the most relevant complete task records,
prioritizing open, in-progress, high-priority, overdue, and soon-due work. The
summary preflight counts the exact final request shape through the OpenAI
input-token count API before the summary model call. If long descriptions do
not fit, descriptions are shortened or dropped before task metadata is removed.
If no coherent task record can fit the configured budget, the endpoint returns
a structured `AI_TASK_FAILED` error.

Request:

```sh
curl -i -X POST http://localhost:8080/tasks/summary
```

Success response:

```json
{
  "summary": "You have several open tasks. The quarterly report is the highest-attention item because it is high priority and overdue by 2 days.",
  "plan": [
    "Finish the quarterly report first.",
    "Move the in-progress launch checklist forward next.",
    "Then handle lower-priority TODO tasks due later."
  ]
}
```

When there are no tasks, the endpoint returns a local response without OpenAI:

```json
{
  "summary": "You do not have any tasks yet.",
  "plan": [
    "Create a task to start planning your work."
  ]
}
```

When `OPENAI_API_KEY` is missing and there are tasks:

```json
{
  "timestamp": "2026-05-25T04:43:51.216688Z",
  "status": 503,
  "error": "AI_CONFIGURATION_MISSING",
  "message": "AI configuration is missing. Set OPENAI_API_KEY to enable AI task features.",
  "path": "/tasks/summary",
  "fieldErrors": []
}
```

Summaries are advisory and are grounded only in the stored task data sent to the
AI client. The model is instructed not to invent tasks, deadlines, blockers,
priorities, or project context. Summary responses are validated for nonblank
summary text, bounded summary length, bounded plan count, and bounded plan-item
length; invalid model output returns `AI_TASK_OUTPUT_INVALID` rather than being
silently trimmed.

## Error Responses

Errors are returned as structured JSON and do not expose stack traces.

Validation errors include field-level details:

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

AI errors use endpoint-neutral task-AI codes because both task suggestions and
task summaries share the same AI boundary:

- `AI_CONFIGURATION_MISSING`: `OPENAI_API_KEY` is not configured for an AI path
  that needs OpenAI.
- `AI_TASK_FAILED`: token preflight, provider calls, configured context budget,
  or another AI integration step failed in a controlled way.
- `AI_TASK_OUTPUT_INVALID`: the model returned malformed or unusable structured
  output after the endpoint's validation/retry policy.

## Manual OpenAI Smoke Checklist

Automated tests must remain deterministic and mock OpenAI. Use this checklist
only for local manual verification:

- Start the app without `OPENAI_API_KEY`; confirm `./mvnw spring-boot:run`
  starts.
- Confirm task CRUD still works without `OPENAI_API_KEY`.
- Confirm `POST /tasks/suggest` without `OPENAI_API_KEY` returns structured
  `503 AI_CONFIGURATION_MISSING`.
- Confirm `POST /tasks/summary` with no tasks returns the local empty-state
  response without OpenAI.
- Confirm `POST /tasks/summary` with stored tasks and no `OPENAI_API_KEY`
  returns structured `503 AI_CONFIGURATION_MISSING`.
- Set `OPENAI_API_KEY` locally and confirm `POST /tasks/suggest` returns a
  structured suggestion.
- With `OPENAI_API_KEY` set, submit suggestion text with no due date and confirm
  the endpoint can return `dueDate: null`.
- With `OPENAI_API_KEY` set, submit `follow up with finance next <today's
  weekday>` and confirm the returned `dueDate` is that weekday in the next
  Monday-Sunday calendar week, not today's date.
- With `OPENAI_API_KEY` set, submit date phrases for `today`, `June 5`,
  `before Friday`, `next Friday`, `before next Friday`, `end of week`,
  `not this Friday but the one after`, `the Friday after next`, and `two days
  before end of month`; confirm results match the documented server-local date
  semantics and end of week resolves to Sunday.
- Confirm the UI does not directly create a task from a no-date suggestion.
- Confirm the UI can copy a no-date suggestion into the form, let the user add a
  due date, and then create the task.
- With `OPENAI_API_KEY` set and stored tasks present, confirm
  `POST /tasks/summary` returns bounded `summary` and `plan` JSON.

## Known Limitations

- H2 persistence is in memory. Task data resets when the application process
  stops, and this is intentional for the local take-home scope.
- Live OpenAI suggestions and summaries require local `OPENAI_API_KEY`
  configuration. The core CRUD app still runs without OpenAI credentials.
- Automated tests mock OpenAI boundaries and do not call the live provider.
  Live provider behavior is covered by the manual smoke checklist above when a
  local API key is available.
- The UI is intentionally minimal: it supports the required local workflows but
  is not a production design system or full frontend application.
- OpenAI transport failures are not retried. Only invalid model output for task
  suggestions is retried once with a specific validation reason.
- There is no circuit breaker, fallback AI response, or explicit OpenAI timeout
  configuration. Those are production-hardening concerns and are intentionally
  out of scope for this take-home.
- Task list filtering/sorting and summary candidate selection are in-memory,
  which is acceptable for the local H2 scope but not for production-scale task
  data.

## Scope

Included:

- Task CRUD
- H2 in-memory persistence
- Static UI
- OpenAI task suggestions
- OpenAI task summaries and focus plans
- Local tests and smoke-run support

Out of scope:

- Authentication or authorization
- Production database configuration
- Deployment configuration
- Background jobs
- Unrelated task-management features
