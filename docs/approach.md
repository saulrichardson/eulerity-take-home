# Project Approach

Project: Eulerity Take-Home

This is a Java 17 Spring Boot personal task manager for the Eulerity take-home.
It uses H2 in-memory persistence, explicit REST DTOs, and a small OpenAI client
boundary for AI task suggestions and task-list summaries.

This file is the current project truth.

Use it to understand how the project is shaped today: the selected stack, the
architecture, the operating model, the constraints that matter, and the way work
is verified and delivered.

Use `product-intent.md` for the product north star. Use `records/` for durable
rationale, caveats, lessons, and dated decisions.

## Project At A Glance

- domain: personal task management
- main users: local task user; Eulerity reviewer
- main workflows: task CRUD, task list filtering/sorting, AI task suggestion,
  AI task summary/focus plan, local review
- current stage: local Spring Boot MVP implemented

## Stack

- frontend: static HTML/CSS/JavaScript served by Spring Boot, organized as
  `index.html`, `styles.css`, `app.js`, `api.js`, and `ui.js`
- backend: Java 17, Spring Boot 4.0.6, Spring Web MVC
- data store: H2 in-memory database through Spring Data JPA
- auth or policy model: none; authentication and authorization are out of scope
- background work or workflow runtime: none
- AI integration: OpenAI Java SDK 4.37.0 behind a centralized
  `OpenAiClientProvider`, mockable client boundary, and mockable token-count
  boundary for suggestions and summaries
- hosting or deployment target: local execution only
- build command: `./mvnw package`
- test command: `./mvnw test`
- run command: `./mvnw spring-boot:run`
- deploy command: not selected

When the stack changes, update this section for the current truth. Add a record
when future agents should understand why the choice was made.

## Architecture

Describe the current system shape at the level future agents need to act.

- main applications or services: one Spring Boot application in
  `com.eulerity.taskmanager`
- important directories: `src/main/java/` for application code,
  `src/main/resources/static/` for the UI, `src/test/java/` for tests,
  `docs/` for project-local context
- where product rules live: service classes
- where state and durable data live: JPA entities and repositories backed by H2
  in memory
- list behavior: `GET /tasks` can filter by `status` and `priority`, and sort
  by `id`, `dueDate`, or priority order (`HIGH`, `MEDIUM`, `LOW`)
- static UI behavior: the dashboard centers the task list, supports create,
  full edit/update through `PUT /tasks/{id}`, delete, status completion,
  filtering/sorting, AI task drafts, and summary/focus-plan output
- completion behavior: `PATCH /tasks/{id}/status` updates only task status;
  broader partial-update behavior is intentionally not exposed
- AI suggestion behavior: suggestions are drafts and may omit `dueDate` when
  the source text does not state or strongly imply a date; persisted tasks still
  require `dueDate`
- AI suggestion due-date handling: the OpenAI structured payload uses an
  internal whitelisted `dueDateRule` expression DSL, such as
  `next_or_same(FRIDAY)`, `month_day(06-05)`,
  `minus_days(end_of_month(),2)`, `next(FRIDAY)`,
  `minus_days(next(FRIDAY),1)`, or `none()`, and backend code parses and
  resolves that expression to the public `dueDate` response using the captured
  server-local date
- summary behavior: `POST /tasks/summary` loads stored tasks, returns a
  natural-language summary and short plan, and never persists the AI output
- summary task selection: `ai.summary.candidate-task-cap` defaults to 30 tasks;
  relevance favors open, in-progress, high-priority, overdue, and soon-due tasks
  with deterministic tie-breakers
- AI token budgeting: both `/tasks/suggest` and `/tasks/summary` preflight the
  final OpenAI request shape through `TaskAiTokenCounter` before model calls;
  production counting uses the OpenAI Responses input-token count API
- summary prompt budgeting: `TaskSummaryService` builds coherent candidate
  `AiTaskSummaryPrompt` records and accepts a prompt only when counted input
  tokens fit both the application summary budget and the model hard ceiling
  after output tokens and request overhead reserve
- summary context reduction: preserve full task records and critical metadata;
  shorten descriptions at paragraph, sentence, or word boundaries where
  possible, mark shortened descriptions, and omit lower-relevance records as
  whole records
- date-sensitive summary behavior: `Clock` is injected so due timing context is
  deterministic in tests and based on the server local date at runtime
- date-sensitive suggestion behavior: OpenAI suggestion instructions also use
  injected `Clock` for relative-date context; `TaskSuggestionService` captures
  the current date once per suggestion workflow and passes it through
  `AiTaskSuggestionPrompt` so token preflight, model calls, and internal
  due-date rule resolution use the same date
- suggestion due-date semantic guard: for a narrow set of explicit weekday
  phrases such as `before Friday`, `next Friday`, `before next Friday`,
  `not this Friday but the one after`, and `the Friday after next`, the service
  rejects syntactically valid but semantically wrong model dates and retries
  once with a specific correction reason
- where integrations and side effects live: the OpenAI client implementation
  under an AI boundary; API-key checks and configured client creation live in
  `OpenAiClientProvider`
- where deployment and operations live: local Maven Wrapper commands and
  README instructions

## Operating Model

The runtime operating model is:

```text
HTTP or UI action
  -> controller DTO validation
  -> service behavior
  -> repository or AI client boundary
  -> structured response or structured error
```

Keep this section focused on the project-level model. Feature details belong in
code, tests, and records when the reasoning should persist.

## Constraints And Invariants

Document the facts that shape future work.

- durable source of truth: code, tests, configs, README, and project docs
- important entities or resources: `Task`
- states or lifecycles that matter: task status is `TODO`, `IN_PROGRESS`, or
  `DONE`
