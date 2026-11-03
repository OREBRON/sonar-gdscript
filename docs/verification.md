# Bootstrap verification

Local verification on 2026-10-20; this is not a production compatibility report.

## Toolchain and pins
- JDK 21, Maven 3.9.9.
- Sonar Plugin API 13.8.0.4399, scope provided.
- Sonar packaging Maven plugin 1.25.1.3002.
- Sensor test fixtures 13.4.0.3968; JUnit 5.11.4.
- Build image: `maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e`.

The [official API compatibility table](https://github.com/SonarSource/sonar-plugin-api#compatibility)
is the source for selecting the development target's API; deployment must still
be tested independently.

## Executed checks
`mvn -B -ntp -Dmaven.repo.local=/maven/repository verify` was executed in the
pinned build container with source mounted at /workspace and a separate user-owned
Maven cache. Result: **BUILD SUCCESS; 6 tests, 0 failures, 0 errors, 0 skipped**.

Tests exercise registration/profile and sensor behavior, including a real original
GDScript fixture whose expected issue is on line 2; threshold boundaries, Unicode,
CRLF, empty input, inactive rule, invalid configuration and file-type filtering.

Generated artifact: `target/sonar-gdscript-plugin-0.1.0-SNAPSHOT.jar`.
Inspected manifest includes `Plugin-Key: gdscript`,
`Plugin-Class: org.orebron.gdscript.GDScriptPlugin`, `Sonar-Version: 13.8`,
`Jre-Min-Version: 21` and `Plugin-RequiredForLanguages: gdscript`.
The build does not bundle the provided Sonar API as a dependency JAR.

The local Markdown-link checker and actionlint validation of the GitHub Actions
workflow also passed. CI results must be read from the actual workflow run rather
than inferred from these local checks.

## Not verified
- Server loading/startup or scanner download on Community Build 26.7.
- End-to-end report upload, Compute Engine processing or UI finding display.
- AST parsing, Godot semantic correctness, security analysis, metrics or coverage.
- Godot editor/LSP/gdlint integration; no game was executed.

The existing working SonarQube was not modified or restarted by this bootstrap.

## Metrics M1 (local, 2026-11-02)
Roadmap Phase 1 was deferred because no SonarQube is available locally, so M1
was verified by unit tests only.

- Toolchain: Apache Maven 3.9.9 (archive download, SHA-512 checked) on
  OpenJDK 27 compiling `--release 21`, with a separate local repository. This is
  not the pinned bootstrap container.
- `mvn -B -ntp verify`: **BUILD SUCCESS; 41 tests, 0 failures** (lexer 23,
  sensor 12, incomplete-file computer 3, size metrics 2, registration 1).
- The plugin JAR bundles `META-INF/lib/gdscript-lexer-0.1.0-SNAPSHOT.jar`, listed
  under `Plugin-Dependencies`; the provided Sonar API and slf4j are not bundled.
- The lexer produces the same token counts as the ADR 0002 prototype on all
  eight corpus files it accepts.

Not verified for M1: constructor injection of `FileLinesContextFactory`, measure
display and `ncloc_data` on a server, `IncompleteFilesComputer` in the Compute
Engine, quality-gate use of `gdscript_incomplete_files`, the tokei comparison
and the performance and memory gates.

## Metrics M3 (local, 2026-11-02)
Same toolchain and limits as M1.

- `mvn -B -ntp verify`: **BUILD SUCCESS; 66 tests, 0 failures** (lexer 23,
  parser 17, sensor 13, complexity examples 7, incomplete-file computer 3, size
  metrics 2, registration 1).
- Every valid corpus file parses with the expected class, function, accessor and
  lambda counts; the parser-level invalid files fail at the same lines as the
  prototype; 255 nested parentheses parse and 256 fail.
- On the 227 ADR 0002 mutants, the production parser accepts exactly the
  prototype's 50, which Godot judged syntactically valid.
- All worked complexity examples of the metrics plan give the documented values.
- The plugin JAR bundles the lexer and parser JARs under `Plugin-Dependencies`.

Not verified for M3: the measures on a server, a real-project pilot with zero
parser failures, and the gdradon comparison (gdtoolkit is not installed).

### Public corpus check (2026-11-03)
A user scan reported `Expected end of line near ')'` across a project. The
same error reproduced on the official demo projects
(`godotengine/godot-demo-projects`, commit b761b4c, read-only, not committed):
3 of 461 `.gd` files failed, all on multi-line lambdas inside brackets that end
before `)` or `,` instead of a dedent. After the parser fix, all 461 files
parse, the 227 ADR 0002 mutants still give exactly the same 50 accepted files,
and `mvn verify` passes 69 tests.

### Godot test-suite conformance (2026-11-03)
Godot 4.7.2's own GDScript tests (`modules/gdscript/tests/scripts`, from a local
engine checkout, read-only) state each test's expected outcome in a `.out`
file. Lexing plus parsing was compared with it:

| Expected by Godot | Tests | Before | After the fixes |
|---|---|---|---|
| Syntax valid (`GDTEST_OK`, analyzer or runtime error) | 423 | 403 accepted | 423 accepted |
| `GDTEST_PARSER_ERROR` | 73 | 40 rejected | 40 rejected |

- The 20 false rejections came from 12 constructs: raw line breaks in regular
  strings, `$%"…"`, trailing-dot floats such as `123.`, continuations across
  comment lines, `match`, `when`, `PI`, `TAU`, `INF` and `NAN` as names,
  `name : = value`, Lua-style dictionaries with string keys, annotations before
  match branches, a match with only `pass`, repeated `;`, class-level strings,
  and an empty lambda body before a closer. `valid/godot_extras.gd` covers the
  engine-valid ones.
- The 33 accepted parser-error tests were checks Godot's parser makes beyond
  the grammar. 14 of them no longer pass (see "Stricter parsing" below).
- After the fixes: all 461 demo files, a private game project with its addons
  (501 files, read-only) and a 13-file plugin project parse; the 227 mutants
  still give exactly the same 50 accepted files; `mvn verify` passes 76 tests.

### First server scan (2026-11-03)
A proof-of-concept scan used Community Build 26.7.0.124771, SonarScanner CLI
8.3.0.16463 (scanner engine 13.1.3.3768), `sonar.inclusions=**/*.gd` and the
installed plugin. The scanner indexed the 501 `.gd` files but reported
"0 languages detected", so it never downloaded the plugin: no GDScript sensor
ran and no measures were saved. The engine's `DefaultLanguagesLoader` takes
suffixes only from `sonar.<language>.file.suffixes`, which the plugin did not
define. The plugin now defines `sonar.gdscript.file.suffixes` (default `.gd`)
and `GDScriptLanguage` reads it.

Rescan with that JAR (commit 3d951c1): "1 language detected in 501
preprocessed files", the GDScript sensor ran in 354 ms, the Compute Engine task
succeeded and the quality gate passed. The project measures on the server equal
a local recomputation with the same lexer, parser and metric classes:

| Measure | Server | Local |
|---|---|---|
| files | 501 | 501 |
| ncloc | 58,640 | 58,640 |
| comment_lines | 5,811 | 5,811 |
| classes | 709 | 709 |
| functions | 4,321 | 4,321 |
| complexity | 17,653 | 17,653 |
| gdscript_incomplete_files | 0 | 0 |
| open `gdscript:line-length` issues | 2,851 | 2,851 lines over 120 |

This also shows, on this server, that the scanner injects
`FileLinesContextFactory` and that `IncompleteFilesComputer` writes the
project value. Still not checked: `ncloc_data` line by line, quality-gate use of
`gdscript_incomplete_files`, and a parser or lexer failure seen on the server.

The [roadmap](roadmap.md) and [development guide](development.md) define the
isolated end-to-end gate needed before deployment or a compatibility claim.

## Highlighting, duplication tokens and NOSONAR (local, 2026-11-03)
The sensor runs through `SensorContextTester`, which validates every saved
range, on the 461 demo files and the private project (495 readable files):
no file fails, and 77,781 lines carry CPD tokens. The second server scan below
shows highlighting and duplication on a server; NOSONAR filtering is still
unchecked there, since the project has no NOSONAR comment.

## Stricter parsing (local, 2026-11-03)
The lexer and parser now also reject what Godot 4.7.2's tokenizer and parser
reject without a semantic model; each rule was confirmed with
`godot --headless --check-only`:

- Strings: escapes other than Godot's (`\a \b \f \n \r \t \v \' \" \\`,
  `\uXXXX`, `\UXXXXXX`, an escaped line break) outside raw strings, and
  invisible text direction controls.
- Numbers: adjacent `_` separators, `0x_`/`0b_`, `10._5`, a missing exponent,
  a second decimal point, a decimal point in hex or binary, and a letter right
  after a number, so `5.abs()` is an error while `123.e4` is a float.
- Node paths: a `/` after a path segment continues the path, spaces allowed,
  so `$A/23` and `$A / 2` are errors; `/=` still ends the path.
- `assert`, `preload`, `namespace`, `trait` and `yield` are keywords.
- Parameters: none after a rest parameter, no default on it, no mandatory
  parameter after an optional one; signal parameters have neither.
- `break`/`continue` outside a loop (a lambda body starts outside), a value
  returned from `_init`/`_static_init`, a non-static or parameterized
  `_static_init`, and a standalone lambda statement.
