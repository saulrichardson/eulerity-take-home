# AI Suggestion And OpenAI Integration Polish

- date: 2026-05-25

## Context

The AI integration already used provider-backed token preflight and mockable AI
boundaries. A final polish pass clarified draft suggestion behavior, OpenAI
client setup, relative-date handling, and documentation for production
hardening limits.

## Record

This record describes the polish pass before the internal AI due-date rule DSL.
Current suggestion date behavior is documented in
`2026-05-25-ai-due-date-rule-dsl.md`.

AI suggestions are drafts. Persisted tasks remain records. Draft suggestions may
return `dueDate: null` or an empty due date when the user did not state or
strongly imply a due date. Creating or fully updating a persisted task still
requires `dueDate`.

The static UI now prevents direct task creation from a no-date suggestion and
guides the user to copy the suggestion into the form, add the required due date,
and save normally. Backend validation remains the source of truth for persisted
task records.

OpenAI suggestion instructions now avoid contradictory due-date language. They
ask the model to set `dueDate` only when stated or strongly implied, use
`yyyy-MM-dd` when set, and avoid inventing a date. The prompt date context comes
from injected `Clock`, matching summary date handling. The date is captured once
per suggestion workflow in `TaskSuggestionService` and carried through
`AiTaskSuggestionPrompt`, so token preflight and model request construction use
the same date even if the system clock crosses midnight between calls.

OpenAI client setup now lives in `OpenAiClientProvider`. Token counting and
model calls share that provider, which centralizes missing-key behavior and
memoizes the configured client for the process lifetime.

Timeouts, transport retries, circuit breakers, and fallback AI responses remain
out of scope for this take-home. Invalid model output may still be retried once
where implemented, but provider/transport failures return controlled structured
AI errors.

Task list filtering/sorting and summary candidate selection remain in memory.
That is acceptable for the local H2 take-home scope and should be revisited
before treating this as a production-scale task system.

## Evidence

- `OpenAiTaskRequestFactoryTest` covers deterministic prompt date context,
  non-contradictory due-date instructions, and no-date payload handling.
- `TaskSuggestionServiceTest` verifies a missing suggestion due date does not
  trigger retry or failure, and verifies token preflight and model calls receive
  the same captured suggestion prompt.
- `TaskCrudIntegrationTest` verifies persisted create/update requests still
  reject missing `dueDate`.
- `StaticUiLimitsTest` verifies the static UI contains the no-date direct-create
  guard.
- `OpenAiClientProviderTest` verifies missing-key behavior and client
  memoization.

Follow-up cleanup generalized AI exception names and public error codes after
this record. Current task-AI error behavior is documented in
`2026-05-25-final-ai-cleanup.md`.

## Future Guidance

Keep the draft-vs-record distinction explicit. Do not make persisted due dates
optional unless product direction changes. If OpenAI traffic becomes important
outside local take-home use, add explicit timeout and resilience policy as a
separate production-hardening change rather than hiding it inside prompt or UI
logic.
