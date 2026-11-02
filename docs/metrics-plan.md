# Metrics analysis plan

Status: **M1 and M3 implemented and unit-tested on 2026-11-02; their gates are
not passed**, because the M0 harness was deferred (decision 10) and the pilot,
tokei and gdradon comparisons have not run. M2 and M4 are not implemented.
This refines roadmap Phase 2 ("measured NCLOC/complexity") and Phase 3
(complexity-based rules).

Revised on 2026-11-01 after three rounds of independent review (Codex CLI
0.166). One reviewer recommendation was not adopted; see decision 4.

## Goal and non-goals

Goal: publish trustworthy per-file GDScript measures to SonarQube so that size,
comment density, complexity and duplication are visible and usable in quality
gates, with every published number backed by a written definition and tests.

Non-goals for this plan: coverage import, executable-lines data, highlighting,
symbol tables, semantic/type analysis, project context (scenes, autoloads),
and complexity-threshold rules (those belong to roadmap Phase 3).

## Current state

- `GDScriptSensor.execute` returns immediately when `gdscript:line-length` is
  inactive. Metrics must be published regardless of rule activation, so this
  early return moves into the line-length check.
- `sonar.gdscript.maxLineLength` is therefore validated only when the rule is
  active.
- An `IOException` while reading any file becomes an `IllegalStateException`
  that ends the whole analysis.
- The sensor streams lines through a `BufferedReader`; there is no tokenizer.
- `GDScriptSensorTest.reportsActualOffendingLine` asserts that no measures are
  saved; that assertion changes when metrics land.

## Sonar API surface (checked against sonar-plugin-api 13.8.0.4399)

- Measures: `context.<Integer>newMeasure().on(file).forMetric(CoreMetrics.X).withValue(v).save()`.
- Per-line code data: `FileLinesContextFactory` (constructor-injected into the
  sensor) → `createFor(file).setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1)` → `save()`.
- Duplication: `context.newCpdTokens().onFile(file).addToken(range, image).save()`.
- Per-file failures: `context.newAnalysisError().onFile(file).at(pointer).message(...).save()`.
  Not every client surfaces these, so they are not the only failure signal.
- Custom metrics: a `Metrics` extension declares them; a Compute Engine
  `MeasureComputer` aggregates file values to directories and the project.
  `addMeasure` throws `UnsupportedOperationException` when the component
  already has a measure for that metric, so the computer must not write to
  files the scanner already measured. Whether a plugin metric can be used in a
  quality-gate condition on the target server is unverified.
- `COMMENT_LINES_DATA` is deprecated and will not be published.
- Computed elsewhere, not by us: `lines` (scanner file metadata),
  `comment_lines_density`, `duplicated_*` (from CPD tokens) and all
  directory/project aggregates of core metrics (server).
- Test fixtures (`sensor-test-fixtures` 13.4.0.3968) provide `measure(key, metric)`,
  `cpdTokens(key)` and `allAnalysisErrors()`; there is no `FileLinesContext`
  helper, so tests use a small in-test fake factory.

## Source positions

- Lines end at `\r\n`, `\n` or a lone `\r`, matching Sonar's own split, so
  measures and `ncloc_data` land on the lines Sonar displays.
- Columns are UTF-16 offsets (what `newRange` expects); the line-length rule
  keeps counting code points.
- Token ranges are start-inclusive and end-exclusive. A multi-line token ends
  on its last line. Example: `"""a` starting at line 3, column 4, closed by
  `bc"""` on line 4, has the range (3, 4) → (4, 5).

## Metric definitions

All metrics are computed only for `InputFile.Type.MAIN` GDScript files. A file
whose outcome is `ANALYZED` (see "Failure behavior") publishes every metric
implemented at the current milestone, including explicit zeros (an empty file
publishes `ncloc` = 0 and, from M3, `functions` = 0, and so on).

Examples in this section are abbreviated. Their fixtures must be complete,
engine-valid scripts that declare every identifier they use.

