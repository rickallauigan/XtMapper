#!/usr/bin/env python3
"""Build the controller profile from the physically tested KBM action anchors.
No profile-format includes or game-specific runtime code are required.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent

def generate(version="0.2"):
    lines = [line.split() for line in (ROOT / 'mlbb-kbm-v0.1.txt').read_text().splitlines() if line.strip()]
    tags = {line[0]: line for line in lines}
    aim = {line[1]: line for line in lines if line[0] == 'AIM_KEY'}
    result = [' '.join(tags['APPLICATION']), ' '.join(tags['SCREENSIZE']), 'ENABLED']
    for button, source in [('BTN_TL2' if version == '0.2' else 'BTN_GAMEPAD','MOUSE_LEFT'),('BTN_EAST','KEY_G'),('BTN_WEST','KEY_B')]:
        result.append(f'{button} {tags[source][1]} {tags[source][2]} 0' + (' 150' if source == 'MOUSE_LEFT' else ''))
    for button, source in [('BTN_TR','KEY_Q'),('BTN_TR2','KEY_E'),('BTN_TL','KEY_R')]:
        c = aim[source]
        result.append(f'STICK_AIM {button} {c[2]} {c[3]} {c[4]} 1 1 0.15 0')
    # Battle spell reuses its measured fixed anchor and KBM's accepted skill range.
    c = tags['KEY_F']
    result.append(f'STICK_AIM {"BTN_GAMEPAD" if version == "0.2" else "BTN_TL2"} {c[1]} {c[2]} {aim["KEY_Q"][4]} 1 1 0.15 0')
    c = tags['CAMERA']
    result.append(f'STICK_CAMERA {c[1]} {c[2]} {c[3]} {c[4]} 0.15')
    extra_path = ROOT / 'mlbb-extra-anchors.json'
    if extra_path.exists():
        for binding in json.loads(extra_path.read_text()).get('bindings', []):
            result.append(f'{binding["code"]} {binding["x"]} {binding["y"]} 0')
    return '\n'.join(result) + '\n'

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--version', choices=['0.1', '0.2'], default='0.2')
    args = parser.parse_args()
    output = ROOT / f'mlbb-gamesir-g8-v{args.version}.txt'
    expected = generate(args.version)
    if args.check:
        if output.read_text() != expected:
            raise SystemExit('Controller profile differs from the shared KBM anchors; regenerate it.')
        print('GameSir profile matches KBM anchors and measured extra anchors')
    else:
        output.write_text(expected)
