#!/usr/bin/env python3
"""Check repository-relative Markdown link targets; never fetch remote URLs."""
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
LINK = re.compile(r"(?<!!)\[[^\]\n]+\]\(([^)\s]+)(?:\s+[^)]*)?\)")


def main() -> int:
    failures = []
    checked = 0
    documents = sorted(p for p in ROOT.rglob('*.md')
                       if not {'.git', 'target', '.m2'} & set(p.relative_to(ROOT).parts))
    for document in documents:
        for target in LINK.findall(document.read_text(encoding='utf-8')):
            uri = urlsplit(target)
            if uri.scheme or uri.netloc or not uri.path:
                continue
            resolved = (document.parent / unquote(uri.path)).resolve()
            checked += 1
            if not resolved.is_relative_to(ROOT) or not resolved.exists():
                failures.append(f'{document.relative_to(ROOT)}: missing/unsafe target {target}')
    if failures:
        print('\n'.join(failures), file=sys.stderr)
        return 1
    print(f'Documentation links OK: {checked} local targets in {len(documents)} Markdown files')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
