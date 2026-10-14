import importlib.util
import unittest
import tempfile
import json
from unittest.mock import patch
from pathlib import Path

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('generator', ROOT / 'generate-gamesir-profile.py')
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)

class ProfileTest(unittest.TestCase):
    def test_swap_preserves_every_other_binding_and_rollback(self):
        old = (ROOT / 'mlbb-gamesir-g8-v0.1.txt').read_text()
        self.assertEqual(old, generator.generate('0.1'))
        expected = old.replace('BTN_GAMEPAD 1981.08 946.58 0 150',
                               'BTN_TL2 1981.08 946.58 0 150').replace(
            'STICK_AIM BTN_TL2 ', 'STICK_AIM BTN_GAMEPAD ')
        self.assertEqual(expected, generator.generate())
        self.assertEqual(expected, (ROOT / 'mlbb-gamesir-g8-v0.2.txt').read_text())

    def test_optional_anchors_require_measurements_and_do_not_rewrite_v01(self):
        # Synthetic fixture positions exercise validation, not MLBB calibration.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mlbb-kbm-v0.1.txt').write_text((ROOT / 'mlbb-kbm-v0.1.txt').read_text())
            extra = root / 'mlbb-extra-anchors.json'
            with patch.object(generator, 'ROOT', root):
                baseline = generator.generate('0.1')
                extra.write_text(json.dumps({'chords': [{'modifier': 'BTN_SELECT', 'trigger': 'BTN_TR',
                    'x': 100, 'y': 200}]}))
                with self.assertRaises(ValueError):
                    generator.generate()
                anchors = {'bindings': [{'code': 'DPAD_LEFT', 'x': 100, 'y': 200,
                    'measurement': 'synthetic test fixture'}], 'chords': [{'modifier': 'BTN_SELECT',
                    'trigger': 'BTN_TR', 'x': 300, 'y': 200, 'measurement': 'synthetic test fixture'}]}
                extra.write_text(json.dumps(anchors))
                self.assertIn('CHORD BTN_SELECT BTN_TR 300 200 0', generator.generate())
                self.assertIn('DPAD_LEFT 100 200 0', generator.generate())
                self.assertEqual(baseline, generator.generate('0.1'))
                for bad in (-1, 2280, float('inf')):
                    anchors['bindings'][0]['x'] = bad
                    extra.write_text(json.dumps(anchors))
                    with self.assertRaises(ValueError):
                        generator.generate()

class RearLevelingTest(unittest.TestCase):
    def test_named_measured_actions_keep_rear_identity_and_legacy_profiles(self):
        # These are synthetic validation coordinates, never deployment anchors.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mlbb-kbm-v0.1.txt').write_text((ROOT / 'mlbb-kbm-v0.1.txt').read_text())
            with patch.object(generator, 'ROOT', root):
                baseline = generator.generate()
                with self.assertRaises(ValueError):
                    generator.generate(rear_leveling=True)
                extras = {'actions': {
                    'skill1_level_up': {'x': 100, 'y': 200, 'measurement': 'synthetic test'},
                    'skill2_level_up': {'x': 300, 'y': 400, 'measurement': 'synthetic test'}}}
                path = root / 'mlbb-extra-anchors.json'
                path.write_text(json.dumps(extras))
                generated = generator.generate(rear_leveling=True)
                self.assertEqual(baseline, generator.generate())
                self.assertTrue(generated.startswith(baseline))
                self.assertIn('BTN_THUMBR 100 200 0', generated)
                self.assertIn('BTN_THUMBL 300 400 0', generated)
                self.assertEqual(1, generated.count('BTN_THUMBR'))
                self.assertEqual(1, generated.count('BTN_THUMBL'))
                with self.assertRaises(ValueError):
                    generator.generate('0.1', rear_leveling=True)
                extras['actions']['skill2_level_up'] = extras['actions']['skill1_level_up'].copy()
                path.write_text(json.dumps(extras))
                with self.assertRaises(ValueError):
                    generator.generate(rear_leveling=True)

    def test_swapped_chord_positions_and_missing_measurements_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mlbb-kbm-v0.1.txt').write_text((ROOT / 'mlbb-kbm-v0.1.txt').read_text())
            extras = {'actions': {
                'skill1_level_up': {'x': 100, 'y': 200, 'measurement': 'synthetic test'},
                'skill2_level_up': {'x': 300, 'y': 400, 'measurement': 'synthetic test'}},
                'chords': [{'modifier': 'BTN_SELECT', 'trigger': 'BTN_TR',
                    'x': 300, 'y': 400, 'measurement': 'synthetic test'}]}
            with patch.object(generator, 'ROOT', root):
                path = root / 'mlbb-extra-anchors.json'
                path.write_text(json.dumps(extras))
                with self.assertRaises(ValueError):
                    generator.generate(rear_leveling=True)
                extras['chords'][0].update(x=100, y=200)
                path.write_text(json.dumps(extras))
                self.assertIn('CHORD BTN_SELECT BTN_TR 100 200 0', generator.generate(rear_leveling=True))
                del extras['actions']['skill1_level_up']['measurement']
                path.write_text(json.dumps(extras))
                with self.assertRaises(ValueError):
                    generator.generate(rear_leveling=True)

