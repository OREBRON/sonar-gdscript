# Roadmap and acceptance criteria

## Phase 0: native foundation
- Register .gd, a stable rule repository and default profile.
- Implement one deterministic line-length rule and validated threshold.
- Test clean/violating input, location, rule activation and invalid config.
- Package JAR metadata; CI runs Maven and documentation verification.
- Clearly mark no AST parsing, semantic rules or language metrics yet.

Gate: real unit/sensor tests pass and a plugin JAR is produced. This is not
end-to-end server compatibility certification.

## Phase 1: isolated integration
- Pin image identities for the 2026.1 LTA floor (or Community Build 26.1/26.2,
  same Plugin API 13.4.3) and the current LTA; separate loopback ports and volumes.
- Install only into disposable instance; verify UP, language/rules/profile via API.
- Scan original positive and negative .gd fixtures with the actual SonarScanner.
- Verify exact CE task SUCCESS and expected file/rule/line; test rule deactivation.
- Retain real logs without secrets and clean up only the disposable resources.

Gate: repeatable real server-plus-scanner run; no fabricated API responses.

## Phase 2: parser-backed analysis
- Choose parser in a separate ADR using modern Godot 4 syntax fixtures.
- Cover strings/comments, annotations, typed dictionaries/arrays, lambdas,
  enums, setters/getters and every newly claimed syntax feature.
- Implement measured NCLOC/complexity and syntax-aware rules with negative tests.
- Make unsupported syntax and parser failures visible, bounded and reproducible.

Gate: syntax support matrix and authorized real-project read-only pilot.

## Phase 3: project context and useful rules
- Complexity, nesting, size, unused-argument rules where evidence supports them.
- Read scenes/resources/autoloads/signals as data; resolve paths and UIDs.
- Do not label methods/assets unused without dynamic-reference safeguards.
- Stable IDs, suppression policy, rationale and false-positive documentation.

## Phase 4: distribution
- Select project license and audit dependency provenance.
- Test declared server/scanner/JDK/Godot version combinations.
- Add release checksums, compatibility notes and reproducible packaging.
- Add coverage only from genuine test execution and validated line mapping.

No public release, delivery date or security certification is implied.
