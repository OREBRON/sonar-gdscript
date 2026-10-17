# Contributing

Read the [architecture](docs/architecture.md) and [roadmap](docs/roadmap.md).

1. Pick one acceptance criterion and add a failing test.
2. Implement the smallest change without altering scanned projects.
3. Run Maven verify and the documentation check described in the README.
4. Review negative fixtures, unsupported syntax and false positives.
5. Update the rule specification and compatibility evidence.

Use Conventional Commits. Keep source, tests and documentation together. Do not
commit target/, tokens, private game source or report archives. Fixtures must be
original/minimal or have explicit license/provenance permission.

No public reusable release until the distribution license and exact-version
integration gates are resolved. CI must not require org secrets or execute games.
