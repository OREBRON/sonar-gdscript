# Bootstrap verification

Local verification on 2026-10-20, repeated for the 2026.1 LTA baseline on
2026-10-21; this is not a production compatibility report.

## Toolchain and pins
- JDK 17 and JDK 21, Maven 3.9.9; Java 17 bytecode (`maven.compiler.release` 17).
- Sonar Plugin API 13.4.3.4290 (Server 2026.1 LTA floor), scope provided.
- Sonar packaging Maven plugin 1.25.1.3002.
- Test harness `sonar-plugin-api-impl` 26.2.0.119303 (Community Build 26.2 ships
  the same Plugin API 13.4.3); JUnit 5.11.4.
- Build images:
  `maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc`,
  `maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e`.

The [official API compatibility table](https://github.com/SonarSource/sonar-plugin-api#compatibility)
is the source for the supported floor; deployment must still be tested
independently. See [ADR 0002](adr/0002-support-baseline-2026-1-lta.md).

## Executed checks
`mvn -B -ntp -Dmaven.repo.local=/m2 clean verify` was executed in the pinned build
containers on a copy of the source with a separate user-owned Maven cache.

| JDK | Plugin API | Corresponds to | Result |
|---|---|---|---|
| 17 | 13.4.3.4290 (default) | Server 2026.1 LTA, Community Build 26.1–26.2 | BUILD SUCCESS, 6 tests |
| 21 | 13.4.3.4290 (default) | Server 2026.1 LTA, Community Build 26.1–26.2 | BUILD SUCCESS, 6 tests |
| 21 | 13.8.0.4399 | Server 2026.4, Community Build 26.7–26.8 | BUILD SUCCESS, 6 tests |
| 21 | 14.0.0.4498 | Server 2026.5 LTA, Community Build 26.9 | BUILD SUCCESS, 6 tests |
| 21 | 14.2.0.5584 | latest published API | BUILD SUCCESS, 6 tests |

Non-default rows pass `-Dsonar.api.version=<version>`. All runs had 0 failures,
0 errors and 0 skipped tests. Tests exercise registration/profile and sensor
behavior, including a real original GDScript fixture whose expected issue is on
line 2; threshold boundaries, Unicode, CRLF, empty input, inactive rule, invalid
configuration and file-type filtering.

Generated artifact: `target/sonar-gdscript-plugin-0.1.0-SNAPSHOT.jar`.
Inspected manifest includes `Plugin-Key: gdscript`,
`Plugin-Class: org.orebron.gdscript.GDScriptPlugin`, `Sonar-Version: 13.4.3`,
`Jre-Min-Version: 17`, `Java-Version: 17` and
`Plugin-RequiredForLanguages: gdscript`; plugin classes are class-file version 61
(Java 17). The build does not bundle the provided Sonar API as a dependency JAR.

The local Markdown-link checker and actionlint validation of the GitHub Actions
workflow also passed. CI results must be read from the actual workflow run rather
than inferred from these local checks.

## Not verified
- Server loading/startup or scanner download on any server version, including
  the 2026.1 floor and the 2026.5 LTA.
- Analysis on a real Java 17 scanner runtime; only the JDK 17 build/test ran.
- End-to-end report upload, Compute Engine processing or UI finding display.
- AST parsing, Godot semantic correctness, security analysis, metrics or coverage.
- Godot editor/LSP/gdlint integration; no game was executed.

The existing working SonarQube was not modified or restarted by this bootstrap.
The [roadmap](roadmap.md) and [development guide](development.md) define the
isolated end-to-end gate needed before deployment or a compatibility claim.