### Size and comments

| Metric | Definition |
|---|---|
| `ncloc` | Physical lines that contain at least one token other than a comment, newline, indentation or explicit `\` continuation. Every line spanned by a multi-line string token counts, including blank lines inside it. A standalone string statement is code (decision 1). |
| `ncloc_data` | `1` for exactly the lines counted by `ncloc`. |
| `comment_lines` | Lines containing a `#` or `##` comment whose body, after stripping `#` characters and whitespace, contains at least one letter or digit. Trailing comments count (a line can be both code and comment). Commented-out code counts. `#region` / `#endregion` markers count under the same rule (decision 2). |

### Structure

| Metric | Definition |
|---|---|
| `classes` | 1 for the script itself (every `.gd` file is a class) + 1 per inner `class Name:` declaration, abstract or not. |
| `functions` | Named callables with a body: `func` declarations at any class level (including `static` and `_static_init`) and inline `set`/`get` accessor bodies. Excludes lambdas, delegated accessors (`get = getter`) and bodiless `@abstract` declarations. |

### Cyclomatic complexity (`complexity`)

Increments are counted on parser node kinds, never on raw tokens, so the `if`
inside a conditional expression is not also counted as an `if` statement.

- +1 per function-like body: `func` with a body, lambda, inline accessor body.
- +1 per `if` statement, per `elif`, per `while`, per `for`.
- +1 per conditional expression `a if c else b`.
- +1 per `match` branch, except an unguarded catch-all branch. A catch-all
  branch is one whose pattern list contains a wildcard `_` or a bare binding
  `var name`; a guarded catch-all counts +1. A branch counts once however many
  patterns it lists, and its `when` guard adds nothing beyond that +1.
- +1 per binary `and`, `&&`, `or`, `||` node.
- `else`, `not`, `!`, `await`, `break`, `continue` add nothing.

Ownership: each increment belongs to the innermost function-like body that
contains it; lambdas are their own bodies. Increments in member initializers
outside any body belong to the file. The file measure is the sum of all bodies
plus file-level increments, each counted once.

| GDScript | Complexity |
|---|---|
| `func f(): pass` | 1 |
| `func f(x):` with `if x and y:` … `elif z:` … `else:` … | 1 + 1 (`if`) + 1 (`and`) + 1 (`elif`) = 4 |
| `var v = a if c else b` (member) | file +1 |
| `match x:` with branches `1, 2:` / `[var a, ..]:` / `var o when o > 0:` / `_:` | 1 + 1 + 1 + 0 = 3, added to the enclosing body |
| `var cb = func(): return a or b` inside a function | lambda body 1 + 1 (`or`) = 2, separate from the enclosing body |

### Cognitive complexity (`cognitive_complexity`)

A GDScript adaptation of the SonarSource Cognitive Complexity whitepaper, using
SonarJava's `CognitiveComplexityVisitor` as the reference for ambiguous cases.

- Structural +1 plus the current nesting level: `if` statement, conditional
  expression, `match` (once per statement), `for`, `while`.
- Hybrid +1 with no nesting penalty: `elif`, `else` of an `if` statement.
- Nesting level rises inside the bodies of `if`/`elif`/`else`, `match` branches,
  `for`, `while` and lambdas. In a conditional expression `a if c else b`, the
  condition `c` is scored at the current level and both results `a` and `b`
  at the current level + 1.
- `when` guards add no structural increment; boolean operators inside them
  follow the boolean rule.
- Boolean rule: +1 per sequence of like operators. `and` and `&&` are the same
  operator, as are `or` and `||`. Parentheses and negation do not break a
  sequence: `a and b or c` → +2; `a and not b and c` → +1;
  `(a and b) and c` → +1.
- Ownership: lambda increments fold into the enclosing named function, with
  nesting, as the whitepaper does for nested functions. Inner-class methods are
  independent owners. Member initializers are scored at file level with
  nesting 0; a lambda inside a member initializer is owned by the file and its
  body starts at nesting 1. The file measure counts each increment once.
