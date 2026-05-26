# AI Due-Date Rule DSL

- date: 2026-05-25

## Context

AI task suggestions previously asked the model to return a concrete ISO
`dueDate` or `null`. That kept the public API simple, but it put relative-date
calculation such as "before Friday" mostly inside the model.

## Record

AI suggestions now use a bounded internal due-date expression DSL. The model
returns a single whitelisted `dueDateRule` inside the OpenAI structured
payload, and the backend parses and resolves that expression against the
server-local date captured in `AiTaskSuggestionPrompt`.

Supported internal expressions are intentionally small, pure, and bounded:

- `none()`
- `date(YYYY-MM-DD)`
- `month_day(MM-DD)`
- `plus_days(N)`
- `plus_days(EXPR,N)`
- `plus_weeks(N)`
- `plus_weeks(EXPR,N)`
- `minus_days(EXPR,N)`
- `minus_weeks(EXPR,N)`
- `next_or_same(WEEKDAY)`
- `next(WEEKDAY)`
- `nth_next(WEEKDAY,N)`
- `end_of_week()`
- `start_of_next_week()`
- `end_of_month()`
- `start_of_next_month()`
- `end_of_next_month()`

The app deliberately does not support business-day calendars, holidays,
recurrence, external calendar lookup, or timezone-personalized scheduling. If
the model cannot represent a due-date phrase with one supported expression, it
should return `none()` so the public suggestion can fall through to the normal
user-adds-date path.

The public `/tasks/suggest` response still exposes only `dueDate` as
`yyyy-MM-dd` or `null`. The UI does not know about the DSL. Persisted task
create/update requests still require `dueDate`.

No model-generated code is executed. The parser accepts only exact supported
expressions with no spaces, rejects unknown functions, invalid dates, invalid
weekdays, extra text, out-of-range numeric arguments, and nesting deeper than
3 levels, and surfaces failures through the existing `AI_TASK_OUTPUT_INVALID`
retry/failure path.

End of week is a product rule, not a locale rule: `end_of_week()` resolves to
the next-or-same Sunday.

Weekday language is also a product rule. The app treats `by Friday`, `on
Friday`, bare `Friday`, and `this Friday` as the upcoming-or-same Friday.
`before Friday` and `before this Friday` mean the day before that
upcoming-or-same Friday. `next Friday` means Friday in the next Monday-Sunday
calendar week, not merely the next occurrence of Friday after today. `before
next Friday` means the day before that next-week Friday. `not this Friday but
the one after` means the next-week Friday. `the Friday after next` means the
second upcoming Friday.

Live testing showed that a model can still choose a syntactically valid but
semantically wrong rule. For example, when today is Monday, `next Friday` must
resolve to Friday of the next calendar week, not the current week's Friday.
The service now has a narrow semantic guard for this known class of errors: if
the source text contains one of the documented explicit weekday phrases and
the resolved public `dueDate` contradicts the product convention, the
suggestion is treated as invalid and retried with a targeted correction reason.

The same captured date is used for suggestion prompt instructions, token-count
request construction, model request construction, and date-rule resolution.
This keeps relative-date behavior deterministic and avoids clock-crossing
drift between token preflight and the model call.

## Evidence

- `AiDueDateRuleParserTest` covers supported expressions and rejection of
  natural language, unknown functions, invalid weekdays, invalid dates, nested
  expressions, executable-looking text, and out-of-range amounts.
- `AiDueDateRuleResolverTest` covers deterministic resolution from
  `2026-05-25`, including `month_day(MM-DD)`, composed offsets,
  `next(WEEKDAY)` resolving to the next Monday-Sunday calendar week,
  `before` composition, `nth_next(WEEKDAY,N)`, and `end_of_week()` resolving to
  Sunday.
- `OpenAiTaskRequestFactoryTest` verifies suggestion instructions and schema
  descriptions ask for `dueDateRule`, not public `dueDate`, and preserve final
  request shape matching for token preflight, including the `next Monday`,
  `next Friday`, `before Friday`, and `before next Friday` distinctions for the
  captured date.
- `TaskSuggestionServiceTest` verifies invalid DSL output retries with the
  specific validation reason, verifies explicit next-week and before-weekday
  misclassifications are retried, and still fails as invalid AI output after
  retry.
- `AiDueDateSemanticValidatorTest` covers the narrow guard that prevents
  explicit weekday phrases from resolving to the wrong side of the documented
  convention while allowing `this`, `on`, and `by` current-weekday phrases.

## Future Guidance

Keep this DSL deliberately bounded. Do not add business-day calendars, holiday
logic, recurrence, external calendar lookup, timezone-personalized scheduling,
or backend natural-language parsing unless product direction changes. If a
date phrase is not supported by the DSL, the model should choose `none()`
unless the user stated or strongly implied a date that maps to one of the
supported expressions. If future live testing finds more valid-but-wrong
classifications, prefer a precise semantic guard with tests over a broad
backend natural-language parser.
