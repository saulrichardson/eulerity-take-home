# AI Context Budgeting

- date: 2026-05-25

## Context

Task text limits were raised so users can store richer task context and paste
larger raw notes into the AI suggestion flow. Longer stored descriptions made
the summary integration need a model-aware prompt budget instead of relying only
on a task-count cap.

## Record

Persisted task limits, prompt budgets, output-token caps, and deterministic
validation are separate concerns.

Current persisted limits are:

- task title: 255 characters
- task description: 8000 characters
- AI suggestion request text: 12000 characters

The AI suggestion input may be larger than the stored task description because
it is raw source material. The model is instructed to distill that source into
one task and return fields that satisfy the persisted task limits. The
application still validates the returned suggestion deterministically. AI
suggestions are drafts and may omit `dueDate` when the source text does not
state or strongly imply one; persisted task records still require `dueDate`.

For summaries, the app preserves coherent task records and reduces prompt
context by relevance and natural boundaries instead of mechanically truncating
serialized prompts. Task identity, title, due date, due timing, priority, and
status are preserved before description detail. Long descriptions are shortened
at paragraph, sentence, or word boundaries where possible and visibly marked
with `[description shortened]`. Lower-relevance tasks are omitted as whole
records when the budget is constrained.

The model-aware hard ceiling is the configured model context window multiplied
by the configured usage ratio. The default model context window for
`gpt-5.4-nano` is `400000` tokens and the default ratio is `0.90`, so the hard
ceiling is `360000` tokens. The application summary input budget defaults much
lower at `6000` counted input tokens.

Token budgeting is enforced through `TaskAiTokenCounter`, backed in production
by the OpenAI Responses input-token count API. Character length is not an
authoritative token budget. Suggestion and summary model calls are preflighted
against the final request shape before calling OpenAI.

OpenAI suggestion and summary requests set explicit output-token caps. Summary
model output is validated for nonblank summary text, summary length, plan count,
nonblank plan items, and plan item length.

This record describes current limits and context-budgeting behavior. Older
records that mention earlier 200/1000/2000-character limits are historical.

## Evidence

- `TaskCrudIntegrationTest` covers the raised task text limits.
- `TaskSuggestionControllerTest` covers the raised AI suggestion request limit.
- `TaskSuggestionServiceTest` covers specific retry reasons and deterministic
  rejection of oversized returned fields after retry.
- `TaskSummaryServiceTest` covers task relevance, counted budget pressure,
  natural description shortening, metadata preservation, whole-record omission,
  and the 90% model-window ceiling.
- `OpenAiTaskRequestFactoryTest` covers prompt limit instructions, schema
  descriptions, output-token caps, token-count request shape, and summary output
  validation.
- `StaticUiLimitsTest` verifies UI maxlength attributes match backend limits.

## Future Guidance

Do not solve AI prompt pressure by cutting the final serialized prompt. Keep
prompt selection as a deterministic, testable application concern and keep
OpenAI request construction, token counting, and structured response handling
inside the AI integration. If a future model changes, update either the
configured context window or a known model-limit mapping before relying on the
model-aware budget.
