# Spring Boot Task Manager Direction

- date: 2026-05-25

## Context

The take-home project direction became concrete: build a small Java 17 Spring
Boot personal task manager with H2 persistence, task CRUD, a minimal static UI,
and an OpenAI-backed task suggestion endpoint.

## Record

Use a conventional Spring Boot structure with controllers, services,
repositories, JPA models, DTOs, configuration, and a small AI client boundary.
The app must run locally with `./mvnw spring-boot:run` and tests must run with
`./mvnw test`.

Task creation and full update require `title`, `dueDate`, `priority`, and
`status`; `description` is optional. AI suggestions must return complete valid
task data. If the model returns invalid output, retry once and then return a
first-class failure without defaults or fallback values.

OpenAI configuration is local-only:

- `OPENAI_API_KEY`
- `OPENAI_MODEL`, defaulting to `gpt-5.4-nano`

The application must start without OpenAI credentials. Task CRUD and the UI
must work without AI configuration.

## Evidence

User requirements on 2026-05-25 specified Java 17, Spring Boot, Maven, H2,
OpenAI Responses API, `.env.example`, no committed secrets, CRUD endpoints, AI
suggestion endpoint, static UI, required tests, and final verification.

The local workspace did not have a git repository, so the first implementation
slice initializes git to satisfy the requested git workflow.

## Future Guidance

Keep scope small. Do not add authentication, authorization, production database
configuration, deployment setup, background jobs, or unrelated task-management
features unless the product direction changes.
