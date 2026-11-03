# Rule specification

## Foundation: line-length

A readability/maintainability check for lines exceeding a configured length.
This intentionally line-based rule proves native registration/profile/sensor
integration; it does not parse GDScript or identify vulnerabilities.

Identifiers:
- Plugin/language: gdscript
- Repository: gdscript
- Rule: line-length
- Property: sonar.gdscript.maxLineLength; default 120, positive integer.

Code and fixtures define exact behavior. Keep this document synchronized before
release if bootstrap naming differs.

Expected boundaries:
- At/below limit: no finding; above limit: finding on that source line.
- Inactive rule: no findings.
- Invalid threshold: explicit tested failure behavior, not a false clean scan.
- No exemptions for comments/strings until a parser supports them.
- Character counting uses Unicode code points; tabs count as one character,
  regardless of editor tab width. Surrogate-pair Unicode and CRLF are tested.
- A non-positive, non-integer or overflowing threshold raises an explicit error.
- Only MAIN GDScript inputs are checked; TEST inputs and other languages are skipped.

## Code smells

Each rule is one `Check` class in `org.orebron.gdscript.checks`, registered in
`Checks.ALL`; the rules definition, the "GDScript way" profile (all rules
active) and the sensor read that list. Limits are integer rule parameters named
`max`; a non-positive or non-integer value fails the analysis, as for
line-length. Tree rules report nothing on files the parser could not read
(`LEXICAL_ONLY`); they are counted by `gdscript_incomplete_files` instead.
Defaults follow SonarSource's rules for other languages.

| Rule | Severity | Default `max` | Finding |
|---|---|---|---|
| `function-complexity` | Major | 10 | A function or inline accessor whose cyclomatic complexity (metrics plan definition) is above `max`. Lambdas count on their own and do not add to the function around them. Reported on the function name. |
| `function-length` | Major | 75 | A function or inline accessor with more lines of code than `max`, from its `func` line to its last statement; comment and blank lines do not count. Reported on the function name. |
| `too-many-parameters` | Major | 7 | A function or inline accessor with more parameters than `max`; a variadic `...args` counts as one. Lambdas are not checked. Reported on the function name. |
| `nesting-depth` | Major | 3 | `if`, `elif`, `else`, `for`, `while` and match branches each add a level; each function, accessor, lambda and class body starts at 0. The outermost `if`, `for` or `while` beyond `max` is reported, not the statements nested in it. A `match` beyond the limit is only reported through statements inside it. |
| `task-comment` | Info | — | A comment containing an uppercase `TODO` or `FIXME` tag as a whole word, as the Godot editor highlights them; strings, lowercase tags and longer words such as `TODOS` are ignored. Reported on the comment. |

The fixtures in `ChecksTest` cover each rule at and just above its limit, and
the sensor tests cover the issue location and an invalid parameter.

## Future rules

Every parser-backed rule needs a stable ID, rationale, severity, location mapping,
positive/negative fixtures, suppression semantics and false-positive examples.
Scene/signal/dynamic-reference rules require project context, not grep heuristics.
Rules that need scopes or types (unused variables and parameters, the security
plan's L2 rules) wait for the semantic model.
