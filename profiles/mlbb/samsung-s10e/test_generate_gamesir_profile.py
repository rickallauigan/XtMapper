import importlib.util
import unittest
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

if __name__ == '__main__':
    unittest.main()
