# Product Intent

This file communicates what `Eulerity Take-Home` is trying to become.

The project is a small personal task manager take-home application. It should
support a complete basic task workflow, use AI to turn plain-language task
intent into structured task data, and use AI to summarize the stored task list
into a concise focus plan.

Keep this file focused on product direction and user intent. Put technical
architecture in `approach.md` and durable rationale in `records/`.

## North Star

Deliver a clear, working, reviewable Java 17 Spring Boot task manager for
Eulerity. A reviewer should be able to run it locally with one command, exercise
task CRUD through the API or static UI, and understand how AI task suggestions
and task summaries are configured safely.

## Who It Serves

- A person managing personal tasks locally.
- The Eulerity reviewer evaluating whether the implementation is correct,
  maintainable, safe to configure, and practical to run.

## Desired Outcomes

- Users can create, view, update, delete, filter, sort, and mark tasks done.
- Users can submit plain-language task intent and receive a structured task
  suggestion without persisting it automatically.
- Users can paste rough notes, email excerpts, or meeting context into the AI
  suggestion flow and have it distilled into one bounded task.
- Users can store multi-paragraph task descriptions as task context without
  turning tasks into unlimited documents.
- Users can review an AI suggestion and explicitly create a real task from it;
  suggestions are drafts and may omit due dates, while persisted tasks require
  due dates.
- Users can summarize the stored task list into a short workload summary and
  practical focus plan without persisting the AI output.
- The app remains usable without OpenAI credentials; model-backed AI features
  are unavailable when task data must be sent to OpenAI.
- Reviewers can run the API and tests locally with Maven Wrapper commands.

## Core Workflows

- Manage tasks through explicit CRUD endpoints, including small list controls
  for status, priority, and useful sort order.
- Mark tasks complete from the task list without replacing the full task body.
- Use a small static UI to list tasks, create tasks, request AI suggestions,
  review suggested fields, confirm creation from a suggestion, and request a
  focus plan for the current task list.
- Configure OpenAI locally through environment variables when AI suggestions or
  task summaries should call the real API.
- Verify the app with `./mvnw test` and local smoke checks.

## Product Qualities

- Conventional Spring Boot structure.
- Explicit API requests, responses, validation, and errors.
- Safe configuration with no committed secrets.
- Small scope: task CRUD, AI suggestion, AI task summary, and minimal UI only.
- AI output should help with task creation and prioritization, but should not
  persist automatically or invent unsupported task context.
- AI prompts should stay bounded and coherent: preserve task identity and core
  metadata first, reduce description detail at natural boundaries, and omit less
  relevant task records as whole records when needed.
- AI requests should be token-budgeted with provider-backed preflight, so the
  app does not call the model when the final request shape cannot fit the
  configured model context budget.

## Examples

Representative examples, scenarios, sample inputs, sample outputs, or sketches:

- Plain-language AI input:
  `"remind me to submit the quarterly report before Friday"`
- Structured task suggestion:
  `{ "title": "Submit quarterly report", "dueDate": "2026-05-29", "priority": "MEDIUM", "status": "TODO" }`

## Open Questions

- None currently. Local execution is the intended review path, and deployment is
  out of scope for this iteration.

## When To Update This File

Update this file when the user clarifies the goal, a workflow becomes important,
an implementation reveals a product question, or the product direction changes.
