# Initial Operating Context

- date: 2026-05-24

## Context

The agent operating context was copied into this new project from the
`starter/` folder of `https://github.com/saulrichardson/agentic-engineering-template`.
The workspace currently has no application source, tests, configs, data, or
project README.

## Record

Only the operating context was imported:

- `starter/AGENTS.md` became `AGENTS.md`
- `starter/docs/` became `docs/`

The starter repository README was intentionally not copied. Any project README
should be written separately for the actual Eulerity take-home implementation.

Product, stack, architecture, persistence, deployment, and verification details
are not known yet. Future agents should update `docs/product-intent.md` and
`docs/approach.md` when the assignment prompt or source artifacts clarify those
facts.

## Evidence

Initial inspection found only `.DS_Store` before the import. After the import,
the expected files were present:

- `AGENTS.md`
- `docs/README.md`
- `docs/product-intent.md`
- `docs/approach.md`
- `docs/records/README.md`

The local directory was not a git repository at the time of setup.

## Future Guidance

Build from actual assignment artifacts when they arrive. Do not infer product
requirements, stack constraints, or deployment expectations beyond what the
repository or user provides.
