#!/usr/bin/env python3
"""Basic tracked-source guards. Reports location/rule only, never matched values."""
import re, subprocess, sys
from pathlib import Path
PATTERNS = {
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'aws-key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})\b'),
    'slack-token': re.compile(r'\bxox[baprs]-[A-Za-z0-9-]{20,}\b'),
}
def findings(text):
    return [(i,rule) for i,line in enumerate(text.splitlines(),1) for rule,pattern in PATTERNS.items() if pattern.search(line)]
if __name__ == '__main__':
    paths = subprocess.check_output(['git','ls-files','-z']).decode().split('\0')
    found = []
    for name in paths:
        p=Path(name)
        if not p.is_file() or p.stat().st_size>5_000_000: continue
        try: text=p.read_text()
        except UnicodeError: continue
        found += [(name,line,rule) for line,rule in findings(text)]
    for name,line,rule in found: print(f'{name}:{line}: {rule} (value redacted)')
    print(f'Tracked-source secret guard: {len(found)} findings. Not a complete secret audit.')
    sys.exit(bool(found))