- Documented deviations: no recursion increment (needs call resolution); no
  `break`/`continue` increments (GDScript has no labels).

Example: the function below scores 8. `for` +1; nested `if` +2 and its `and`
sequence +1; `elif` +1; `else` +1; the conditional expression inside the lambda
is at nesting 1, so +2.

```gdscript
func f(items: Array, ok: bool, other: bool, c: bool, x: int, y: int) -> void:
	for item in items:
		if item and ok:
			pass
		elif other:
			pass
		else:
			pass
	var g = func(): return x if c else y
```

More examples, each scored on its own:

| GDScript | Cognitive |
|---|---|
| `var v = (a if c else b) if d else e` (member) | outer conditional +1; inner conditional in a result at nesting 1, +2; file total 3 |
| `var cb = func():` whose body holds an `if x:` statement (member) | `if` at nesting 1, +2, owned by the file |

### Duplication (CPD tokens)

- Tokens: all lexer tokens except comments, newlines, indentation and explicit
  `\` continuation. Line grouping is kept by Sonar's CPD engine; indentation is
  not, so blocks that differ only by indentation can match. This is accepted and
  documented.
- Literals are normalized within their kind, not across kinds: one image each
  for String (regular, raw and triple-quoted), StringName `&"..."`,
  NodePath `^"..."` and numbers. `true`, `false`, `null`, identifiers, keywords
  and `$`/`%` node shorthands stay verbatim.
- Thresholds: SonarQube documents `sonar.cpd.gdscript.minimumTokens` (default
  100) and `sonar.cpd.gdscript.minimumLines` (default 10) as analysis parameters.
  They are not constants in the plugin API, so the harness must confirm both.

Deferred: `statements` and `executable_lines_data` (decision 9).

## Architecture

Keep the analysis engine free of Sonar types so it is unit-testable and
replaceable, as already required by [architecture](architecture.md).

- `org.orebron.gdscript.lexer`: `GDScriptLexer` → immutable `Token` list
  (kind, text, range as defined above). It handles the string literal forms of
  the GDScript reference (regular, triple-quoted, raw, triple-quoted raw,
  `&"..."`, `^"..."`), `#`/`##` comments, explicit `\` continuation, implicit
  continuation inside `()`/`[]`/`{}`, and emits `NEWLINE`/`INDENT`/`DEDENT`.
  `$Path/To/Node` and `%UniqueNode` are `get_node` shorthands, not literals: the
  lexer emits a node-shorthand token whose text includes any `/` segments, and
  treats `%` as a shorthand only in operand position (otherwise modulo).
  Errors are a checked `LexException` with a location.
- `org.orebron.gdscript.parser` (M3): a parser over tokens producing a small
  tree (script → classes → functions, lambdas, accessors → blocks → statements →
  expressions). Contract:
  - Every supported production is listed in the syntax support matrix.
  - A parse succeeds only if every token is consumed by a known production.
    Expressions are parsed fully with a precedence parser, so no token range is
    skipped.
  - Anything else is a parser failure with a location. There is no recovery
    mode whose output feeds metrics.
  - Semicolon separators and one-line suites (`if x: return`) are parsed even
    though `statements` is deferred.
  - Recursion depth is bounded; exceeding it is a parser failure.
- `org.orebron.gdscript.metrics`: `FileMetrics` (NCLOC line set, comment line
  set, counts, complexity) computed from tokens and the tree.
- Sonar adapter: `GDScriptSensor` reads each file once via `InputFile.contents()`,
  then lexes, parses (from M3), computes metrics and runs checks. All results
  for the file (measures, `ncloc_data`, CPD tokens, issues, analysis errors)
  are staged in memory and published together; see "Cancellation and the
  publication boundary". The line-length check moves to its own class.
  Per-file data is released before the next file.

### Syntax support matrix (minimum before M3 ships)

Each entry needs a passing fixture, otherwise it is listed as unsupported.

