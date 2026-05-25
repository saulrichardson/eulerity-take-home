# Final AI Cleanup

- date: 2026-05-25

## Context

The AI boundary had been generalized to `TaskAiClient`, but the remaining
exception and public error-code names still referred specifically to task
suggestions. Full Spring tests also inherited the runtime `.env` import, which
made the canonical test suite sensitive to local developer configuration.

## Record

Automated tests now load deterministic properties from
`src/test/resources/application.properties`. The runtime application still
imports optional local `.env` configuration, but the test classpath does not.
This keeps `./mvnw test` independent from local OpenAI credentials or malformed
local AI budget values.

The AI failure taxonomy is now endpoint-neutral:

- `AI_CONFIGURATION_MISSING` remains the structured 503 response for missing
  OpenAI configuration.
- `AI_TASK_FAILED` covers controlled AI integration failures, including token
  preflight, provider calls, and configured context-budget failures.
- `AI_TASK_OUTPUT_INVALID` covers malformed or invalid model output.

The UI no-date suggestion behavior remains intentionally lightweight. Static
tests protect the frontend limits and no-date guard in source, and manual browser
smoke verification covers the real draft-to-form workflow.

## Evidence

- Controller tests cover suggestion failure, suggestion invalid output, summary
  failure, summary invalid output, and missing OpenAI configuration.
- Test configuration isolation was verified with both normal local `.env` and an
  intentionally invalid temporary `.env`.
- The no-date suggestion UI draft behavior was verified through the local app in
  a browser with a temporary local proxy mocking the `/tasks/suggest` response:
  the no-date suggestion rendered, direct creation was disabled, editing copied
  the suggestion into the form with a blank due date, and blank-date submission
  was blocked by browser validation.

## Future Guidance

Keep automated tests mocked and deterministic. Real OpenAI behavior belongs in
manual smoke checks, not in the canonical Maven test suite.
