# Architecture

## Responsibilities
- Server-side registration: GDScript language, rule repository, profile, properties.
- Scanner-side sensor: select indexed .gd inputs, execute checks, create findings.
- Existing SonarScanner: obtain configuration/analyzers, execute sensors, upload report.
- SonarQube Compute Engine: process/persist report, calculate gates and expose results.

Source analysis runs next to the scanner, not as a server-side raw-project job.

## Foundation and target
The analysis engine is separate from the Sonar adapter, enforced by Maven modules:
`gdscript-lexer` and `gdscript-parser` have no Sonar dependency, and
`sonar-gdscript-plugin` bundles them. The sensor reads each file once, tokenizes
and parses it, stages metrics and line-length findings, and publishes them
together. The parser keeps only the constructs metrics need (`SyntaxKind`); it
does not validate Godot types or start Godot.
[ADR 0002](adr/0002-hand-written-parser.md) records the hand-written lexer and
parser decision and its evidence.

A Python parser requires a scanner-machine runtime and versioned subprocess
contract. A JVM parser avoids Python but requires grammar maintenance. Neither
should silently execute project code.

## Planned finding contract
Stable rule/engine ID, project-relative file path, source location with explicit
indexing, message, severity mapping and provenance. Validate paths; reject root
escape. Distinguish clean, unreadable, unsupported and failed analysis outcomes.

## Metrics and context
Publish only implemented/tested metrics: nonblank lines are not NCLOC because
comments/multiline strings matter. Duplication needs tokens; complexity needs
syntax/control-flow; coverage needs actual execution reports and line mapping.

Later project context can read project.godot, .tscn and .tres as data, resolving
UIDs, autoloads and signal connections. Do not infer unused methods/assets before
handling scene references, reflection and dynamic calls.

Respect scanner exclusions. Exclude third-party addons explicitly if desired;
do not globally hide all first-party addon code. Default scans never import or
execute games/editor scripts. Bound diagnostics and failure behavior.
