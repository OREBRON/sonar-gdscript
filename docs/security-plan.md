# Security analysis plan

Status: **draft, not implemented.** The plugin performs no security analysis
today, and the [security policy](../SECURITY.md) statements stay true until a
gate below passes. This plan builds on the lexer, parser, failure policy and
integration harness defined in the [metrics plan](metrics-plan.md), and keeps
its rule that the analysis engine is free of Sonar types.

Revised on 2026-11-01 after two rounds of independent review (Codex CLI
0.166).

## Goal and non-goals

Goal: help Godot teams find security-sensitive GDScript code. The plugin
reports security issues in two classes, *confirmed* weaknesses and *review*
findings, and every active rule has a measured precision.

Non-goals:
- proof of security or any certification ("zero findings is not a security
  audit");
- anti-cheat and client tamper resistance: the player fully controls their own
  client;
- GDExtension, C#, shader, binary or exported-build analysis;
- runtime testing and dependency (addon) vulnerability scanning.

## Threat model

| Where GDScript runs | Attacker | Untrusted input that crosses a trust boundary |
|---|---|---|
| Game client | Other players; authors of shared saves, mods and downloaded content | RPC arguments, replicated state, network packets, files under `user://` or user-chosen paths, downloaded archives and packs |
| Dedicated or headless server | Every connected client | RPC arguments, replicated state, packets, HTTP and WebSocket payloads |
| Editor (`@tool`, `EditorPlugin`) | Authors of data opened in the editor (scenes, resources, imported assets) | File contents read by trusted editor code |
| Web export | The hosting page or other scripts in it | `JavaScriptBridge` callbacks and return values |

- A local player attacking their own single-player client is out of scope.
- An addon's own code is already trusted to run, so a dangerous API call inside
  it gives its author nothing new. The editor risk is lesser-trust *data*
  reaching trusted editor code.
- Everything shipped in an exported game can be extracted by players, so any
  secret embedded in GDScript is disclosed by design.

Untrusted sources, used by the rules and the taint model:
- parameters of RPCs callable by any peer: `@rpc("any_peer", ...)` and
  `rpc_config` with `any_peer`;
- parameters of `authority` RPCs, unless the authority is shown to be trusted
  (see "Taint model");
- properties replicated by `MultiplayerSynchronizer` from a non-server
  authority;
- `PacketPeer.get_var` / `get_packet` (including `ENetPacketPeer`,
  `WebSocketPeer`, `PacketPeerUDP`, `PacketPeerDTLS`, `PacketPeerStream`),
  `StreamPeer` reads;
- `HTTPRequest` response bodies (`request_completed` arguments) and
  `HTTPClient.read_response_body_chunk()`;
- values returned by `await` on a source signal;
- `FileAccess` reads from `user://` or non-constant paths; `ZIPReader` entry
  names and contents;
- `OS.get_cmdline_args()` and `OS.get_cmdline_user_args()`;
- `JavaScriptBridge` callbacks;
- `DisplayServer.clipboard_get()`;
- UI text input when it reaches other players (chat, names).

## Sonar model (checked against sonar-plugin-api 13.8.0.4399)

### Finding model

On 2026-03-31 Sonar announced that it is phasing out Security Hotspots: by
2026-07-01 hotspots were to become issues impacting the software quality
`SECURITY` ([announcement](https://community.sonarsource.com/t/moving-security-hotspots-to-security-issues/180583)).
The plugin API still offers `RuleType.SECURITY_HOTSPOT`, but this plan does not
build on it.

- Every rule is `RuleType.VULNERABILITY` with
  `addDefaultImpact(SoftwareQuality.SECURITY, …)`.
- **Confirmed** rules establish a weakness with high confidence. Their impact,
  `HIGH` or `BLOCKER`, reflects the likely consequence, not only the
  confidence. Description sections: `root_cause` and `how_to_fix`.
- **Review** rules mark security-sensitive code that a human must judge: impact
  `LOW` or `MEDIUM`; an `assess_the_problem` section ("ask yourself whether").
- Because review findings are issues, they can fail quality gates. Activation
  therefore follows the precision gates in "Evaluation and evidence".
- S0 also records how the pinned server treats a third-party
  `SECURITY_HOTSPOT` rule, as evidence only.

### Metadata and mapping policy

- Metadata: `setType`, `addDefaultImpact`, `setCleanCodeAttribute`, `addCwe`,
  `addOwaspTop10(OwaspTop10Version.Y2021, …)` and `addDescriptionSection` with
  the keys `introduction`, `root_cause`, `assess_the_problem`, `how_to_fix`,
  `resources`. `Y2025` exists in the API; its mappings are added only after
  explicit review against the official list and the pinned server.
- A confirmed rule's CWE names the weakness it establishes. A review rule's CWE
  names the weakness the reviewer is asked to rule out; a review rule without a
  clear weakness class carries no CWE.
- Every CWE and OWASP mapping is checked against MITRE CWE and the official
  OWASP Top 10 2021 lists in S0. A mapping marked TBD below is not published
  until then.

### Platform behavior

- Taint findings show the source-to-sink path with `NewIssue.addFlow`.
- Suppression: `# NOSONAR` lines are registered through
  `NoSonarFilter.noSonarInFile`; core `sonar.issue.ignore.*` settings apply.
  Godot's `@warning_ignore` is unrelated and is not mapped.
- `javap` shows that these API methods exist, not how the server behaves. S0
  tests each claimed behavior on the pinned server (see "Milestones and
  gates").
- Issue messages and logs never contain a detected secret value, only the name
  it was assigned to.

## Analysis levels

| Level | Needs | Enables |
|---|---|---|
| L0 | Lexer (metrics M1) | Rule infrastructure, suppression |
| L1 | Parser (metrics M3) + scope check | Calls on engine singletons, global functions and class-qualified constants; annotations; assignments |
| L2 | Semantic model: inheritance, local type inference, engine API model | Instance members on resolved receivers, inherited properties such as `multiplayer`, same-object call sequences |
| L3 | Control-flow graph + taint with same-file summaries | Injection-style confirmed rules |
| L4 | Project context (`.tscn`, `.tres`, `project.godot`, export configuration) | Authority analysis, scene-wired handlers, configuration secrets |

- Engine singletons (`OS`, `ClassDB`, `ProjectSettings`, `JavaScriptBridge`,
  `Marshalls`) match at L1 only if no enclosing scope declares the same name.
- `multiplayer` is an inherited `Node` property of type `MultiplayerAPI`, and
  projects can install custom implementations. Members such as
  `allow_object_decoding` exist on `SceneMultiplayer`, the default
  implementation, so rules on them need L2.
- Precision-first rule: an untyped or unresolved receiver produces no issue.

## Rule catalog (candidates)

IDs are stable once shipped; a retired rule is deprecated, never renamed.

### Review rules

| ID | Level | CWE | OWASP 2021 | Flags |
|---|---|---|---|---|
| `process-execution` | L1 | — | — | `OS.execute`, `OS.create_process`, `OS.execute_with_pipe` |
| `shell-command` | L1 | 78 | A03 | The same calls when the executable is a shell or interpreter invoked in its command-string form (`sh -c`, `bash -c`, `cmd /C`, `powershell -Command`, …) with a non-constant command string |
| `shell-open-dynamic` | L1 | — | — | `OS.shell_open` with a non-constant argument; a CWE is added only for a demonstrated dispatch exploit class |
| `javascript-eval` | L1 | 95 | A03 | `JavaScriptBridge.eval` with a non-constant argument |
| `resource-pack-loading` | L1 | 829 | A08 | `ProjectSettings.load_resource_pack` |
| `untrusted-resource-load` | L1 | 829 | A08 | `load`, `ResourceLoader.load`, `ResourceLoader.load_threaded_request` with a non-constant path or a constant `user://` path; ships only after the proof described below |
| `object-decoding` | L1/L2 | 502 | A08 | `bytes_to_var_with_objects`; `Marshalls.base64_to_variant(…, true)`; `get_var(true)` on a resolved `FileAccess` (L2) |
| `rpc-any-peer` | L1 | 862 | A01 | Every RPC callable by any peer, for authorization review |
| `hardcoded-credential` | L1 | 798 | A07 | Non-empty, non-placeholder string assigned to a narrow set of credential names (password, passwd, secret, token, api_key, private_key); public identifiers (`*_id`, `client_id`, `public_key`) are excluded |
| `weak-hash` | L1/L2 | 328 | A02 | `HashingContext.HASH_MD5` / `HASH_SHA1` (L1); `md5_text`, `sha1_text`, `md5_buffer`, `sha1_buffer` on resolved `String` / `PackedByteArray` receivers (L2) |
| `aes-ecb-reference` | L1 | 327 | A02 | References to `AESContext.MODE_ECB_ENCRYPT` / `MODE_ECB_DECRYPT` where `aes-ecb` does not apply |
| `tls-unsafe-options` | L1 | 295 | A07 | `TLSOptions.client_unsafe(…)` where `tls-validation-disabled` does not apply |
| `expression-evaluation` | L2 | 95 | A03 | `parse` followed by `execute` on the same resolved `Expression` |
| `dynamic-script-source` | L2 | 94 | A03 | Setting `source_code` followed by `reload()` on the same resolved `Script` |
| `cleartext-protocol` | L2 | 319 | A02 | An `http://` or `ws://` literal (except loopback hosts) that reaches `HTTPRequest.request`, `HTTPClient.connect_to_host` or `WebSocketPeer.connect_to_url` directly or through a constant in the same file |
| `replication-authority` | L4 | — | — | `MultiplayerSynchronizer` configurations replicating from a client authority into state the server acts on (research candidate) |
| `export-credentials` | L4 | 312 | TBD | A non-empty sensitive value in `.godot/export_credentials.cfg` that is part of the analyzed sources, or signing secrets in `export_presets.cfg` from Godot versions that stored them there. The finding states inclusion in the analysis, not that the file is tracked by Git |

Several review rules have a more specific counterpart; see "Paired rules".

### Confirmed rules

| ID | Level | CWE | OWASP 2021 | Detects |
|---|---|---|---|---|
| `network-object-decoding` | L2 | 502 | A08 | `allow_object_decoding = true` on a receiver resolved to `SceneMultiplayer` (including the default `multiplayer`); `get_var(true)` on a resolved network peer (`ENetPacketPeer`, `WebSocketPeer`, `PacketPeerUDP`, `PacketPeerDTLS`, `PacketPeerStream`) |
| `tls-validation-disabled` | L2 | 295 | A07 | A `client_unsafe()` result passed to `HTTPClient.connect_to_host`, `WebSocketPeer.connect_to_url` or `StreamPeerTLS.connect_to_stream`, or to `HTTPRequest.set_tls_options` on an `HTTPRequest` that then calls `request`. Web exports always enforce validation, so Web-only projects are excluded once L4 knows the export targets |
| `aes-ecb` | L2 | 327 | A02 | An ECB mode passed to `start` on a resolved `AESContext` |
| `command-injection` | L3 | 78 | A03 | Tainted data in the command string of a shell or interpreter invocation |
| `argument-injection` | L3 | 88 | A03 | Tainted data in an argument position that the target program interprets as options, before any `--` end-of-options marker; target programs and their option rules are data in the sink specification |
| `expression-injection` | L3 | 95 | A03 | Tainted data into `Expression.parse` followed by `execute` |
| `javascript-injection` | L3 | 95 | A03 | Tainted data into `JavaScriptBridge.eval` |
| `script-injection` | L3 | 94 | A03 | Tainted data into `Script.source_code` followed by `reload()` |
| `unsafe-reflection` | L3 | 470 | A03 | Tainted names into `call`, `callv`, `call_deferred`, `ClassDB.instantiate`, or into a `Callable(obj, name)` that is then called |
| `unsafe-property-access` | L3 | 915 | A08 | Tainted property names into `set`, `set_indexed`, `set_deferred`. Tainted names into `get` are a separate read-exposure question for S0 |
| `path-traversal` | L3 | 22 | A01 | Tainted paths that can escape the intended directory, into `FileAccess.open`, `DirAccess` operations, `load` |
| `archive-path-traversal` | L3 | 22 | A01 | `ZIPReader.get_files()` entry names used to build a path that is then opened for writing with `FileAccess.open` or created with `DirAccess` |
| `server-side-request-forgery` | L4 | 918 | A10 | In code that L4 shows runs on a server, tainted URLs or hosts into `HTTPRequest.request` or `HTTPClient.connect_to_host` without a dominating check against a constant host allow-list |
| `untrusted-pack-download` | L4 | 494 | A08 | Research candidate: a downloaded pack that contains code and is loaded with `load_resource_pack` without verification against a trusted origin. Needs a file-path relation the taint model does not have yet |
| `insecure-random` | L3 | 338 | A02 | Non-cryptographic RNG output reaching a token, salt, nonce, key or session value; `Crypto.generate_random_bytes` is the safe alternative |
| `unbounded-allocation` | L3 | 789 (606 for loop bounds) | — | Tainted sizes into `resize` or loop bounds without a dominating upper-bound check |
| `rpc-missing-authorization` | L3/L4 | 862 | A01 | An `any_peer` RPC (this rule covers no other mode) reaching a privileged sink on a path not dominated by a recognized authorization check (see "Taint model"); privileged sinks are listed in the sink specification |

Evidence for the riskiest APIs, from the Godot class reference (master):
- The object-decoding options of `bytes_to_var_with_objects`,
  `FileAccess.get_var`, `PacketPeer.get_var`, `Marshalls.base64_to_variant` and
  `SceneMultiplayer.allow_object_decoding` all carry an explicit
  remote-code-execution warning.
- `TLSOptions.client_unsafe` is documented as making certificate validation
  optional.
- `Expression.parse` only parses; code runs in `execute`. `source_code` and
  `reload` belong to `Script`, and setting `source_code` does not reload by
  itself.
- `ResourceLoader` carries no security warning. `untrusted-resource-load`
  therefore ships only after an isolated, reproducible proof that loading a
  crafted resource runs embedded script code. Its CWE stays 829 unless that
  proof shows unsafe deserialization (502).
- `str_to_var` is not a rule until it is shown that it can construct objects.
- Modern Godot keeps confidential export options in
  `.godot/export_credentials.cfg` and documents that it should not be committed.

### Paired rules

| Less specific | More specific | Shared primary location |
|---|---|---|
| `process-execution` | `shell-command` | The process call |
| `shell-command` | `command-injection` | The process call |
| `javascript-eval` | `javascript-injection` | The `eval` call |
| `expression-evaluation` | `expression-injection` | The `parse` call |
| `dynamic-script-source` | `script-injection` | The `source_code` assignment |
| `aes-ecb-reference` | `aes-ecb` | The mode reference |
| `tls-unsafe-options` | `tls-validation-disabled` | The `client_unsafe()` call |
| `rpc-any-peer` | `rpc-missing-authorization` | The RPC function declaration (sinks are secondary locations) |

- For one primary location, the most specific rule that is active and reports
  in this analysis wins; the less specific rules stay silent there.
- A less specific rule stays silent only when that more specific finding is
  actually published. If the specific rule is inactive, its level is not
  enabled, or its analysis of the function was incomplete, the less specific
  rule reports.
- Precedence is decided per file before publication, inside the metrics plan's
  publication boundary.

## Taint model (L3)

Sources are listed in "Threat model". RPC trust:
- `any_peer` RPC parameters are always tainted.
- `authority` RPC parameters are tainted unless L4 shows that the authority is
  the server and is never reassigned to a client. Authority can be changed with
  `set_multiplayer_authority`, and `rpc_config` can change RPC modes at runtime;
  both are tracked.
- Calling `get_remote_sender_id()` proves nothing by itself. A recognized
  authorization check for a privileged operation is one of:
  - a comparison of the sender id with a principal trusted for that operation:
    the server peer (1) for server-only operations, or a constant or
    server-controlled allow-list;
  - a call to a helper declared in the project's authorization specification
    (a data file stating which argument is checked and which result means
    "allowed"), whose result guards the sink.
- The check counts only where its success branch dominates the sink. A sender
  equal to a client authority does not authorize server-privileged operations.
- An unrecognized check never produces a confirmed `rpc-missing-authorization`
  finding; the function keeps its `rpc-any-peer` review finding.

Propagation:
- assignment, string concatenation, `%` formatting, `format`, `str`, `String`
  transformations;
- local aliases are tracked; fields of `self` are tracked per field name; fields
  of other objects are tainted when any write to them is tainted;
- container element access (taint tracked per container);
- lambda captures;
- branch joins take the union of taint.

Calls:
- engine methods use the specifications in the engine API model;
- same-file functions use summaries: parameter to return value, parameter to
  sink, and mutations of parameters and `self` fields;
- other unknown calls return tainted data if any argument is tainted, and they
  invalidate the sanitization of every argument passed to them and of every
  `self` field;
- signal handlers connected with `connect` in the same script map signal
  arguments to handler parameters by position, including `Callable.bind`. A
  `connect` whose signal or callable cannot be resolved makes the enclosing
  function incomplete (see "Budgets and incompleteness").

Sanitizers are sink-specific, apply only on paths dominated by a successful
check, and are invalidated when the value is reassigned or mutated:
- membership in a constant collection, or `match` on constants: for reflection,
  property, command and code sinks;
- numeric conversion after `is_valid_int()` / `is_valid_float()`: for
  reflection, property, expression and JavaScript sinks only. Never for process
  arguments (a negative number can be read as an option), allocation sizes or
  authorization; allocation sinks need a dominating upper-bound comparison;
- paths: let `p` be the result of `simplify_path()`; `p` is contained in a
  constant base directory `B` only if `p == B` or `p.begins_with(B + "/")`.
  `begins_with(B)` alone is not enough: it accepts `user://safe_evil` for
  `B = "user://safe"`. Lexical checks cannot see symlinks; this limit is
  documented.
- file names: Godot's `is_valid_filename()` accepts `.` and `..`, so a name
  counts as sanitized only if it passes that check, is neither `.` nor `..`,
  and the joined final path still passes the containment check above.

Unrecognized checks leave data tainted; precision gates catch the noise. Each
sanitizer has negative tests: the failed-check branch, reassignment after the
check, and mutation through an alias.

Budgets and incompleteness:
- fixed-point iterations and analysis time per function are bounded;
- a function-like body (`func`, lambda, accessor, and a file's member
  initializers taken as one unit) is incomplete when it exceeds its budget,
  uses a construct the engine does not support, or contains an unresolved
  `connect`; it gets no taint findings;
- a custom metric `gdscript_security_incomplete_functions` counts incomplete
  bodies per file and is aggregated like the metrics plan's
  `gdscript_incomplete_files`. It is published for every MAIN file from S4 on,
  with 0 when no L3 rule is active;
- `sonar.gdscript.failOnIncompleteAnalysis` also fails the analysis when this
  count is above zero.

## Architecture

- Reuses `lexer` and `parser` from the metrics plan.
- `org.orebron.gdscript.semantic`: scopes, symbol table, inheritance, local type
  inference from annotations, `:=` with `X.new()` and engine return types.
- Engine API model:
  - versioned data generated from the pinned Godot tag's `doc/classes/*.xml`
    and module `doc_classes` (MIT-licensed);
  - lists classes with their inheritance, singletons, methods, properties,
    signatures and enum constants. Inheritance and singleton data are checked
    by tests against known cases (for example `WebSocketPeer` inherits
    `PacketPeer`, `multiplayer` is a `Node` property);
  - shipped inside the JAR, never downloaded at analysis time.
- `org.orebron.gdscript.security`: rules as tree visitors and the taint engine.
  Source, sink and sanitizer specifications are data files, so they can be
  reviewed without reading engine code.
- Issues follow the metrics plan's publication boundary and failure policy:
  - files without a complete parse get no L1+ security findings;
  - those files are already counted by `gdscript_incomplete_files`.

## Analyzer hardening

This follows the trust boundary in [SECURITY.md](../SECURITY.md).

- Never execute or load scanned code:
  - no Godot process and no resource or class loading from the project;
  - no network access during analysis.
- Pattern matching over source text (URLs, credential names) uses hand-written
  matchers or audited linear-time patterns, never backtracking regexes on
  attacker-controlled text. Fuzz tests feed adversarial inputs.
- The metrics plan's limits apply: depth, size and cancellation. The taint
  engine adds its own budgets.
- Detected secret values never appear in issue messages, logs or analysis
  errors.

## Milestones and gates

Prerequisites from the [metrics plan](metrics-plan.md): M0 (integration
harness), M0.5 (ADR 0002), M1 (lexer) and M3 (parser). S2 and later need M3.

- **S0 – specifications and platform facts.**
  - Each candidate rule gets a specification in [rules](rules.md): rationale,
    finding class, impact, CWE/OWASP, compliant and noncompliant examples,
    false-positive examples, suppression.
  - Every CWE and OWASP mapping is checked against the official lists.
  - Each confirmed rule's specification states its exact evidence: effective
    use of the configured object, the trust boundary crossed, platform
    conditions, and an impact severity justified by the likely consequence.
  - The harness answers, with evidence on the pinned Community Build:
    1. How are third-party `VULNERABILITY` rules with a `SECURITY` impact shown,
       and how does a third-party `SECURITY_HOTSPOT` rule behave?
    2. Are description sections rendered as intended?
    3. Do `# NOSONAR` and each claimed `sonar.issue.ignore.*` setting suppress
       both finding classes? Do rule activation and deactivation work? Are flow
       locations shown in order?
    4. Does the bundled secrets analysis already scan `.gd` files? If so,
       provider-specific secret patterns are not duplicated.
    5. Can a sensor see non-language files such as
       `.godot/export_credentials.cfg`, and are files ignored by Git excluded
       from the analyzed sources?
- **S1 – rule infrastructure, before any rule ships:** rule base classes;
  paired-rule precedence with tests for every activation combination;
  `NOSONAR` support with its tests; and the contract of the
  security-incompleteness metric and strict-mode extension, aligned with the
  metrics plan's file outcomes (values are published from S4).
- **S2 – L1 review rules:** `process-execution`, `shell-command`,
  `shell-open-dynamic`, `javascript-eval`, `resource-pack-loading`,
  `untrusted-resource-load` (after its proof), `object-decoding` (L1 forms),
  `rpc-any-peer`, `hardcoded-credential`, `weak-hash` (constant form),
  `aes-ecb-reference`, `tls-unsafe-options`.
- **S3 – L2:** the semantic model and engine API model, then
  `network-object-decoding`, `tls-validation-disabled`, `aes-ecb`, the method
  form of `weak-hash`, `object-decoding` on `FileAccess`,
  `expression-evaluation`, `dynamic-script-source` and `cleartext-protocol`.
- **S4 – L3:** the taint engine, then `command-injection`,
  `argument-injection`, `expression-injection`, `javascript-injection`,
  `script-injection`, `unsafe-reflection`, `unsafe-property-access`,
  `path-traversal`, `archive-path-traversal`, `insecure-random`,
  `unbounded-allocation`, and `rpc-missing-authorization` in its same-file
  form.
- **S5 – L4:** authority analysis, server-context detection, scene-wired
  signal handlers, `rpc-missing-authorization` with cross-scene context,
  `server-side-request-forgery`, `replication-authority`,
  `export-credentials`, `untrusted-pack-download` (research), and scripts
  embedded in `.tscn` / `.tres` files.

Gate for every rule, active or not:
- a specification;
- noncompliant, compliant and near-miss fixtures with exact expected issues,
  for review rules too.

Additional gate for default activation:
- corpus triage meeting the thresholds in "Evaluation and evidence";
- correct display in the harness, including flows for taint findings.

A rule that misses its activation gate ships inactive, with the reason
documented. README and [verification](verification.md) list only shipped
rules.

## Evaluation and evidence

- Labeled suite:
  - original fixtures under `src/test/resources/fixtures/security/`;
  - every rule has bad, good and near-miss cases tagged with their CWE;
  - taint rules add cross-statement, sanitizer and aliasing variants.
- Real-world corpus:
  - a fixed list of open-source Godot 4 projects at pinned commits, covering
    clients, dedicated servers, editor tools and web exports;
  - split by project into a development set and a held-out set. Rule
    behavior and thresholds are frozen on the development set; activation is
    measured once on the held-out set, and nothing is tuned after seeing its
    results. Projects added later form a new held-out set;
  - analyzed read-only in the harness; only the project list and aggregated
    triage results are committed, never third-party code.
- Triage:
  - each finding is labeled true positive, false positive or needs-context,
    with a one-line reason;
  - a named independent security reviewer, not the rule's author, confirms the
    labels;
  - needs-context counts as a false positive for activation and is reported
    separately.
- Activation thresholds (provisional, revisited after the first corpus run),
  using precision with a 95% Wilson interval:

  | Finding class | Point precision | Interval lower bound |
  |---|---|---|
  | Confirmed | ≥ 95% | ≥ 85% |
  | Review (judged worth reviewing) | ≥ 70% | ≥ 55% |

  - The same thresholds apply to any sample size; nothing is relaxed for small
    samples. When all n findings are true positives the lower bound is
    n / (n + 1.96²), so a confirmed rule needs at least 22 held-out findings.
    At 30 findings a confirmed rule needs 30/30 (29/30 gives 83.3%) and a
    review rule at least 22/30. A sample below 30 is reported as a low sample.
  - A rule with zero corpus findings stays inactive unless targeted corpus
    projects exercise it.
  - Taint rules also report recall on the labeled suite.
- Disclosure:
  - before corpus triage starts, the repository owner names the triagers and a
    confidential channel for third-party reports;
  - a real vulnerability in a corpus project is reported privately through that
    project's published security channel, GitHub private vulnerability
    reporting where enabled, or a published maintainer contact. If none
    exists, the repository owner and the security reviewer decide how to
    proceed, and nothing is disclosed meanwhile;
  - the embargo defaults to 90 days, unless the maintainers agree otherwise. It
    starts when they acknowledge the report, or 14 days after the first
    contact attempt if they do not. It is a disclosure policy, not an
    activation gate;
  - it is never described in this repository, its issues or logs;
  - scanner reports and server data for corpus projects stay on the disposable
    harness, are accessible only to the triagers, and are deleted after triage.
    Evidence for reported findings is kept privately until the fix is
    confirmed or the embargo ends, then deleted.

## Risks

- **Hotspot phase-out:** review findings are issues and can fail quality gates.
  Mitigated by activation gates and low impact severities.
- **Noise:** games use randomness, `load` and dynamic calls everywhere.
  Mitigated by precision gates and inactive-by-default rules.
- **False assurance:** zero findings must never read as "secure". README and
  rule texts say so explicitly.
- **API drift:** Godot renames and adds APIs. The engine model is pinned per
  Godot version, and rule specifications name the versions they cover.
- **Custom multiplayer:** projects can replace `MultiplayerAPI`; rules resolve
  only what the semantic model can prove.
- **Lexical path checks:** containment checks cannot see symlinks.
- **Overlap:** the bundled secrets analysis may already cover some secrets (S0
  decides).
- **Coverage gaps:**
  - scripts embedded in scenes and resources are invisible until S5;
  - C# and GDExtension code is never analyzed;
  - both gaps are documented.
- **Edition differences:** only what the harness shows on the pinned Community
  Build is claimed.
- **Disclosure:** corpus triage can surface real vulnerabilities in other
  people's projects (see "Evaluation and evidence").

## Decisions

1. **Activation:** only rules that pass their activation gate are active by
   default.
2. **`untrusted-resource-load`** includes constant `user://` paths: a constant
   path is not a trusted file.
3. **`authority` RPC parameters are tainted** unless L4 establishes a trusted
   server authority.
4. **Credentials:** narrow credential names with public-identifier exclusions;
   provider-specific secret patterns only after S0 shows the bundled analysis
   does not cover `.gd` files.
5. **Engine API model** from the pinned class reference XML, with inheritance
   and singleton data validated by tests.
6. **`insecure-random`** is a taint rule only; the name-based heuristic is
   dropped.
7. **Thresholds** use point precision plus an interval lower bound, a minimum
   sample, and a held-out set.
8. **Editor code:** editor context is modeled as a source of untrusted data,
   not as a reason for blanket stricter classifications.
9. **OWASP Top 10 2025** mappings are added after explicit review and
   verification on the pinned server.
10. **No `SECURITY_HOTSPOT` rule type** unless S0 shows a concrete benefit on
    the pinned server.

## Open decisions

1. Who are the corpus owner and the independent security reviewer, and which
   confidential channel is used? These must be named before S2 corpus triage.
