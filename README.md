# sonar-gdscript

An independent GDScript analyzer plugin for SonarQube, built for Godot projects.

**Status: early native-plugin foundation, not a production-ready GDScript analyzer.**
This is not a replacement for SonarScanner and is not an official SonarSource or Godot product.

## What this repository is for

SonarQube stores/configures the plugin. During analysis, the existing SonarScanner
retrieves its scanner-side components, executes the sensor next to the source,
and sends the analysis report back to SonarQube. The server processes that report
and evaluates the quality gate. It does not receive a raw project and run our
source analyzer as a server-side job.

```text
Godot source (.gd)
  -> existing SonarScanner
     -> our scanner-side GDScript sensor / checks
  -> SonarQube analysis report
  -> server processing, quality gate, UI
```

## Initial scope

- Native `.gd` language registration, a rule repository and a default profile.
- Six code-smell rules: line length, function complexity, function length,
  parameter count, nesting depth and TODO/FIXME comments ([rules](docs/rules.md)).
- A Sonar-free GDScript lexer module and lexer-based size metrics (`ncloc`,
  `ncloc_data`, `comment_lines`) with an incomplete-file metric and strict mode
  ([metrics plan](docs/metrics-plan.md) M1). They are unit-tested only, not yet
  verified on a SonarQube server.
- A Sonar-free parser module that must consume every token, and parser-based
  `classes`, `functions` and cyclomatic `complexity` (metrics plan M3), with the
  same unit-test-only status.
- Syntax highlighting, duplication tokens (metrics plan M2) and `# NOSONAR`
  suppression, all from the lexer's tokens; unit-tested only.
- Unit and sensor-fixture tests, Maven packaging, and documentation checks.
- Research, architecture decisions and an acceptance-driven roadmap.

Line length and TODO/FIXME use lines and comment tokens; the other rules and the
metrics come from the parser tree, with no scopes or types yet. A passing
analysis is not proof of valid GDScript, correct gameplay or absence of
security vulnerabilities. There is no Godot process execution, lint-tool
execution, type checking, cognitive complexity, coverage collection or security
analysis yet.

## Build and test

Prerequisites: JDK 21 and Maven 3.9.x. Exact dependency versions are pinned in
[pom.xml](pom.xml); do not copy obsolete Java/API requirements from old tutorials.

```sh
mvn --batch-mode --no-transfer-progress verify
python3 scripts/check_docs.py
```

The build has three modules: `gdscript-lexer` and `gdscript-parser` (no Sonar
dependency), and `sonar-gdscript-plugin`, which bundles both and creates the
development plugin JAR under `sonar-gdscript-plugin/target/`. Build success and
sensor-fixture tests are not an end-to-end compatibility certification. The
current development target is Community Build **26.7.0.124771**; an isolated
server-plus-scanner smoke test is still required before a release.

## Installation boundary

**Do not install the development JAR into an existing production server.**
Use an isolated matching SonarQube instance and the procedure in
[development and validation](docs/development.md). No source code, credentials,
private project reports, engine binaries or generated caches should be committed.

## Read next

- [Research and sources](docs/research.md)
- [Architecture](docs/architecture.md)
- [Rule scope and specification](docs/rules.md)
- [Roadmap and acceptance criteria](docs/roadmap.md)
- [Metrics plan](docs/metrics-plan.md) and [security analysis plan](docs/security-plan.md)
- [Local verification and remaining gaps](docs/verification.md)
- [Development and validation](docs/development.md)
- [ADR: native language plugin](docs/adr/0001-native-language-plugin.md)
- [ADR: hand-written lexer and parser](docs/adr/0002-hand-written-parser.md)
- [Contributing](CONTRIBUTING.md)
- [Security boundaries](SECURITY.md)

## Licensing

This is an original implementation. No source from the old LGPL-licensed
`darkiyy/sonar-gdscript` plugin has been copied. A distribution license has not
been selected yet; do not assume this repository is MIT/Apache-licensed or
publish a reusable release before that decision. Dependencies retain their own
licenses. See [research](docs/research.md) for third-party references.
