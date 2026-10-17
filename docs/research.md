# Research and sources

Research date: 2026-10-20; version baseline re-checked 2026-10-21. Re-check mutable
upstream documentation and pin actual implementation dependencies. Upstream
descriptions are not runtime test evidence.

## SonarQube model
- [Supporting new languages](https://docs.sonarsource.com/sonarqube-community-build/extension-guide/developing-a-plugin/supporting-new-languages): grammar/parser, rule visitors, scanner Sensor; measures, duplication, highlighting, symbols and coverage are separate capabilities.
- [Plugin basics](https://docs.sonarsource.com/sonarqube-community-build/extension-guide/developing-a-plugin/plugin-basics): Java Plugin API, packaging, server/scanner/Compute Engine extension lifecycle. Old example minimum Java/API versions are not this project's requirements.
- [Official plugin example](https://github.com/SonarSource/sonar-custom-plugin-example): packaging and test-fixture reference.
- [Sonar Plugin API](https://github.com/SonarSource/sonar-plugin-api): versioned separately from server; latest API is not automatically compatible with an older server.
- [Generic issue import](https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/importing-external-issues/generic-issue-import-format): optional external-report alternative, not native language metrics, profiles or coverage.

## Version baseline
- [Plugin API compatibility table](https://github.com/SonarSource/sonar-plugin-api#compatibility):
  every Server 2026.1.x patch and Community Build 26.1/26.2 ship Plugin API
  13.4.3.4290; Server 2026.4 and Community Build 26.7/26.8 ship 13.8.0.4399;
  Server 2026.5 LTA ships 14.0.0.4498.
- [Server 2026.1 scanner requirements](https://docs.sonarsource.com/sonarqube-server/2026.1/analyzing-source-code/scanners/scanner-environment/general-requirements):
  Java 17 is deprecated but supported for scanners until 2026.3; Java 21 is
  required from 2026.4. Auto-provisioning manages the scanner JRE.
- [sensor-test-fixtures](https://repo1.maven.org/maven2/org/sonarsource/scanner/engine/sensor-test-fixtures/):
  every published version depends on Plugin API 13.8+; locally it fails at the
  13.4.3 floor with a missing `NewIssueResolution` class (added in 13.5).
- [sonar-plugin-api-impl 26.2.0.119303](https://repo1.maven.org/maven2/org/sonarsource/sonarqube/sonar-plugin-api-impl/26.2.0.119303/):
  still contains `SensorContextTester`/`TestInputFileBuilder` (removed in 26.7),
  targets Plugin API 13.4.3 and is Java 17 bytecode.
- [Third-party plugins on SonarQube Cloud](https://community.sonarsource.com/t/does-sonarqube-cloud-support-third-party-plugins-like-the-mulesoft-sonarqube-plugin/138134):
  not supported.

## Existing plugin
[darkiyy/sonar-gdscript](https://github.com/darkiyy/sonar-gdscript) explicitly says
it is unfinished, targets SonarQube 10.x and can have Godot 4 grammar problems.
Latest checked prerelease:
[v0.11.1-aplha](https://github.com/darkiyy/sonar-gdscript/releases/tag/v0.11.1-aplha),
2023-05-30. That unusual spelling is the real tag. Coverage and some metrics are
unfinished. No 2026.x server compatibility is established. LGPL-3.0 source;
none has been copied into this independent project.

The [rtownsend9 fork](https://github.com/rtownsend9/sonar-gdscript) showed a newer
push timestamp but default-branch latest commits still from 2023 and no releases.
A push timestamp alone is not evidence of maintained compatibility.

## Parser/linter candidate
[GDScript Toolkit](https://github.com/Scony/godot-gdscript-toolkit) provides a
parser, gdlint, gdformat and gdradon. Checked latest stable
[4.5.0](https://github.com/Scony/godot-gdscript-toolkit/releases/tag/4.5.0) was
published 2025-10-09, confirmed by [PyPI](https://pypi.org/project/gdtoolkit/4.5.0/).
MIT-licensed. Godot 3 and 4 use different package major lines. This is not a
SonarQube plugin, not integrated here, and not certified for every newer Godot
syntax feature. Do not invoke its formatter during an analysis-only scan.

## Engine diagnostics and risks
- [Static typing](https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/static_typing.html): engine-assisted type checks are distinct from style linting.
- [Warning system](https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/warning_system.html): project warning configuration and suppression annotations need explicit semantics in future adapters.
- [Command-line tools](https://docs.godotengine.org/en/stable/tutorials/editor/command_line_tutorial.html): --check-only requires --script; it is not automatically a project-wide analyzer API.
- [Editor code execution](https://docs.godotengine.org/en/stable/tutorials/plugins/running_code_in_the_editor.html): @tool scripts can run in editors; headless is not a sandbox.

## Evidence boundary
Supported range: SonarQube Server 2026.1 LTA and later, Community Build 26.1 and
later (Plugin API 13.4.3+), a Java 17+ analysis runtime and Godot 4 source.
Unit/sensor-fixture tests and packaging do not establish server startup, actual
scanner loading or successful Compute Engine processing. The working SonarQube
instance must not become an implicit integration fixture. Native end-to-end
compatibility remains a separate release gate until an isolated run proves it.