- rules that must remain true: do not copy the starter repository README into
  this project; write any project README separately for the actual project
- task request limits: title is at most 255 characters; description is at most
  8000 characters
- AI suggestion request limit: plain-language description is at most 12000
  characters because it may contain raw pasted context; returned suggestions
  must still satisfy task title and description limits
- due-date rule split: task records require `dueDate`; AI suggestion drafts may
  return public `dueDate: null` when no due date is stated or strongly implied
- AI suggestion date math is app-owned: the model returns only one supported
  `dueDateRule` expression internally. Supported forms include `none()`,
  `date(YYYY-MM-DD)`, `month_day(MM-DD)`, simple day/week offsets,
  composition such as `minus_days(next(FRIDAY),1)`, weekday rules such as
  `next(WEEKDAY)` and `nth_next(WEEKDAY,N)`, and month/week boundary rules; the
  app does not evaluate model-generated code or expose the DSL in the public API
- AI suggestion weekday convention: `by Friday`, `on Friday`, bare `Friday`,
  and `this Friday` use the upcoming-or-same weekday; `before Friday` means the
  day before that weekday; `next Friday` means Friday in the next Monday-Sunday
  calendar week; `before next Friday` means the day before that next-week
  Friday; `the Friday after next` means the second upcoming Friday
- AI suggestion date-expression limits: expressions have no spaces, use only
  whitelisted functions, cap nesting at 3 levels, cap numeric offsets, define
  end of week as Sunday, and deliberately exclude business days, holidays,
  recurrences, external calendars, and timezone-personalized scheduling
- AI suggestion explicit dates: `date(YYYY-MM-DD)` may resolve to a past date
  when the user explicitly provides one; the app treats that as an overdue
  draft rather than rejecting it
- AI suggestion semantic validation stays narrow: backend validation may reject
  a syntactically valid model date when it clearly contradicts one of the
  documented explicit weekday phrases, but it should not grow into broad
  natural-language date parsing
- OpenAI model context window defaults to 400000 tokens for `gpt-5.4-nano`;
  `openai.context-usage-ratio` defaults to 0.90 and is validation-capped at
  0.90, producing a 360000-token hard ceiling before output and request overhead
  reserves
- OpenAI request overhead reserve is configured with
  `openai.request-overhead-reserve-tokens` and applies to AI request preflight
- OpenAI requests set explicit output-token caps through configuration:
  `openai.suggestion-max-output-tokens` and `openai.summary-max-output-tokens`
- AI request preflight is strict: if counted input tokens plus the relevant
  output-token cap and overhead reserve exceed the model hard ceiling, the app
  returns a structured `AI_TASK_FAILED` error instead of calling the model
- AI error taxonomy: missing configuration remains `AI_CONFIGURATION_MISSING`;
  controlled AI integration failures use `AI_TASK_FAILED`; malformed or invalid
  model output uses `AI_TASK_OUTPUT_INVALID`
- model-window caveat: token preflight enforces the configured model context
  window and usage ratio; if `OPENAI_MODEL_CONTEXT_WINDOW_TOKENS` is inaccurate
  for a custom model, the app cannot prove the provider's real hidden limit
- AI summary output is deterministically validated for nonblank summary text,
  summary length, plan item count, nonblank plan items, and plan item length
- AI summary behavior: if no tasks exist, return a local empty-state response
  without OpenAI; if tasks exist and `OPENAI_API_KEY` is missing, return the
  structured AI configuration error
- AI summaries must be grounded in stored task data and precomputed due timing
  context; do not ask the model to calculate date math
- sensitive data or ownership boundaries: OpenAI API keys must only come from
  local environment or `.env`, never source code
- actions that need coordination: public API shape, OpenAI configuration,
  persistence choices, and deployment
- high-blast-radius or irreversible actions: none expected while using H2
  in-memory persistence
- production-hardening limitations: no OpenAI transport retry system, circuit
  breaker, fallback AI responses, or explicit OpenAI timeout configuration yet;
  those are out of scope for this local take-home
- scale limitations: task list filtering/sorting and summary candidate
  selection are handled in memory, acceptable for local H2 but not for a
  production-scale task system

## Verification And Delivery

- expected local checks: `./mvnw test`
- automated test configuration comes from `src/test/resources/application.properties`
  and intentionally does not import local `.env`, keeping tests deterministic
  even when a developer has local OpenAI settings
- expected manual checks: `./mvnw spring-boot:run`, CRUD smoke checks, static UI
  smoke check, AI missing-configuration smoke check, summary empty-state smoke
  check, and the README manual OpenAI smoke checklist when a real API key is
  available locally
- migration or data checks: not applicable yet
- deployment path: not selected
- smoke checks or operational signals: HTTP statuses and structured error
  responses
- rollback or mitigation path: stop the local app; H2 data resets on restart

## Open Questions

- None currently. Deployment remains out of scope unless the product direction
  changes.

## Related Records

List records that explain why the current approach exists or what future agents
should remember.

- `records/2026-05-24-initial-operating-context.md`
- `records/2026-05-25-spring-boot-task-manager.md`
- `records/2026-05-25-task-validation-hardening.md`
- `records/2026-05-25-ai-hardening.md`
- `records/2026-05-25-ai-task-summary.md`
- `records/2026-05-25-ai-context-budgeting.md`
- `records/2026-05-25-ai-token-preflight.md`
- `records/2026-05-25-ai-suggestion-openai-polish.md`
- `records/2026-05-25-ai-due-date-rule-dsl.md`
- `records/2026-05-25-frontend-dashboard-redesign.md`