- Literals: every string kind above; integers with `0x`, `0b` and `_`
  separators; floats with exponents and a leading `.`; node shorthands
  `$Path/To/Node`, quoted `$"Path With Spaces"`, `%UniqueNode`; `%` as modulo.
- Annotations with and without arguments, on their own line or inline
  (`@export_range(1, 100, 1, "or_greater")`, `@warning_ignore(...)`, `@tool`,
  `@icon(...)`, `@abstract`).
- Declarations: `class_name`, `extends` (class or path), `signal` with
  parameters, named and anonymous `enum`, `const`, `var` with type or `:=`,
  `static var`, inline `set`/`get` bodies, delegated `get = getter, set = setter`,
  inner `class X extends Y:`, `@abstract class` and bodiless `@abstract func`,
  `static func _static_init()`, variadic `...args`, typed collections
  (`Array[int]`, `Dictionary[String, int]`, `Dictionary[Vector2i, MyClass]`),
  return types.
- Statements: `if`/`elif`/`else`, `for` (including a typed loop variable),
  `while`, `match` with literal, constant, wildcard, binding, array, dictionary
  and open-ended `..` patterns, multiple patterns per branch, `when` guards,
  `break`, `continue`, `pass`, `return`, `breakpoint`, `assert`, `await`,
  one-line suites, `;` separators.
- Expressions: anonymous and named lambdas, multi-line lambdas inside call
  arguments, conditional expressions, `is`, `as`, `in`, `not in`, `not`/`!`,
  `and`/`&&`, `or`/`||`, `**`, bitwise operators, `preload`, `super`, `self`,
  dictionary literals in both `{"a": 1}` and `{a = 1}` form, trailing commas.

The matrix is pinned to one Godot version, recorded in ADR 0002, before any
fixture is required to pass engine validation. Nested typed collections such
as `Dictionary[String, Dictionary[String, int]]` are unsupported by Godot
(GDScript reference, "Typed dictionaries"); they are not in the matrix, and
one fixture documents the analyzer's behavior on them.

## Failure behavior

Completeness is relative to the analysis stages enabled at the current
milestone: reading and lexing from M1, parsing from M3. A stage that does not
exist yet is never a failure. Every MAIN file ends in exactly one outcome:

| Outcome | Cause | Published |
|---|---|---|
| `ANALYZED` | Every enabled stage succeeded | All metrics implemented at this milestone; `gdscript_incomplete_files` = 0 |
| `LEXICAL_ONLY` | Parser failure (M3 onward) | `ncloc`, `ncloc_data`, `comment_lines`, CPD tokens; `gdscript_incomplete_files` = 1; no `classes`, `functions`, `complexity`, `cognitive_complexity` |
| `FAILED` | Unreadable file or lexer failure | Only `gdscript_incomplete_files` = 1; no other measures, no CPD tokens |

`gdscript_incomplete_files` is a diagnostic measure: it is the one measure
published for every MAIN file whatever its outcome.

- Unreadable files no longer end the analysis: this deliberately replaces the
  current `IllegalStateException`. Text-based checks still run when the content
  could be read.
- These still end the analysis: invalid configuration, unexpected runtime
  exceptions (programming defects must not silently become missing numbers),
  and strict mode below.
- `sonar.gdscript.maxLineLength` keeps being validated only when the rule is
  active, because it has no effect otherwise; this is documented and tested.
- Logs: at most 20 failures are logged individually with location; the rest
  are summarized as a count. Each failure is also saved as an analysis error,
  which by itself neither fails a quality gate nor gives durable server-side
  visibility.
- Strict mode: `sonar.gdscript.failOnIncompleteAnalysis` (boolean, default
  `false`). When `true`, the sensor fails after processing all files if any
  file is not `ANALYZED`, listing up to 20 of them. Intended for CI pipelines
  that need trustworthy gates, and the fallback if the harness shows that
  plugin metrics cannot be used in quality-gate conditions.

### Incompleteness metric

