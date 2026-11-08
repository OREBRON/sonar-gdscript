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
by `gdscript_incomplete_files` and reported by `parsing-failure` instead.
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
| `duplicate-string` | Critical | 3 (`threshold`) | A regular or raw string of at least 5 characters written `threshold` times or more in a file. StringName and NodePath literals are left out. Reported on the first occurrence; the message gives the count, never the literal, which may be a secret. |
| `collapsible-if` | Major | — | An `if` without `elif`/`else` whose only statement is another `if` without `elif`/`else`. Reported on the inner `if`. |
| `too-many-returns` | Major | 3 | A function or inline accessor with more `return` statements than `max`; returns in lambdas are the lambda's. Not in the default profile, since guard clauses are idiomatic GDScript. |
| `boolean-literal-comparison` | Minor | — | `==`/`!=` with `true` or `false` on either side. Comparisons with the result of a `get*` call, such as `Dictionary.get()` or `ConfigFile.get_value()`, are left out: they compare a Variant on purpose. |
| `identical-branches` | Major | — | A branch of an `if`/`elif`/`else` chain or of a `match` whose body equals an earlier branch's, comparing tokens and relative indentation. Single-line bodies are left out. Reported on the later branch. |

A comment containing `NOSONAR` drops the issues on its line; the plugin passes
the lines to the scanner, which filters the issues. A file that fails to
tokenize has no NOSONAR lines, so its line-length issues stay. The line where
the analysis of an incomplete file stopped is never a NOSONAR line, so a comment
there cannot drop the `parsing-failure` issue.

The fixtures in `ChecksTest` cover each rule at and just above its limit or with
compliant and noncompliant code, and the sensor tests cover the issue location
and an invalid parameter. Before the rules were added, samples of each rule's
findings on the demo projects and a private game project were read by hand; the
two false-positive patterns found (prose with a parenthesis taken for a call,
`get()` results compared with `true`) are excluded as described above.

## Godot warnings

Twelve rules report what Godot 4.7's GDScript warnings of the same name report,
without the engine's type information. A semantic pass (`Scopes`, `Symbols`)
resolves each name to the latest local declaration visible in an enclosing
block, else to a member of the enclosing or an outer class; `a.b` on another
object and Lua-style dictionary keys are not uses, `self.b` is, and a signal is
also used by name in `emit_signal`, `connect`, `disconnect`, `is_connected`,
`has_signal` and `Signal(self, ...)`. Like the editor, the rules stay silent on
lines that `@warning_ignore("name")` covers (its line through the end of the
next declaration's or statement's header) or that lie between
`@warning_ignore_start("name")` and `@warning_ignore_restore("name")`.

| Rule | Godot warning | Severity | Finding |
|---|---|---|---|
| `unused-variable` | `UNUSED_VARIABLE` | Minor | A local variable or match bind never read; names starting with `_` are left out. |
| `unused-local-constant` | `UNUSED_LOCAL_CONSTANT` | Minor | A local constant never used; `_` names are left out. |
| `unused-parameter` | `UNUSED_PARAMETER` | Major | A function or lambda parameter never used; `_` names and setter parameters are left out. |
| `unused-private-class-variable` | `UNUSED_PRIVATE_CLASS_VARIABLE` | Major | A class variable whose name starts with `_` and that its class never uses. |
| `unused-signal` | `UNUSED_SIGNAL` | Minor | A signal its class never emits, connects or refers to. |
| `shadowed-variable` | `SHADOWED_VARIABLE` | Major | A local, parameter, loop variable or bind with the name of a member of its own class. Base classes and engine names need type data and are not checked. |
| `unreachable-code` | `UNREACHABLE_CODE` | Major | The first statement of a block after a `return`, an `if` whose every branch including `else` returns, or a `match` with a catch-all branch whose every branch returns. Accessor bodies are left out, as in Godot. |
| `unreachable-pattern` | `UNREACHABLE_PATTERN` | Major | Match branches after an unguarded `_` or bare `var name` branch. |
| `standalone-expression` | `STANDALONE_EXPRESSION`, `STANDALONE_TERNARY` | Major | An expression statement that is not a call, an assignment, an `await` or a string used as a comment. |
| `empty-file` | `EMPTY_FILE` | Minor | A script with no code. |
| `get-node-without-onready` | `GET_NODE_DEFAULT_WITHOUT_ONREADY` | Major | A non-static class variable whose whole default value is `$Path`, `%Name`, `get_node(...)` or `self.get_node(...)`, optionally cast with `as`, without `@onready`. |
| `onready-with-export` | `ONREADY_WITH_EXPORT` | Major | A class variable with both `@onready` and an `@export` annotation. |

