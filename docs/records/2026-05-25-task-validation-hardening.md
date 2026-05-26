# Task Validation Hardening

- date: 2026-05-25

## Context

The task workflow already validated required fields at the API boundary, but
length limits were only implicit or persistence-level. Invalid client input
should fail before it reaches the database.

## Record

This record describes the initial validation hardening pass. The current limits
were later raised; see `2026-05-25-ai-context-budgeting.md` for current task
text limits.

Historical caveat: the values below reflect the implementation at the time of
this record. Current task and AI text limits are documented in `README.md` and
`docs/approach.md`.

Task request DTOs now enforce:

- `title`: at most 200 characters
- `description`: at most 1000 characters

The title entity column is explicitly capped at 200 characters. The description
DTO limit matches the existing 1000-character entity/database column limit.

Overlong task inputs should return the existing structured
`400 VALIDATION_FAILED` response with field-level errors. Malformed dates and
invalid enum values remain structured `400 BAD_REQUEST` responses because they
fail during request deserialization before bean validation runs.

## Future Guidance

Keep API request validation aligned with entity/database limits. When adding new
stored task fields, define request validation before relying on persistence
errors.
