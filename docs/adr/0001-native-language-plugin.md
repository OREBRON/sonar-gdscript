# ADR 0001: native GDScript language plugin

Status: accepted direction; parser backend remains open.

## Context
Existing SonarScanner handles configuration, sensor execution and report upload.
The missing capability is GDScript understanding. The old plugin is unfinished
and has no verified compatibility with the current development target.

## Decision
Develop an original Java SonarQube plugin registering GDScript and a scanner-side
sensor. Start with a well-tested line-based rule and preserve a seam for future
parser-backed checks. Do not reimplement SonarScanner or copy the old plugin.

## Alternatives
- External gdlint reports: cheaper server maintenance, but not full native support.
- Fork old plugin: outdated grammar/API plus LGPL/provenance obligations.
- Write a grammar first: broad scope before the integration path is validated.

## Consequences
Server plugin installation/restart and API compatibility maintenance are required.
The sensor runs on scanner machines. Registration does not automatically provide
AST, metrics, duplication, coverage or semantic correctness; each needs tests.
