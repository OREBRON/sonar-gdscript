# Bootstrap verification

Local verification on 2026-10-20; this is not a production compatibility report.

## Toolchain and pins
- JDK 21, Maven 3.9.9.
- Sonar Plugin API 13.8.0.4399, scope provided.
- Sonar packaging Maven plugin 1.25.1.3002.
- Sensor test fixtures 13.4.0.3968; JUnit 5.11.4.
- Build image: `maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e`.

The [official API compatibility table](https://github.com/SonarSource/sonar-plugin-api#compatibility)
is the source for selecting the development target's API; deployment must still
be tested independently.

## Executed checks
`mvn -B -ntp -Dmaven.repo.local=/maven/repository verify` was executed in the
pinned build container with source mounted at /workspace and a separate user-owned
Maven cache. Result: **BUILD SUCCESS; 6 tests, 0 failures, 0 errors, 0 skipped**.

Tests exercise registration/profile and sensor behavior, including a real original
GDScript fixture whose expected issue is on line 2; threshold boundaries, Unicode,
CRLF, empty input, inactive rule, invalid configuration and file-type filtering.

Generated artifact: `target/sonar-gdscript-plugin-0.1.0-SNAPSHOT.jar`.
Inspected manifest includes `Plugin-Key: gdscript`,
`Plugin-Class: org.orebron.gdscript.GDScriptPlugin`, `Sonar-Version: 13.8`,
`Jre-Min-Version: 21` and `Plugin-RequiredForLanguages: gdscript`.
The build does not bundle the provided Sonar API as a dependency JAR.

The local Markdown-link checker and actionlint validation of the GitHub Actions
workflow also passed. CI results must be read from the actual workflow run rather
than inferred from these local checks.

## Not verified
- Server loading/startup or scanner download on Community Build 26.7.
- End-to-end report upload, Compute Engine processing or UI finding display.
- AST parsing, Godot semantic correctness, security analysis, metrics or coverage.
- Godot editor/LSP/gdlint integration; no game was executed.

The existing working SonarQube was not modified or restarted by this bootstrap.
The [roadmap](roadmap.md) and [development guide](development.md) define the
isolated end-to-end gate needed before deployment or a compatibility claim.
