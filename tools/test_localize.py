import unittest
from localize import validate


class LocalizationTests(unittest.TestCase):
    def test_preserves_numbered_placeholders(self):
        self.assertEqual([], validate({"x": "%1$d of %2$d"}, {"x": "%2$d : %1$d"}))
        self.assertTrue(validate({"x": "%1$d jobs"}, {"x": "%1$s tâches"}))

    def test_missing_and_unknown_keys_are_reported(self):
        self.assertEqual(2, len(validate({"one": "One"}, {"two": "Deux"})))

    def test_line_break_markers_are_preserved(self):
        self.assertTrue(validate({"x": "Hello\\nthere"}, {"x": "Bonjour"}))


if __name__ == "__main__":
    unittest.main()
