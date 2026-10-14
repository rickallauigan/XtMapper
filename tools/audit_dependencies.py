#!/usr/bin/env python3
"""OSV Maven advisory query. No credentials, package installation or auto-fixes."""
import json, sys, urllib.request
from pathlib import Path

def audit(components, query):
    unique = sorted({(c['group'], c['name'], c['version']) for c in components})
    if len(unique) > 1000: raise ValueError('Too many dependency records')
    payload = {'queries': [{'package': {'ecosystem': 'Maven', 'name': f'{g}:{n}'}, 'version': v} for g,n,v in unique]}
    response = query(payload)
    results = response['results']
    if len(results) != len(unique): raise ValueError('Incomplete advisory response')
    if any(r.get('next_page_token') for r in results): raise ValueError('Paginated advisory response requires manual review')
    return [{'module': ':'.join(module), 'advisories': sorted(v['id'] for v in result.get('vulns', []))}
            for module, result in zip(unique, results)]

def query(payload):
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch', json.dumps(payload).encode(),
                                    {'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(4_000_001)
        if len(data) > 4_000_000: raise ValueError('Oversized advisory response')
        return json.loads(data)

if __name__ == '__main__':
    records = audit(json.loads(Path(sys.argv[1]).read_text()), query)
    Path(sys.argv[2]).write_text(json.dumps(records, indent=2)+'\n')
    affected = [r for r in records if r['advisories']]
    print(f'OSV queried {len(records)} Maven components; {len(affected)} have advisory records.')
    for r in affected: print(r['module'], ', '.join(r['advisories']))
    sys.exit(1 if affected else 0)
