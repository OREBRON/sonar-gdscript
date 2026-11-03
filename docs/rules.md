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
`Checks.ALL`; the rules definition, the "GDScript way" profile (every rule
except `too-many-returns`) and the sensor read that list. Limits are integer
rule parameters (`max`, or `threshold` for `duplicate-string`); a non-positive
or non-integer value fails the analysis, as for line-length. Tree rules report
nothing on files the parser could not read (`LEXICAL_ONLY`); they are counted
by `gdscript_incomplete_files` instead.
Defaults follow SonarSource's rules for other languages.

| Rule | Severity | Default `max` | Finding |
|---|---|---|---|
| `function-complexity` | Major | 10 | A function or inline accessor whose cyclomatic complexity (metrics plan definition) is above `max`. Lambdas count on their own and do not add to the function around them. Reported on the function name. |
| `function-length` | Major | 75 | A function or inline accessor with more lines of code than `max`, from its `func` line to its last statement; comment and blank lines do not count. Reported on the function name. |
| `too-many-parameters` | Major | 7 | A function or inline accessor with more parameters than `max`; a variadic `...args` counts as one. Lambdas are not checked. Reported on the function name. |
| `nesting-depth` | Major | 3 | `if`, `elif`, `else`, `for`, `while` and match branches each add a level; each function, accessor, lambda and class body starts at 0. The outermost `if`, `for` or `while` beyond `max` is reported, not the statements nested in it. A `match` beyond the limit is only reported through statements inside it. |
| `task-comment` | Info | — | A comment containing an uppercase `TODO` or `FIXME` tag as a whole word, as the Godot editor highlights them; strings, lowercase tags and longer words such as `TODOS` are ignored. Reported on the comment. |
| `naming-convention` | Minor | — | A name against the [GDScript style guide](https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/gdscript_styleguide.html): `snake_case` functions, signals, variables, loop variables, match binds and parameters; `PascalCase` classes (`class`, `class_name`) and enums; `CONSTANT_CASE` enum members; `CONSTANT_CASE` or `PascalCase` constants (preloaded classes and scenes). Leading underscores are ignored. Godot 3 style callbacks such as `_on_Button_pressed` are reported. |
| `empty-block` | Major | — | A function or `if`/`elif`/`else`/`for`/`while` block whose body is only `pass`, with no comment in it or after the `pass`. Match branches, lambdas and accessors are left out. Virtual methods of base classes are reported until they get a comment. |
| `commented-code` | Major | — | Whole-line `#` comments that parse as a class member or statement and also look like code: a declaration or block header, an assignment, a call or subscript with no space before the bracket, an annotation, or a lone `pass`/`break`/`continue`/`return`. `##` documentation comments and trailing comments are not read; consecutive lines give one issue. |
| `duplicate-string` | Critical | 3 (`threshold`) | A regular or raw string of at least 5 characters written `threshold` times or more in a file. StringName and NodePath literals are left out. Reported on the first occurrence. |
| `collapsible-if` | Major | — | An `if` without `elif`/`else` whose only statement is another `if` without `elif`/`else`. Reported on the inner `if`. |
| `too-many-returns` | Major | 3 | A function or inline accessor with more `return` statements than `max`; returns in lambdas are the lambda's. Not in the default profile, since guard clauses are idiomatic GDScript. |
| `boolean-literal-comparison` | Minor | — | `==`/`!=` with `true` or `false` on either side. Comparisons with the result of a `get*` call, such as `Dictionary.get()` or `ConfigFile.get_value()`, are left out: they compare a Variant on purpose. |
| `identical-branches` | Major | — | A branch of an `if`/`elif`/`else` chain or of a `match` whose body equals an earlier branch's, comparing tokens and relative indentation. Single-line bodies are left out. Reported on the later branch. |

A comment containing `NOSONAR` drops the issues on its line; the plugin passes
the lines to the scanner, which filters the issues. A file that fails to
tokenize has no NOSONAR lines, so its line-length issues stay.

The fixtures in `ChecksTest` cover each rule at and just above its limit or with
compliant and noncompliant code, and the sensor tests cover the issue location
and an invalid parameter. Before the rules were added, samples of each rule's
findings on the demo projects and a private game project were read by hand; the
two false-positive patterns found (prose with a parenthesis taken for a call,
`get()` results compared with `true`) are excluded as described above.

## Future rules

Every parser-backed rule needs a stable ID, rationale, severity, location mapping,
positive/negative fixtures, suppression semantics and false-positive examples.
Scene/signal/dynamic-reference rules require project context, not grep heuristics.
Rules that need scopes or types (unused variables and parameters, the security
plan's L2 rules) wait for the semantic model.
