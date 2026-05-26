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

- `TaskSuggestionUiSmokeTest` covers the no-date AI draft flow through a real
  browser and now also covers create, edit, mark done, and delete through the
  dashboard task rows.
- `StaticUiLimitsTest` verifies frontend field limits and the no-date direct
  create guard across the split static files.

## Future Guidance

Keep the frontend static unless the product scope changes materially. Do not
add a build system or framework for small UI improvements. Preserve the file
ownership split so API calls, rendering, and workflow state do not collapse back
into one large page.
