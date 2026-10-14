#!/usr/bin/env python3
"""Build the controller profile from the physically tested KBM action anchors.
No profile-format includes or game-specific runtime code are required.
"""
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent

def validate_measurement(entry, width, height):
    if not isinstance(entry, dict) or not isinstance(entry.get('measurement'), str) or not entry['measurement'].strip():
        raise ValueError('Extra anchors require a physical measurement record')
    if not all(type(entry.get(k)) in (int, float) and math.isfinite(entry[k]) for k in ('x', 'y')):
        raise ValueError('Extra coordinates must be finite numbers')
    if not (0 <= entry['x'] < width and 0 <= entry['y'] < height):
        raise ValueError('Extra anchor is outside the calibrated display')

def generate(version="0.2", rear_leveling=False):
    if version == "0.3":
        if rear_leveling:
            raise ValueError('v0.3 already contains measured rear leveling')
        recovered = json.loads((ROOT / 'mlbb-gamesir-g8-v0.3-anchors.json').read_text())
        if recovered.get('viewport') != [2280, 1080] or recovered.get('origin') != [0, 0]:
            raise ValueError('Recovered anchors require the calibrated global display plane')
        actions = recovered.get('actions', {})
        result = generate('0.2').splitlines()
        base_viewport = next((line.split()[1:] for line in result if line.startswith('SCREENSIZE ')), None)
        if base_viewport is None or list(map(float, base_viewport)) != recovered['viewport']:
            raise ValueError('Recovered anchors do not match the base profile viewport')
        centers = set()
        for action, code in [('ultimate_level_up', 'BTN_SELECT'), ('skill1_level_up', 'BTN_THUMBR'), ('skill2_level_up', 'BTN_THUMBL')]:
            entry = actions.get(action)
            validate_measurement(entry, 2280, 1080)
            center = (entry['x'], entry['y'])
            if center in centers:
                raise ValueError('Level-up action anchors must be distinct')
            centers.add(center)
            if any(code in line.split() for line in result):
                raise ValueError('Recovered level-up binding conflicts with an existing utility')
            result.append(f'{code} {entry["x"]} {entry["y"]} 0')
        return '\n'.join(result) + '\n'
    if version not in {"0.1", "0.2"}:
        raise ValueError("Unsupported profile version")
    if rear_leveling and version != "0.2":
        raise ValueError("Rear leveling cannot modify the v0.1 rollback")
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
    if version == '0.2' and extra_path.exists():
        extras = json.loads(extra_path.read_text())
        width, height = map(float, tags['SCREENSIZE'][1:])
        def measured(entry):
            validate_measurement(entry, width, height)
        codes = set()
        for binding in extras.get('bindings', []):
            measured(binding)
            code = binding['code']
            if code not in {'BTN_NORTH', 'DPAD_UP', 'DPAD_DOWN', 'DPAD_LEFT', 'DPAD_RIGHT'} or code in codes:
                raise ValueError('Unsupported or duplicate utility binding')
            codes.add(code)
            result.append(f'{code} {binding["x"]} {binding["y"]} 0')
        triggers = set()
        for chord in extras.get('chords', []):
            measured(chord)
            trigger = chord['trigger']
            if chord['modifier'] != 'BTN_SELECT' or trigger not in {'BTN_TR', 'BTN_TR2', 'BTN_TL'} or trigger in triggers:
                raise ValueError('Unsupported or duplicate skill-leveling chord')
            triggers.add(trigger)
            result.append(f'CHORD BTN_SELECT {trigger} {chord["x"]} {chord["y"]} 0')
    if rear_leveling:
        if not extra_path.exists():
            raise ValueError('Rear leveling requires measured Skill 1 and Skill 2 level-up anchors')
        actions = extras.get('actions', {})
        centers = []
        for action, code in [('skill1_level_up', 'BTN_THUMBR'), ('skill2_level_up', 'BTN_THUMBL')]:
            entry = actions.get(action)
            if not isinstance(entry, dict):
                raise ValueError(f'Missing measured action: {action}')
            measured(entry)
            centers.append((entry['x'], entry['y']))
            result.append(f'{code} {entry["x"]} {entry["y"]} 0')
        if centers[0] == centers[1]:
            raise ValueError('Skill 1 and Skill 2 level-up anchors must be distinct')
        # A supplied SELECT layer must agree with the same labeled action anchors.
        for chord in extras.get('chords', []):
            index = {'BTN_TR': 0, 'BTN_TR2': 1}.get(chord['trigger'])
            if index is not None and (chord['x'], chord['y']) != centers[index]:
                raise ValueError('SELECT leveling chord disagrees with its measured skill action')
    return '\n'.join(result) + '\n'

