# Rule fixtures

`rules/` is a small GDScript project with one file per rule, named after the rule
key. A `# Noncompliant` comment marks a line the rule must report at its default
limit; `# Noncompliant@+1` marks the next line and `# Noncompliant 2` counts two
issues on one line. Every other line, including the near misses next to each
case, must stay clean for that rule. `nosonar.gd` holds findings on `# NOSONAR`
lines, which the scanner must drop.

The same files are checked twice:

- `RuleFixturesTest` runs every rule on its fixture during `mvn verify`, and
  fails when a rule has no fixture or reports other lines.
- On a server, scan the folder as its own project and compare the published
  issues with the markers:

```sh
sonar-scanner -Dsonar.projectKey=gdscript-rule-fixtures -Dsonar.sources=its/rules \
  -Dsonar.inclusions='**/*.gd' -Dsonar.scm.disabled=true -Dsonar.qualitygate.wait=true
SONAR_TOKEN=... python3 scripts/verify_rule_fixtures.py --host http://localhost:9000 \
  --project gdscript-rule-fixtures
```

The script reads the project's quality profile and expects nothing from rules
that are not active in it, such as `too-many-returns` in "GDScript way".
