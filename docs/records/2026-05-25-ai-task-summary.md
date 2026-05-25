# AI Task Summary

- date: 2026-05-25

## Context

The task manager now includes `POST /tasks/summary`, an AI-assisted focus-plan
endpoint grounded in stored task data. The endpoint should help the user decide
what to work on next without persisting AI-generated text or expanding the core
task domain.

## Record

Task summaries are produced through `TaskSummaryService` and the `TaskAiClient`
boundary. Token preflight is performed through `TaskAiTokenCounter`. The
controller only exposes the HTTP endpoint; it does not build prompts or contain
OpenAI-specific behavior.

The service loads tasks from the repository. If there are no tasks, it returns a
local empty-state summary and does not call OpenAI. If there are tasks, it sends
bounded task context to the AI client. The default candidate cap is 30 tasks and
is now configurable through `ai.summary.candidate-task-cap`.

Task selection is deterministic. Relevance favors open work, `IN_PROGRESS`
tasks, higher priorities, overdue tasks, tasks due today, and tasks due soon.
Tie-breakers then sort by due date, priority, status, and id. This keeps prompt
size bounded without adding pagination or complex search.

Due timing is computed in application code using an injected `Clock`. The AI is
given readable timing context such as `due today`, `overdue by 2 days`,
`overdue by 3 weeks`, `due in 4 days`, or `due in 2 weeks`. The model is
instructed to use this context and not calculate date math independently.

LLM communication instructions live in `OpenAiTaskRequestFactory`, next to the
OpenAI request-shape construction used by both token counting and model calls.
They require a concise, practical, calm, direct style and prohibit inventing
tasks, deadlines, blockers, priorities, or project context.

## Evidence

- `TaskSummaryServiceTest` verifies empty-state behavior, the 30-task cap,
  deterministic relevance selection, and due timing in days and whole weeks.
- `TaskSummaryControllerTest` verifies a successful mocked summary response and
  the structured missing-configuration error.
- `./mvnw test` passed after adding the endpoint and tests.

## Future Guidance

Keep summaries advisory and non-persistent. If the summary prompt grows more
complex, preserve the existing boundary: selection and timing logic belong in
application services, while OpenAI request/response details and LLM
communication instructions belong in the AI integration layer.
