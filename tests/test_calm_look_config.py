"""The calm look and the BRAVIA performance profile reach the TV home (bravia-custom).

bridge/config.py's _home() decides which home settings apply.sh hands the TV (home-config.py) and
the bridge serves: anything it does not know is dropped. Each calm-look key must pass with its
exact values and nothing else, and every key of the BRAVIA profile must survive together.
"""
import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "bridge"))
import config  # noqa: E402

BRAVIA_PROFILE = {
    "look": "night", "lighter": True, "glass": "lite", "moving_tiles": False, "drift": False,
    "hi_res": False, "clock_style": "frosted", "scene_seconds": 60, "updates": "manual",
    "dock": "classic", "idle_photo_seconds": 15, "date_pill": True, "focus_style": "lift",
    "overlay": "cinematic",
}


class CalmLookKeys(unittest.TestCase):
    def test_each_key_passes_its_values(self):
        self.assertIs(config._home({"drift": False})["drift"], False)
        self.assertIs(config._home({"drift": True})["drift"], True)
        self.assertIs(config._home({"date_pill": True})["date_pill"], True)
        self.assertEqual(config._home({"focus_style": "lift"})["focus_style"], "lift")
        self.assertEqual(config._home({"focus_style": "ring"})["focus_style"], "ring")
        self.assertEqual(config._home({"overlay": "cinematic"})["overlay"], "cinematic")
        self.assertEqual(config._home({"overlay": "default"})["overlay"], "default")

    def test_anything_else_is_left_out(self):
        for key, bad in (("drift", "false"), ("drift", 0), ("date_pill", "yes"), ("focus_style", "LIFT"),
                         ("focus_style", True), ("overlay", "dark"), ("overlay", 1)):
            with self.subTest(key=key, value=bad):
                self.assertNotIn(key, config._home({key: bad}))

    def test_absent_by_default(self):
        home = config._home({})
        for key in ("drift", "date_pill", "focus_style", "overlay"):
            self.assertNotIn(key, home)


class BraviaProfile(unittest.TestCase):
    def test_every_profile_key_reaches_the_tv(self):
        home = config._home(dict(BRAVIA_PROFILE))
        for key, value in BRAVIA_PROFILE.items():
            with self.subTest(key=key):
                self.assertIn(key, home, f"{key} was dropped: the TV would never see it")
                self.assertEqual(home[key], value)

    def test_no_video_background(self):
        self.assertNotIn("video_url", config._home(dict(BRAVIA_PROFILE)))


class UpdaterOff(unittest.TestCase):
    """bravia-custom builds have no in-app updates: gradle.properties sets updateRepo= (empty)."""

    def test_gradle_properties_switch_updates_off(self):
        props = (Path(__file__).parents[1] / "tv-home" / "android" / "gradle.properties").read_text(encoding="utf-8")
        lines = [l.strip() for l in props.splitlines() if l.strip() and not l.lstrip().startswith("#")]
        self.assertIn("updateRepo=", lines, "updateRepo must be set and empty")

    def test_release_builds_require_the_permanent_key(self):
        props = (Path(__file__).parents[1] / "tv-home" / "android" / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn("requireReleaseKey=true", props)
        self.assertRegex(props, r"releaseCertSha256=([0-9A-F]{2}:){31}[0-9A-F]{2}")
        gradle = (Path(__file__).parents[1] / "tv-home" / "android" / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('name == "validateSigningRelease" || name == "packageRelease"', gradle)
        self.assertIn("checkReleaseKey()", gradle)
        self.assertNotRegex(gradle, r"storePassword\s*=\s*\"(?!android\")", "no real password in the build file")


if __name__ == "__main__":
    unittest.main()
