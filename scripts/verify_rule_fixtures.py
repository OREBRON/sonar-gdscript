#!/usr/bin/env python3
"""Compare a server's issues on the its/rules fixture project with the fixtures' "# Noncompliant" markers.

Run after scanning its/rules as its own project. The token comes from the SONAR_TOKEN environment variable and is
never printed. Exits 1 when any rule reports other lines than its fixture lists.

    SONAR_TOKEN=... python3 scripts/verify_rule_fixtures.py --host http://localhost:9000 --project gdscript-rule-fixtures
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'its' / 'rules'
# The same syntax RuleFixturesTest reads: "# Noncompliant", "# Noncompliant@+1", "# Noncompliant 2".
MARKER = re.compile(r"#\s*Noncompliant(?:@([+-]\d+))?(?:\s+(\d+)\b)?")


def expected_lines(text):
    lines = []
    for number, line in enumerate(text.split('\n'), start=1):
        match = MARKER.search(line)
        if match:
            target = number + int(match.group(1) or 0)
            lines += [target] * int(match.group(2) or 1)
    return sorted(lines)


def api(host, token, path, **params):
    request = Request(f"{host.rstrip('/')}{path}?{urlencode(params)}")
    request.add_header('Authorization', 'Basic ' + base64.b64encode(f"{token}:".encode()).decode())
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def active_rules(host, token, project):
    profiles = api(host, token, '/api/qualityprofiles/search', project=project, language='gdscript')['profiles']
    rules = api(host, token, '/api/rules/search', qprofile=profiles[0]['key'], activation='true', languages='gdscript', ps=500)
    return {rule['key'].split(':', 1)[1] for rule in rules['rules']}


def issues(host, token, project):
    found, page = [], 1
    while True:
        result = api(host, token, '/api/issues/search', componentKeys=project, resolved='false', ps=500, p=page)
        found += result['issues']
        if page * 500 >= result['paging']['total']:
            return found
        page += 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--host', required=True)
    parser.add_argument('--project', required=True)
    arguments = parser.parse_args()
    token = os.environ.get('SONAR_TOKEN')
    if not token:
        print('Set SONAR_TOKEN to a token that can browse the project.', file=sys.stderr)
        return 2
    active = active_rules(arguments.host, token, arguments.project)
    reported = {}
    for issue in issues(arguments.host, token, arguments.project):
        name = issue['component'].rsplit('/', 1)[-1]
        reported.setdefault((name, issue['rule'].split(':', 1)[1]), []).append(issue.get('line', 0))
    failures = 0
    for fixture in sorted(FIXTURES.glob('*.gd')):
        text = fixture.read_text(encoding='utf-8')
        if fixture.stem == 'nosonar':
            dropped = [n for n, line in enumerate(text.split('\n'), 1) if '# NOSONAR' in line]
            leaked = sorted(line for (name, _), lines in reported.items() if name == fixture.name for line in lines if line in dropped)
            status = 'ok' if not leaked else f'issues on NOSONAR lines {leaked}'
        else:
            want = expected_lines(text) if fixture.stem in active else []
            got = sorted(reported.get((fixture.name, fixture.stem), []))
            status = 'ok' if want == got else f'expected {want}, server reported {got}'
            if fixture.stem not in active:
                status += ' (rule not active in the project\'s profile)'
        failures += status.split(' (')[0] != 'ok'
        print(f"{'PASS' if status.startswith('ok') else 'FAIL'}  {fixture.stem:32} {status}")
    print(f"{failures} of {len(list(FIXTURES.glob('*.gd')))} fixtures differ")
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