class RecoveredV03Test(unittest.TestCase):
    def test_recovered_archive_parity_roles_aliases_and_zero_offsets(self):
        import hashlib
        anchors = json.loads((ROOT / 'mlbb-gamesir-g8-v0.3-anchors.json').read_text())
        generated = generator.generate('0.3')
        self.assertEqual(generated, (ROOT / 'mlbb-gamesir-g8-v0.3.txt').read_text())
        self.assertEqual(anchors['provenance']['profile_sha256'], hashlib.sha256(generated.encode()).hexdigest())
        self.assertTrue(generated.startswith(generator.generate('0.2')))
        targets = {line.split()[0]: line.split()[1:] for line in generated.splitlines() if line.startswith(('BTN_SELECT ', 'BTN_THUMBR ', 'BTN_THUMBL '))}
        self.assertEqual({'BTN_SELECT': ['1860', '536', '0'], 'BTN_THUMBR': ['1551', '846.5', '0'], 'BTN_THUMBL': ['1660.5', '646', '0']}, targets)
        self.assertNotEqual(targets['BTN_THUMBR'][:2], targets['BTN_THUMBL'][:2])
        self.assertEqual(1, generated.count('BTN_THUMBR'))
        self.assertEqual(1, generated.count('BTN_THUMBL'))
        with self.assertRaises(ValueError):
            generator.generate('0.3', rear_leveling=True)

    def test_recovered_binding_conflicts_and_base_viewport_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            kbm = (ROOT / 'mlbb-kbm-v0.1.txt').read_text()
            (root / 'mlbb-kbm-v0.1.txt').write_text(kbm)
            (root / 'mlbb-gamesir-g8-v0.3-anchors.json').write_text(
                (ROOT / 'mlbb-gamesir-g8-v0.3-anchors.json').read_text())
            with patch.object(generator, 'ROOT', root):
                extras = root / 'mlbb-extra-anchors.json'
                extras.write_text(json.dumps({'chords': [{'modifier': 'BTN_SELECT',
                    'trigger': 'BTN_TR', 'x': 1551, 'y': 846.5,
                    'measurement': 'synthetic collision test'}]}))
                with self.assertRaisesRegex(ValueError, 'conflicts'):
                    generator.generate('0.3')
                extras.unlink()
                (root / 'mlbb-kbm-v0.1.txt').write_text(kbm.replace(
                    'SCREENSIZE 2280 1080', 'SCREENSIZE 2400 1080'))
                with self.assertRaisesRegex(ValueError, 'viewport'):
                    generator.generate('0.3')

    def test_bad_recovered_measurements_rejected(self):
        import copy
        anchors = json.loads((ROOT / 'mlbb-gamesir-g8-v0.3-anchors.json').read_text())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mlbb-kbm-v0.1.txt').write_text((ROOT / 'mlbb-kbm-v0.1.txt').read_text())
            path = root / 'mlbb-gamesir-g8-v0.3-anchors.json'
            corruptions = [lambda a: a['actions']['skill1_level_up'].update(x=float('nan')),
                lambda a: a['actions']['skill1_level_up'].update(x=True),
                lambda a: a['actions']['skill1_level_up'].update(x=2280),
                lambda a: a['actions']['skill1_level_up'].update(measurement=''),
                lambda a: a['actions'].pop('skill1_level_up'),
                lambda a: a['actions'].update(skill1_level_up=a['actions']['skill2_level_up'].copy()),
                lambda a: a.update(origin=[116, 0])]
            with patch.object(generator, 'ROOT', root):
                for corrupt in corruptions:
                    data = copy.deepcopy(anchors); corrupt(data);path.write_text(json.dumps(data))
                    with self.assertRaises(ValueError):
                        generator.generate('0.3')

