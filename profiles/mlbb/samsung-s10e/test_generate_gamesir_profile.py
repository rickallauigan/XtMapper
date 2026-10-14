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

if __name__ == '__main__':
    unittest.main()
