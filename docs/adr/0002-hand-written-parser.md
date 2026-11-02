# ADR 0002: hand-written Java lexer and parser

Status: accepted on 2026-11-02 (metrics plan milestone M0.5).

## Context
The [metrics plan](../metrics-plan.md) and the [security plan](../security-plan.md)
need GDScript tokens and a syntax tree on the scanner, under JRE 21, without
executing project code ([SECURITY.md](../../SECURITY.md)). ADR 0001 left the
parser backend open.

## Decision
Write the lexer and parser by hand in Java, with no runtime dependency, and keep
them free of Sonar types. The contracts below are binding for M1 and M3.

Lexer contract:
- Lossless: tokens are ordered, never overlap, and cover every non-whitespace
  character, comments and `\` continuations included.
- A `NEWLINE` token ends every physical line that has code, at any bracket
  depth. It carries the indentation of the next code line, and every token
  carries the indentation of its own line. Blank and comment-only lines carry
  no indentation.
- Tab/space mixing is an error at bracket depth 0. Inside brackets it is not
  checked, so a mixed multi-line lambda body is accepted (known limit).
- `&"…"` and `^"…"` are string tokens; `$Path/To/Node`, `$"…"` and
  `%Unique` are node-path tokens. `%` starts a node path only where an operand
  may start; after an operand it is modulo.

Parser contract:
- A parse succeeds only if every token is consumed; there is no recovery mode.
- Newlines are ignored inside `()`, `[]` and `{}`, except in multi-line lambda
  bodies. A lambda body's indentation is compared with the indentation of the
  line that holds `func`.
- One nesting counter covers blocks and expressions, with a limit of 256.
  Measured: 255 nested parentheses in a member initializer parse, 256 fail.

## Evidence
A throwaway prototype (692 lines, compiled with `javac --release 21`, not
kept) implemented both contracts. It was run on an original syntax corpus in
`gdscript-lexer/src/test/resources/fixtures/syntax/`, checked with
`godot --headless --check-only` (Godot 4.7.2.stable.official.ed1daf0bf):

| Corpus | Files | Godot | Prototype |
|---|---|---|---|
| `valid/` | 7 | all accepted | all parsed; class, function and lambda counts match a manual count |
| `invalid/` | 4 | all rejected | all rejected, at the same line |
| `engine-rejected/` | 1 | rejected (nested typed collection) | parsed: well-formed syntax, rejected by Godot's analyzer |

Mutation check: deleting each `:` or `)` in the valid files gave 227 mutants.
- The prototype rejected 177; Godot rejected all 177 as well.
- The prototype accepted 50. Godot accepted 47; it rejected the other 3 only
  for semantic reasons (type inference, a missing preload path).
- No syntax disagreement was found in either direction.

Performance (Apple M5, OpenJDK 27 running `--release 21` bytecode,
`-Xmx256m`):
- Input: one fixed 12-line function template repeated, so sizes are
  deterministic; the 1k-line file is accepted by Godot.
- Measurement: median of 10 runs after 5 warm-up runs in one JVM.

| Lines | Median |
|---|---|
| 1,000 | 8.3 ms |
| 10,000 | 32.4 ms |
| 100,000 | 270.8 ms |

Each 10× step cost 3.9× and 8.4×, within the metrics plan's 15× budget, which
stays as is.

Godot facts the harness must respect:
- `--check-only` exits with 0 even when the script has errors. Tooling must
  read the `SCRIPT ERROR` output.
- It reports analyzer errors (type inference, missing preload paths) as parse
  errors. "Accepted by Godot" therefore means syntax and analysis both pass.
- Preload and `extends` paths in the corpus assume all `valid/` files sit in
  one project root.

## Alternatives
- ANTLR: indentation needs a custom lexer anyway, and the parser must already
  switch newline handling inside lambdas; the grammar would add a shaded
  dependency without removing that code. Not prototyped.
- tree-sitter-gdscript via jtreesitter: the upstream `pom.xml` sets
  `maven.compiler.release` 23 (checked 2026-11-01), above the plugin's JRE 21,
  and needs native libraries per scanner platform. Not prototyped.
- gdtoolkit (Python): needs a Python runtime on every scanner. Version 4.5.0
  stays a dev-time oracle for `gdradon` in M3 only; it is not installed in the
  environment used for this ADR.
- Godot `--check-only`: validates but gives no tree or metrics, and needs an
  engine binary. It is a dev-time oracle only.

## Recorded versions
- Syntax matrix pinned to Godot 4.7.2.stable.official.ed1daf0bf.
- Parser depth limit: 256.
- Oracles: Godot 4.7.2 (now); gdtoolkit 4.5.0 (M3); tokei is not installed, so
  its version is recorded when M1 first uses it.

## Consequences
- Grammar maintenance is ours: every Godot minor release needs a syntax-matrix
  check against the new version before the pin moves.
- The corpus becomes the M1/M3 fixture set; the throwaway prototype is not a
  starting point for production code.
- Per-file memory is not measured yet; M1 covers it.