- A `Metrics` extension declares `gdscript_incomplete_files` (INT, domain
  GDScript, higher is worse).
- The sensor writes the file-level value. A `MeasureComputer` lists it as its
  output metric and, on directory and project components only, adds the sum of
  `getChildrenMeasures`. It never writes to file components, where the scanner
  value already exists and a second `addMeasure` would throw.
- Children without the measure (non-GDScript files) are ignored. A component
  with no GDScript descendant gets no value.
- Harness acceptance, in every case: exact directory and project totals for a
  fixture project with known failures, including a directory with no GDScript
  file. Then exactly one of:
  - the target server accepts a quality-gate condition on
    `gdscript_incomplete_files`, and that condition fails the gate for the
    fixture project; or
  - the harness records that the server does not offer the metric in gate
    conditions, the docs say so, and strict mode is shown to fail the scanner
    run for the same fixture project.

### Cancellation and the publication boundary

- Sonar has no rollback for saved results, so the boundary is explicit: a
  file's results are staged in memory and published only after a final
  `isCancelled()` check. Once publication of a file starts, it completes; a
  cancellation that arrives during publication takes effect before the next
  file.
- Cancellation is checked before each file, periodically during lexing and
  parsing (at least every 10,000 tokens), and before publication. A cancelled
  file publishes nothing; earlier files keep what they published.

## Milestones and gates

- **M0 – integration harness (roadmap Phase 1):** a disposable server with a
  real scanner, proven with the existing line-length rule. Finished before
  substantial M1 work, not only before release.
- **M0.5 – ADR 0002:** lexer and parser contracts and the parser technology,
  backed by a throwaway prototype run against a syntax corpus. Written before
  the lexer is built so M1 does not prejudge the parser choice. It records the
  pinned Godot version for the syntax matrix, every evaluated parser or binding
  version, the oracle tool versions (tokei, gdtoolkit) and the parser depth
  limit.
- **M1 – lexer, size metrics and failure policy:** `ncloc`, `ncloc_data`,
  `comment_lines`, file outcomes, `gdscript_incomplete_files`, strict mode.
  Gate: lexer tests for every literal and continuation form; golden fixtures;
  an explained diff against tokei on a pilot project; in the harness, exact
  `ncloc`, `comment_lines` and derived `comment_lines_density` after a successful
  Compute Engine task; the incompleteness-metric acceptance in "Failure
  behavior"; and the line-data verification protocol below.
- **M2 – duplication:** CPD tokens. Gate: unit tests via `cpdTokens()`,
  including distinct token images for String, StringName and NodePath literals
  with the same text; in the harness, exact `duplicated_lines` and
  `duplicated_blocks` for a positive clone; no duplication for a near-clone in
  which a literal changes kind at least every 50 tokens, so no identical run
  reaches `minimumTokens`; no duplication below `minimumTokens` and below
  `minimumLines`; `sonar.cpd.exclusions` respected; TEST files not tokenized.
- **M3 – parser and structural metrics:** `classes`, `functions`, `complexity`.
  Gate: every syntax-matrix entry has a fixture; complete token consumption on
  all fixtures; zero parser failures on the pilot's valid code (validity
  confirmed with a pinned Godot `--headless --check-only`); an explained diff
  against gdradon.
- **M4 – cognitive complexity.** Gate: the whitepaper's examples translated to
  GDScript plus the boolean-sequence and lambda-ownership examples above.

Each milestone updates README scope, [rules](rules.md),
[architecture](architecture.md) and [verification](verification.md); docs never
list a metric before its gate passes.

### Line-data verification protocol (M1)

`ncloc_data` cannot be read through `api/measures/component`, and `new_lines`
does not use it: SonarQube's `NewSizeMeasuresStep` counts changed lines only.
`new_ncloc` is the size of `ncloc_data` ∩ new lines. New lines exist only for a
pull request, a period with a start date or a reference branch, and only when
SCM data is available (`NewLinesRepository`); otherwise the measure is absent.
Community Build analyzes a single branch, so pull requests and reference
branches are not available there. The harness therefore fixes:

