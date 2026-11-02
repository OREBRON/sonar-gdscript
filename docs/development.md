# Development and validation

## Build
JDK 21 and Maven 3.9.x, from repository root:

```sh
mvn --batch-mode --no-transfer-progress verify
python3 scripts/check_docs.py
```

Inspect each module's target/ test reports and the manifest of
`sonar-gdscript-plugin/target/*.jar`, which must list the bundled lexer under
`Plugin-Dependencies`. Building must not require
a running SonarQube, Python analyzer dependencies or private game credentials.

CI builds/tests on every push and pull request. Artifact storage is optional:
manual workflow dispatch can enable `upload_artifact` when organization quota is
available. The initial CI build/tests passed but artifact upload failed because
the organization's storage quota was full. Existing artifacts were not deleted;
normal verification no longer requires artifact storage. Local JARs remain in
target/ and are intentionally not committed.

## Isolated integration gate (required before release)
1. Build/inspect the JAR and its plugin/API/JDK manifest requirements.
2. Provision disposable matching SonarQube with separate volumes and a loopback
   port. Never reuse the working server's database or extensions volume.
3. Add the JAR to that test instance's extensions/plugins and restart only it.
4. Wait for UP; verify installed plugin, language, rules and profile via API.
5. Run the real existing SonarScanner on original clean/violating .gd fixtures.
6. Assert exact CE task SUCCESS and expected issue file/rule/line. Also test
   deactivating the rule and changing its threshold.
7. Retain real verification logs without secrets; remove only disposable resources.

Example fixture scanner properties after successful installation:

```properties
sonar.projectKey=gdscript-plugin-smoke
sonar.sources=src
sonar.sourceEncoding=UTF-8
sonar.gdscript.maxLineLength=120
```

Provide SONAR_HOST_URL and SONAR_TOKEN through the environment, not committed
properties. This validates a line check, not coverage or GDScript semantics.

## Real-project pilot
Only after isolated integration, use a user-authorized project. Record Git status
and source/config hashes, keep work artifacts outside source and verify unchanged
files afterward. Respect exclusions and distinguish failed/unsupported files from
clean files. No engine/editor execution is required by the foundation.