- Match patterns: a variable bind in a multi-pattern branch, `..` outside an
  array or dictionary pattern, `..` before the last element or as a value.

| Expected by Godot | Tests | Before | Now |
|---|---|---|---|
| Syntax valid | 423 | 423 accepted | 423 accepted |
| `GDTEST_PARSER_ERROR` | 73 | 40 rejected | 54 rejected |

The 19 still accepted need a semantic model or engine data: annotations (10:
unknown names and wrong targets 5, which depend on the Godot version;
placement and duplicates 3; `@warning_ignore_start` regions 2), name conflicts
in a scope (8) and an identifier visually similar to a keyword (1, Unicode
confusables data). All 12,132 `.gd` files still parse: the local Godot
projects outside build and cache folders plus the 461 demo files.

## Second server scan (2026-11-03)
The same server, scanner and scope as the first scan, with the plugin of
commit 1b7d7c6 (code-smell rules, highlighting, CPD tokens, stricter parsing)
on the private project, now 502 `.gd` files. "1 language detected", the
GDScript sensor ran in 545 ms and the Compute Engine task succeeded. Every
measure and every rule's open-issue count equals a local recomputation with
the same classes at the rules' default limits:

| Measure | Server | Local |
|---|---|---|
| files | 502 | 502 |
| ncloc | 58,764 | 58,764 |
| comment_lines | 5,839 | 5,839 |
| classes | 710 | 710 |
| functions | 4,329 | 4,329 |
| complexity | 17,687 | 17,687 |
| gdscript_incomplete_files | 0 | 0 |
| `gdscript:line-length` | 2,855 | 2,855 |
| `gdscript:function-complexity` | 317 | 317 |
| `gdscript:nesting-depth` | 103 | 103 |
| `gdscript:function-length` | 79 | 79 |
| `gdscript:too-many-parameters` | 3 | 3 |
| `gdscript:task-comment` | 0 | 0 |

- The new rules reached the built-in "GDScript way" profile after the plugin
  update without manual activation.
- Highlighting: `api/sources/lines` returns keyword, string and structured
  comment spans for the first file (`extends`, `const`, `preload`, `static`,
  `var`, `##` comments and string literals).
- Duplication: the CPD executor computed blocks for 466 files (36 had none);
  the server reports 116 `duplicated_blocks`, 3,507 `duplicated_lines` and
  4.8 % density. These values were not checked against an oracle.
- The quality gate failed and the scanner exited with 3 because
  `sonar.qualitygate.wait=true`; the failing condition was not read.
- Still not checked: NOSONAR filtering, the failing gate condition, and the
  duplication blocks one by one.

## Third server scan (2026-11-03)
The plugin of commit 4c5f04c (fourteen rules, `too-many-returns` not in the
default profile) on the same private project, with third-party `addons/**`
excluded as well: 343 `.gd` files. The sensor ran in 548 ms and the Compute
Engine task succeeded. Every measure and every rule's open-issue count equals
the local recomputation:

| Measure | Server | Local |
|---|---|---|
| files | 343 | 343 |
| ncloc | 41,482 | 41,482 |
| comment_lines | 2,467 | 2,467 |
| classes | 365 | 365 |
| functions | 2,911 | 2,911 |
| complexity | 11,982 | 11,982 |
| gdscript_incomplete_files | 0 | 0 |
| open issues | 3,590 | 3,590 |
| `gdscript:line-length` | 2,540 | 2,540 |
| `gdscript:duplicate-string` | 640 | 640 |
| `gdscript:function-complexity` | 223 | 223 |
| `gdscript:function-length` | 66 | 66 |
| `gdscript:nesting-depth` | 55 | 55 |
| `gdscript:boolean-literal-comparison` | 44 | 44 |
| `gdscript:naming-convention` | 11 | 11 |
| `gdscript:empty-block` | 5 | 5 |
| `gdscript:collapsible-if` | 3 | 3 |
| `gdscript:too-many-parameters` | 2 | 2 |
| `gdscript:identical-branches` | 1 | 1 |
| `gdscript:too-many-returns` (inactive) | 0 | 75 if activated |

- Duplication: 31 `duplicated_blocks`, 1,095 `duplicated_lines`, 2.3 %
  density, down from 116 blocks with the add-ons included.
- The quality gate failed on one condition, `new_violations` = 8 (threshold
  0); `new_duplicated_lines_density` = 1.6 % passed (threshold 3 %). Issues of
  the newly added rules on unchanged code did not count as new. Which 8 issues
  are new was not read.
- About two thirds of the issues are in `tests/`, which the scan treats as main
  code because `sonar.tests` is not set. NOSONAR filtering is still unchecked.