- the pinned server and scanner versions, a Git checkout with full history so
  that blame works, and a "number of days" new-code definition, all recorded
  with each run;
- a fixture whose expected code lines C and non-code lines N together cover
  every line of the file;
- two independent runs, each on a freshly created project: analyze a baseline
  commit whose author and committer dates lie before the new-code window, then
  analyze one change commit dated inside it:
  1. editing every line in N while keeping it non-code (comment text changed,
     whitespace added to blank lines) must give `new_ncloc` = 0;
  2. editing every line in C while keeping it code must give `new_ncloc` = |C|;
- in each run, first assert that the set of lines SonarQube marks as new equals
  the intended set (for example through the per-line new-code flag of
  `api/sources/lines`), and that `new_ncloc` is present. An absent measure is a
  harness failure, never read as 0.

Together the runs show that `ncloc_data` contains no line of N and every line
of C. If the pinned scanner exposes per-line data directly (for example in a
retained scanner report), comparing it entry by entry is preferred and replaces
this protocol; the harness documents which surface it uses.

## Parser options for ADR 0002

| Option | For | Against |
|---|---|---|
| Hand-written Java lexer + parser (provisional choice) | No runtime dependency, runs on scanner JRE 21, full control over failure semantics | Grammar maintenance as Godot adds syntax |
| ANTLR grammar | Declarative grammar | Indentation needs a custom lexer anyway; extra shaded dependency |
| tree-sitter-gdscript via jtreesitter | Maintained community grammar | Upstream `pom.xml` sets `maven.compiler.release` 23 (checked 2026-11-01) while the plugin targets 21; per-platform native libraries on scanner machines. Pin evaluated versions in the ADR. |
| gdtoolkit (Python) subprocess | Existing parser and `gdradon` | Python runtime on every scanner, subprocess contract and versioning |
| Godot `--check-only` | Engine-accurate validation | No AST or metrics output; requires an engine binary; executes engine code |

## Test strategy

- Lexer: table-driven tests for every literal form (including the raw-string
  edge cases from the GDScript reference), `#` inside strings, quotes inside
  triple-quoted strings, `%` as modulo vs shorthand, `/` inside node shorthands
  vs division, both continuation kinds, CRLF and lone CR, BOM, tabs and spaces,
  Unicode identifiers, empty file, no trailing newline, and multi-line token
  ranges against the example in "Source positions".
- Golden fixtures: original `src/test/resources/fixtures/metrics/*.gd` files with
  hand-reviewed expected values (NCLOC lines, comment lines, counts, complexity
  per body). Never copy third-party project code into fixtures.
- Sensor tests (`SensorContextTester`, fake `FileLinesContextFactory`):
  - metrics published with the line-length rule inactive;
  - explicit zero values for an empty file;
  - mixed valid, lexer-failing and parser-failing files in one analysis, with
    the expected outcome, measures and analysis-error locations for each;
  - an `InputFile` whose `contents()` throws, and a `FAILED` file publishing
    only `gdscript_incomplete_files`;
  - cancellation between files and during lexing and parsing, with nothing
    published for the interrupted file;
  - strict mode on and off, both with the M1 stages and from M3;
  - `gdscript_incomplete_files` values.
- Real constructor injection of `FileLinesContextFactory` can only be proven by
  the harness, not by unit tests.
- Invariants and fuzzing: the lexer terminates on arbitrary input and throws
  only `LexException`; `ncloc ≤ lines`; `ncloc_data` has exactly `ncloc`
  entries, all within `1..lines`.
- Dev-time oracles only, never runtime dependencies: tokei (size and comments),
  gdtoolkit 4.5 `gdradon` (cyclomatic), pinned Godot `--headless --check-only`
  (fixture validity). Differences are documented, not silently accepted.