Godot's own tests are the reference: in the 357 GDScript tests of Godot 4.7.2
that pass and parse, the expected `.out` files list 49 of these warnings, and
the rules report exactly those 49 and nothing else. Godot's other warnings need
types (`UNSAFE_*`, `INTEGER_DIVISION`, `RETURN_VALUE_DISCARDED`), flow analysis
(`UNASSIGNED_VARIABLE`), base classes or engine names (`SHADOWED_*` beyond the
own class), or Unicode confusables data, and are not implemented.

## Security review rules

The security plan's level L1 review rules (S2), without types: each matches a
call or constant written on an engine singleton or class (`OS.execute`,
`HashingContext.HASH_MD5`), not after a `.`, in a file that declares no name
that would hide it. They are `VULNERABILITY` rules with a SECURITY impact
(Minor → LOW, Major → MEDIUM) whose descriptions ask the reviewer what to rule
out; "constant" below means a string literal or a constant the file declares.

| Rule | CWE / OWASP 2021 | Severity | Finding |
|---|---|---|---|
| `process-execution` | — | Minor | `OS.execute`, `OS.create_process`, `OS.execute_with_pipe`; silent where `shell-command` reports. |
| `shell-command` | 78 / A03 | Major | The same calls running a shell or interpreter (`sh`, `bash`, `cmd`, `powershell`, `python`, `node`, …, by name or path) in its command-string form (`-c`, `/C`, `-Command`, `-e`, …) with a non-constant command element in an array literal. |
| `shell-open-dynamic` | — | Minor | `OS.shell_open` with a non-constant argument. |
| `javascript-eval` | 95 / A03 | Major | `JavaScriptBridge.eval` with a non-constant argument. |
| `resource-pack-loading` | 829 / A08 | Major | `ProjectSettings.load_resource_pack`. |
| `object-decoding` | 502 / A08 | Major | `bytes_to_var_with_objects(...)` and `Marshalls.base64_to_variant(..., true)`; `get_var(true)` needs a typed receiver (L2). |
| `rpc-any-peer` | 862 / A01 | Major | A function annotated `@rpc("any_peer", ...)`, reported on its name. |
| `hardcoded-credential` | 798 / A07 | Major | A string of 6 or more characters without spaces assigned to a name with `password`, `passwd`, `secret`, `token`, `api key`, `private key` or `secret key` in it (`var`/`const`, assignment, dictionary key). Names ending in `id`, `url`, `path`, `name`, `setting` and the like, identifier-like values, paths, URLs and placeholders are left out. The message names the variable, never the value. |
| `weak-hash` | 328 / A02 | Major | `HashingContext.HASH_MD5`, `HashingContext.HASH_SHA1`. |
| `aes-ecb-reference` | 327 / A02 | Major | `AESContext.MODE_ECB_ENCRYPT`, `AESContext.MODE_ECB_DECRYPT`. |
| `tls-unsafe-options` | 295 / A07 | Major | `TLSOptions.client_unsafe(...)`. |

On the local Godot projects and the demos, every finding outside test code was
read: process starts and `shell_open` in editor add-ons, a downloadable-content
pack loader, multiplayer `any_peer` RPCs and a formatted `eval`. The only
credential finding outside tests was a setting name, now excluded. Test
fixtures with fake tokens are reported; marking the test folder with
`sonar.tests` keeps the sensor away from them. The confirmed rules (L2–L4,
taint) are not implemented.

## Analysis failures

| Rule | Type | Severity | Finding |
|---|---|---|---|
| `parsing-failure` | Code smell | Major | A file that could not be read, tokenized or parsed (`LEXICAL_ONLY` or `FAILED`), for example a syntax error, an expression nested past the parser's depth limit of 256, or more than 500,000 tokens in one file (the lexer's per-file bound, so a token-dense generated file cannot exhaust the scanner heap). The rules that need a syntax tree, every security rule among them, do not run on such a file. Reported on the line where the analysis stopped, or on the file when there is none; the message is the sanitized failure message of the analysis error. |

`gdscript_incomplete_files` counts the same files, but a measure without a
`new_` variant and an analysis error do not reach new-code quality-gate
conditions or pull-request decoration; an issue does. This follows SonarSource's
parser-failure rules for other languages (`python:ParsingError`, S2260). The
rule is in the default profile; strict mode (`sonar.gdscript.failOnIncompleteAnalysis`)
is unchanged and still off by default.

## Rule fixtures

[its/rules](../its/README.md) has one annotated GDScript file per rule.
`RuleFixturesTest` checks every rule against its file in each build, and
`scripts/verify_rule_fixtures.py` checks the issues a server publishes for the
same files, including `# NOSONAR` filtering.

## Future rules

Every parser-backed rule needs a stable ID, rationale, severity, location mapping,
positive/negative fixtures, suppression semantics and false-positive examples.
Scene/signal/dynamic-reference rules require project context, not grep heuristics.
Rules that need scopes or types (unused variables and parameters, the security
plan's L2 rules) wait for the semantic model.