def with_aim_radius(profile, radius):
    """Tune controller drag range without changing measured touch anchors."""
    if type(radius) not in (int, float) or not math.isfinite(radius) or radius <= 0:
        raise ValueError('Aim radius must be a finite positive number')
    result = []
    for line in profile.splitlines():
        fields = line.split()
        if fields and fields[0] == 'STICK_AIM':
            fields[4] = format(radius, 'g')
            line = ' '.join(fields)
        result.append(line)
    return '\n'.join(result) + '\n'

def generate_phase2(quick_buy=None, select_chords=False):
    """Separate opt-in candidate; rollback references and tuned v0.3 stay untouched."""
    result = with_aim_radius(generate('0.3'), 80).splitlines()
    if select_chords:
        recovered = json.loads((ROOT / 'mlbb-gamesir-g8-v0.3-anchors.json').read_text())
        result = [line for line in result if not line.startswith('BTN_SELECT ')]
        for action, trigger in [('skill1_level_up', 'BTN_TR'), ('skill2_level_up', 'BTN_TR2'), ('ultimate_level_up', 'BTN_TL')]:
            e = recovered['actions'][action]
            result.append(f'CHORD BTN_SELECT {trigger} {e["x"]} {e["y"]} 0')
    if quick_buy is not None:
        if not isinstance(quick_buy, dict) or quick_buy.get('input') != 'DPAD_UP' or quick_buy.get('action') != 'fixed_touch' or quick_buy.get('isEnabled') is not True:
            raise ValueError('Quick Buy requires an enabled DPAD_UP fixed_touch measurement record')
        if quick_buy.get('width') != 2280 or quick_buy.get('height') != 1080 or quick_buy.get('gamePackage') != 'com.mobile.legends':
            raise ValueError('Quick Buy measurement must match the S10e MLBB global plane')
        if type(quick_buy.get('capturedAt')) is not int or quick_buy['capturedAt'] <= 0 or not isinstance(quick_buy.get('name'), str) or not quick_buy['name'].strip():
            raise ValueError('Quick Buy needs its capture timestamp and target name')
        position = quick_buy.get('position')
        if not isinstance(position, dict):
            raise ValueError('Quick Buy needs nested position.x/y')
        validate_measurement(dict(position, measurement=quick_buy.get('description')), 2280, 1080)
        if 'repeat_ms' in quick_buy or any('DPAD_UP' in line.split() for line in result):
            raise ValueError('Quick Buy must not repeat or replace an existing D-pad binding silently')
        result.append(f'DPAD_UP {position["x"]} {position["y"]} 0')
    return '\n'.join(result) + '\n'

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--version', choices=['0.1', '0.2', '0.3'], default='0.2')
    parser.add_argument('--rear-leveling', action='store_true', help='Use measured named actions for R4/R3 and L4/L3')
    parser.add_argument('--output', type=Path, help='Separate output required for rear-leveling; preserves rollback fixtures')
    parser.add_argument('--aim-radius', type=float, help='Controller-only drag radius; requires separate output')
    parser.add_argument('--phase2', action='store_true', help='Generate a separate tuned Phase 2 candidate')
    parser.add_argument('--select-chords', action='store_true', help='Explicit opt-in: SELECT modifier replaces standalone Ultimate (+)')
    parser.add_argument('--quick-buy-target', type=Path, help='Explicit measured target record, not a structured profile import')
    args = parser.parse_args()
    if (args.select_chords or args.quick_buy_target) and not args.phase2:
        parser.error('Phase 2 options require --phase2')
    if args.phase2 and (args.rear_leveling or args.aim_radius is not None or args.output is None or args.output.resolve() in {ROOT / f'mlbb-gamesir-g8-v{v}.txt' for v in ('0.1', '0.2', '0.3', '0.3-tuned')}):
        parser.error('--phase2 requires separate --output and cannot rewrite reference profiles')
    if args.rear_leveling and (args.output is None or args.output.resolve() in {ROOT / 'mlbb-gamesir-g8-v0.1.txt', ROOT / 'mlbb-gamesir-g8-v0.2.txt'}):
        parser.error('--rear-leveling requires a separate --output, never a rollback fixture')
    if args.aim_radius is not None and (args.output is None or args.output.resolve() in {ROOT / f'mlbb-gamesir-g8-v{v}.txt' for v in ('0.1', '0.2', '0.3')}):
        parser.error('--aim-radius requires a separate --output, preserving reference fixtures')
    output = ROOT / f'mlbb-gamesir-g8-v{args.version}.txt'
    output = args.output or output
    expected = generate_phase2(json.loads(args.quick_buy_target.read_text()) if args.quick_buy_target else None, args.select_chords) if args.phase2 else generate(args.version, rear_leveling=args.rear_leveling)
    if args.aim_radius is not None:
        expected = with_aim_radius(expected, args.aim_radius)
    if args.check:
        if output.read_text() != expected:
            raise SystemExit('Controller profile differs from the shared KBM anchors; regenerate it.')
        print('GameSir profile matches KBM anchors and measured extra anchors')
    else:
        output.write_text(expected)
