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
- The 33 accepted parser-error tests are semantic checks Godot's parser makes
  (for example assignment inside `if`); accepting them only affects metrics on
  code Godot would refuse to run.
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
and `GDScriptLanguage` reads it; the rescan with that JAR is still open.

The [roadmap](roadmap.md) and [development guide](development.md) define the
isolated end-to-end gate needed before deployment or a compatibility claim.
