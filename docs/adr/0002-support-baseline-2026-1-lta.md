# ADR 0002: support baseline SonarQube Server 2026.1 LTA

Status: accepted.

## Context
Every SonarQube Server 2026.1.x patch ships Plugin API 13.4.3.4290; Community
Build 26.1 and 26.2 ship the same API. Server 2026.1 still accepts Java 17
scanners (deprecated; Java 21 is required from 2026.4). Building against the
newest API (13.8, Community Build 26.7) made the plugin load only on 2026.4+.
See [research](../research.md#version-baseline).

## Decision
- Compile against Plugin API 13.4.3.4290 and declare `pluginApiMinVersion` 13.4.3.
- Emit Java 17 bytecode and declare `jreMinVersion` 17.
- Test with `sonar-plugin-api-impl` 26.2.0.119303, the Community Build harness
  for the same API. The newer `sensor-test-fixtures` artifact needs Plugin API
  13.5+ (`NewIssueResolution`) and cannot run at this floor.
- CI tests the floor on JDK 17 and the current LTA API (2026.5, 14.0.0.4498) on JDK 21.

## Consequences
The plugin may only use API available in 13.4.3; the floor build fails otherwise.
Tests use the older `org.sonar.api.batch.*.internal` harness packages until the
floor moves past Community Build 26.6. Raising the floor requires a new ADR and
matching updates to the pom, CI matrix and compatibility table. Build-level
checks do not replace the isolated end-to-end gate.
