# Security policy

No supported production release exists yet. Do not post credentials, private game
source or exploit-sensitive details in public issues. Contact the repository owner
privately using an existing trusted channel; no dedicated inbox is established.

## Trust boundary
Scanning source is not permission to execute it. Default analysis must not run
Godot, project hooks, @tool scripts or GDExtensions. Headless editors are not a
sandbox. Future external backends need explicit opt-in, subprocess argument arrays,
timeouts, bounded output and isolation.

Future report readers must validate schemas and paths and reject project-root
escape. No executable deserialization. Pass SONAR_TOKEN through the scanner's
supported environment, never committed settings or logs. Keep container data and
credentials out of Git.

Style findings are not proof of vulnerabilities. Zero findings is not a security
audit. Report parser failures and unsupported files rather than hiding them.
