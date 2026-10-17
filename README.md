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
- One intentionally simple line-length rule to prove the plugin/sensor path.
- Unit and sensor-fixture tests, Maven packaging, and documentation checks.
- Research, architecture decisions and an acceptance-driven roadmap.

The initial rule is **line-based**, not an AST parser. A passing analysis is not
proof of valid GDScript, correct gameplay or absence of security vulnerabilities.
There is no Godot process execution, lint-tool execution, syntax highlighting,
code-duplication engine, coverage collection or claimed GDScript metrics yet.

## Compatibility

| Product | Supported from | Plugin API |
|---|---|---|
| SonarQube Server | **2026.1 LTA**, including later releases such as 2026.5 LTA | 13.4.3 and later |
| SonarQube Community Build | 26.1 | 13.4.3 and later |
| Scanner/analysis JRE | Java 17 (2026.1–2026.3 scanners may still run it) | — |
| SonarQube Cloud | not supported: it does not accept third-party plugins | — |

The plugin is compiled against Plugin API 13.4.3.4290 with Java 17 bytecode and
declares `Sonar-Version: 13.4.3`. CI runs the tests at that floor on JDK 17 and
against the 2026.5 LTA API (14.0.0.4498) on JDK 21. These are build-level checks,
not an end-to-end certification: an isolated server-plus-scanner smoke test is
still required before a release. See the
[support baseline ADR](docs/adr/0002-support-baseline-2026-1-lta.md).

## Build and test

Prerequisites: JDK 17 or newer and Maven 3.9.x. Exact dependency versions are
pinned in [pom.xml](pom.xml); do not copy obsolete Java/API requirements from
old tutorials.

```sh
mvn --batch-mode --no-transfer-progress verify
python3 scripts/check_docs.py
```

The build creates a development plugin JAR under `target/`. Build success and
sensor-fixture tests are not an end-to-end compatibility certification.

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
- [Local verification and remaining gaps](docs/verification.md)
- [Development and validation](docs/development.md)
- [ADR: native language plugin](docs/adr/0001-native-language-plugin.md)
- [ADR: support baseline 2026.1 LTA](docs/adr/0002-support-baseline-2026-1-lta.md)
- [Contributing](CONTRIBUTING.md)
- [Security boundaries](SECURITY.md)

## Licensing

This is an original implementation. No source from the old LGPL-licensed
`darkiyy/sonar-gdscript` plugin has been copied. A distribution license has not
been selected yet; do not assume this repository is MIT/Apache-licensed or
publish a reusable release before that decision. Dependencies retain their own
licenses. See [research](docs/research.md) for third-party references.
