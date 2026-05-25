# AI Token Preflight

- date: 2026-05-25

## Context

A cleanup review found that AI summary budgeting used character counts labeled
as token estimates and did not count the final OpenAI request shape before
calling the model. The stricter remediation applies token preflight to both
`/tasks/suggest` and `/tasks/summary`.

## Record

AI context budgeting is now enforced with a provider-backed token-counting
boundary. `TaskAiTokenCounter` is the application-owned interface, and the
OpenAI implementation calls the Responses input-token count API. Tests mock this
boundary and do not call OpenAI.

OpenAI request construction is centralized in `OpenAiTaskRequestFactory` so the
count request and model request use the same instructions, input wrapper, model,
and structured-output configuration. OpenAI client setup and missing-key checks
are centralized in `OpenAiClientProvider`, shared by token counting and model
calls. A model call is allowed only after the final counted input tokens plus
the configured output-token cap and request overhead reserve fit under the
configured hard context ceiling.

Summary prompt reduction remains record-preserving. The service builds coherent
task records, shortens descriptions at natural boundaries, drops descriptions
before dropping metadata, and omits lower-relevance task records as whole
records when the counted final request is still too large.

AI budget configuration is validated at startup. The context usage ratio cannot
be configured above `0.90`, token windows and output caps must be positive, and
the summary candidate cap remains bounded at 30.

## Evidence

- `TaskSuggestionServiceTest` verifies suggestion token preflight and budget
  failure before the model call.
- `TaskSummaryServiceTest` verifies provider-counted budget reduction, metadata
  preservation, whole-record omission, and failure when no coherent record fits.
- `OpenAiTaskRequestFactoryTest` verifies token-count request params match the
  final create request shape for suggestions and summaries.
- `ConfigurationPropertiesValidationTest` verifies invalid budget configuration
  fails startup.
- `./mvnw test` passed with 59 tests after the change.

## Future Guidance

Do not reintroduce character counts as authoritative token budgets. Character
or length heuristics may help build an initial candidate, but final acceptance
must be based on `TaskAiTokenCounter` for the exact request shape that will be
sent to the model. Token preflight enforces the configured context window and
usage ratio; keep `OPENAI_MODEL_CONTEXT_WINDOW_TOKENS` accurate when changing
models.
