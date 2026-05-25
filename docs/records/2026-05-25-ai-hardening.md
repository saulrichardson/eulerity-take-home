# AI Suggestion Hardening

- date: 2026-05-25

## Context

The AI suggestion endpoint should be useful without making the task manager
fragile. Bad AI input, missing configuration, model failures, and malformed
model output should all return controlled API responses.

## Record

This record describes the initial AI hardening pass. The current AI suggestion
request limit and task field limits were later raised; see
`2026-05-25-ai-context-budgeting.md` and
`2026-05-25-ai-token-preflight.md` for current limits and budgeting behavior.

AI suggestion request text is capped at 2000 characters. This is enough for
plain-language task intent while bounding request size, latency, and model cost.

Returned AI suggestions are validated against the same stored task limits:

- `title`: at most 200 characters
- `description`: at most 1000 characters

Unexpected runtime failures from the AI client boundary are wrapped into the
existing `AI_TASK_FAILED` error path instead of falling through as generic
internal errors. Transport failures are still not retried; only invalid model
output is retried once.

## Future Guidance

Keep AI suggestions non-persistent unless the product direction changes. If the
OpenAI call becomes user-facing at higher volume, consider explicit SDK timeouts
and request-cost limits before broader resilience patterns.