- Performance and memory. The thresholds below are provisional budgets, not
  proof of linearity; ADR 0002 records the generator, environment and
  measurement protocol, and justifies or recalibrates them:
  - inputs come from a deterministic generator (fixed seed, documented mix of
    declarations, control flow, expressions, strings and comments);
  - each size is measured as the median of at least 10 runs after at least 5
    warm-up runs in one JVM;
  - scaling at 1k, 10k and 100k lines: each 10× step in size may cost at most
    15× the median time;
  - adversarial inputs: nesting exactly at and one past the parser depth limit
    (default 256), a 1 MB single line, very token-dense lines, an unterminated
    triple-quoted string at the top of a large file;
  - a separate test run with `-Xmx256m` analyzes a 10 MB generated file; no
    file contents, tokens or trees are retained across files.

## Risks

- **License LOC:** in commercial SonarQube editions, published `ncloc` counts
  toward licensed lines of code. Community Build has no such limit. Document it.
- **Incomplete aggregates:** `FAILED` files contribute no NCLOC (to licensing
  totals or density denominators) and `LEXICAL_ONLY` files no complexity, so
  project totals can look smaller or healthier than reality. This is mitigated
  by `gdscript_incomplete_files` and strict mode, not eliminated.
- **Plugin metric in quality gates:** unverified on the target server. If the
  harness shows it cannot be used in a gate condition, strict mode is the only
  gate-level protection.
- **Syntax drift:** Godot minor releases add syntax (typed dictionaries in 4.4,
  variadic functions and `@abstract` in 4.5). The support matrix is pinned to a
  Godot version; complete token consumption turns unknown syntax into a
  visible parser failure instead of a silent miscount.
- **Definition disagreement:** numbers will differ from editor plugins, tokei
  and gdradon. The definitions above are the contract; deviations are
  documented.
- **Line mismatch:** if lexer line splitting differs from Sonar's, measures and
  `ncloc_data` land on the wrong lines. Covered by CR/CRLF tests and the
  harness line-data check.

## Decisions

1. **Standalone multi-line strings are code.** Godot parses them, and guessing
   whether they are meant as comments is unreliable. Blank lines inside them
   count as NCLOC.
2. **`#region` / `#endregion` lines count as comment lines.** They satisfy the
   significant-comment rule; a special case would be an extra exception to test
   and explain.
3. **`classes` counts the implicit script class plus inner classes.**
   `class_name` only registers a global name; every script is a class.
4. **`functions` is a declaration count: named callables with a body; lambdas
   are excluded.** *Diverges from the review*, which recommended counting every
   executable body. Lambdas are expressions rather than declarations, and Godot
   code creates many small ones for signal connections. This is a chosen
   convention, not a claim about API size (private functions and accessors
   count too). Consequence, documented for users: `complexity` adds +1 per
   lambda and includes file-level initializer increments, while `functions`
   counts neither. `complexity / functions` therefore overstates the average
   complexity of declared functions, and is undefined when `functions` is 0
   (for example a script with only members and lambdas).
5. **Cyclomatic `match` counting** is +1 per branch except unguarded catch-alls,
   with `_` and bare bindings treated alike; lambdas are their own +1 bodies.
6. **Partial publication** of lexical results for `LEXICAL_ONLY` files, made
   visible through `gdscript_incomplete_files`, with strict mode for CI.
7. **Parser technology:** hand-written Java provisionally, to be confirmed in
   ADR 0002 by a prototype on a syntax corpus.
8. **CPD literals are normalized within their kind**, never across kinds.
9. **`statements` and `executable_lines_data` stay deferred.** Statement parsing
   itself is still required for complete token consumption; executable-line
   data is designed together with coverage (roadmap Phase 4).
10. **Roadmap Phase 1 is finished before substantial M1 work**, so the
    reporting path is proven before the analyzer grows. *Changed on 2026-11-02 by
    the repository owner:* no SonarQube is available locally, so M1 was built
    and unit-tested first. Every server-side M1 check stays open until the
    harness exists.
