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

## Future rules

Every parser-backed rule needs a stable ID, rationale, severity, location mapping,
positive/negative fixtures, suppression semantics and false-positive examples.
Scene/signal/dynamic-reference rules require project context, not grep heuristics.
