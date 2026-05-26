# Frontend Dashboard Redesign

- date: 2026-05-25

## Context

The first static UI satisfied the required workflows, but it was a single large
HTML file with inline CSS and JavaScript. As task list behavior, AI drafts, and
summary flows grew, that structure became harder to read and the interface felt
more like a prototype than a usable task dashboard.

## Record

The frontend remains intentionally lightweight and static. It is now organized
as:

- `index.html` for semantic page structure
- `styles.css` for the minimalist/refined dashboard visual system
- `api.js` for backend calls
- `ui.js` for DOM rendering and form helpers
- `app.js` for state and event coordination

The task list is the primary dashboard surface. Task creation, editing, AI
drafts, and the focus plan live in the side workspace. Task rows replaced the
table so long descriptions can be previewed without breaking scanability.

The UI now exposes the existing full-update API through an edit workflow. This
does not add backend partial-update behavior; status-only updates still use the
focused `PATCH /tasks/{id}/status` endpoint.

## Evidence

- Manual browser verification covered the no-date AI draft flow through the
  real local dashboard, along with create, edit, mark done, delete,
  filtering/sorting, and summary rendering.
- `StaticUiLimitsTest` verifies frontend field limits, the no-date direct
  create guard, and the edit-in-form copy path across the split static files.
- `StaticUiControllerTest` verifies `GET /` serves the static dashboard without
  requiring browser tooling in the committed `./mvnw test` suite.

The earlier committed Selenium/Chrome smoke test was removed from the default
automated suite so `./mvnw test` stays deterministic on a clean Java/Maven
environment without depending on local browser availability.

## Future Guidance

Keep the frontend static unless the product scope changes materially. Do not
add a build system or framework for small UI improvements. Preserve the file
ownership split so API calls, rendering, and workflow state do not collapse back
into one large page.