class AimRadiusTest(unittest.TestCase):
    def test_tuning_preserves_all_anchors_buttons_and_reference(self):
        original = generator.generate('0.3')
        tuned = generator.with_aim_radius(original, 80)
        self.assertEqual(tuned, (ROOT / 'mlbb-gamesir-g8-v0.3-tuned.txt').read_text())
        for before, after in zip(original.splitlines(), tuned.splitlines()):
            a, b = before.split(), after.split()
            if a[0] == 'STICK_AIM':
                self.assertEqual(b[4], '80')
                self.assertEqual(a[:4] + a[5:], b[:4] + b[5:])
            else:
                self.assertEqual(before, after)
        self.assertEqual(original, generator.generate('0.3'))

    def test_invalid_radius_rejected(self):
        for radius in (0, -1, float('nan'), float('inf'), True, '80'):
            with self.assertRaises(ValueError):
                generator.with_aim_radius(generator.generate('0.3'), radius)

class Phase2Test(unittest.TestCase):
    def test_pending_quick_buy_preserves_tuned_profile_and_standalone_select(self):
        tuned = generator.with_aim_radius(generator.generate('0.3'), 80)
        self.assertEqual(tuned, generator.generate_phase2())
        self.assertEqual(tuned, (ROOT / 'mlbb-gamesir-g8-phase2.txt').read_text())
        self.assertNotIn('DPAD_UP', tuned)
        self.assertIn('BTN_SELECT 1860 536 0', tuned)

    def test_explicit_select_variation_only_replaces_standalone_select(self):
        base = generator.generate_phase2()
        chords = generator.generate_phase2(select_chords=True)
        self.assertEqual(chords, (ROOT / 'mlbb-gamesir-g8-phase2-select-chords.txt').read_text())
        self.assertEqual(set(base.splitlines()) - {'BTN_SELECT 1860 536 0'},
            {line for line in chords.splitlines() if not line.startswith('CHORD ')})
        for expected in ('CHORD BTN_SELECT BTN_TR 1551 846.5 0',
            'CHORD BTN_SELECT BTN_TR2 1660.5 646 0', 'CHORD BTN_SELECT BTN_TL 1860 536 0'):
            self.assertIn(expected, chords)

    def test_quick_buy_requires_record_and_adds_exactly_one_nonrepeating_binding(self):
        # Synthetic coordinates are parser tests, never deployment fixtures.
        measurement = {'name': 'Quick Buy', 'description': 'synthetic unit test only',
            'input': 'DPAD_UP', 'action': 'fixed_touch', 'isEnabled': True,
            'position': {'x': 100, 'y': 200}, 'width': 2280, 'height': 1080,
            'capturedAt': 12345, 'gamePackage': 'com.mobile.legends'}
        baseline = generator.generate_phase2()
        self.assertEqual(baseline + 'DPAD_UP 100 200 0\n', generator.generate_phase2(measurement))
        import copy
        for mutate in (lambda x: x.update(input='DPAD_RIGHT'), lambda x: x.update(isEnabled='true'),
            lambda x: x.update(width=1080), lambda x: x.update(capturedAt=0),
            lambda x: x.update(description=''), lambda x: x.update(repeat_ms=150),
            lambda x: x['position'].update(x=float('nan')), lambda x: x.update(gamePackage='other.game')):
            bad = copy.deepcopy(measurement); mutate(bad)
            with self.assertRaises(ValueError):
                generator.generate_phase2(bad)

if __name__ == '__main__':
    unittest.main()
