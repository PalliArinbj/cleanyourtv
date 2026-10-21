"""The PowerShell twins of the install and disable scripts, run by Windows PowerShell 5.1 against a simulated TV.

README tells Windows users to run launcher\\build-and-install.ps1 and tools\\disable.ps1 with `powershell`, which is
Windows PowerShell 5.1. Its rules for native commands, redirected stderr and null values are what these scripts have
to work around, and the POSIX tests (test_launcher_handover, test_home_guard) never run them there. So this file runs
the real .ps1 scripts under powershell.exe, with tools\\tv.py replaced by a fake that answers from a JSON file: no
TV, no gradle and no SDK are involved. Windows only; CI runs it in the windows job.

Run from the repository root: python -m unittest discover -s tests -p test_powershell_scripts.py -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import venv


ROOT = Path(__file__).resolve().parents[1]
POWERSHELL = shutil.which("powershell") if os.name == "nt" else None
STOCK = "com.google.android.tvlauncher"
STOCK_HOME = f"{STOCK}/.MainActivity"
NEW_HOME = "com.gathin.tvhome/.MainActivity"
# Whatever the caller's shell has set must not steer the scripts under test.
SCRUBBED = ("TV_HOST", "TV_PORT", "STOCK_LAUNCHER", "FORCE_HOME", "FORCE_SETUPWRAITH", "TVINPUT_ALLOW",
            "JAVA_HOME", "ANDROID_HOME")

# Stands in for tools\tv.py during the launcher install: a TV that answers shell commands and keeps the state
# the script changes. After drop_after_handover, the TV stops answering the Home query, as a TV that fell off
# Wi-Fi in the middle of the handover would.
INSTALL_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]
with open(STATE_PATH) as f:
    state = json.load(f)


class LinkLost(Exception):
    pass


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def run(command):
    if "getprop ro.build.version.sdk" in command:
        return state["sdk"] + "\n"
    if "pm install" in command:
        return state["install_output"]
    if "am start" in command:
        if state["start_fails"]:
            return ("Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\n"
                    "Error: Activity not started, unable to resolve Intent\n")
        return ("Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\n"
                "Status: ok\nActivity: com.gathin.tvhome/.MainActivity\n")
    if "pm disable-user" in command:
        state["stock_disabled"] = True
        if state["drop_after_handover"]:
            state["link_lost"] = True
        return ""
    if "pm enable" in command:
        if state["enable_fails"]:
            return "Error: java.lang.SecurityException\n"
        state["stock_disabled"] = False
        return ""
    if "set-home-activity" in command:
        if not state["set_home_fails"]:
            state["home"] = STOCK_HOME if "resolve-activity" in command else NEW_HOME
        return ""
    if "resolve-activity" in command:
        if state["link_lost"]:
            raise LinkLost()
        # A disabled package is not a HOME candidate, so the answer falls back to the chooser.
        if state["stock_disabled"] and state["home"] == STOCK_HOME:
            return "android\n"
        return state["home"] + "\n"
    return ""


def main():
    args = sys.argv[1:]
    if args == ["--batch"]:
        commands = [line.strip() for line in sys.stdin
                    if line.strip() and not line.strip().startswith("#")]
    else:
        commands = args[-1:]
    for command in commands:
        state["commands"].append(command)
        try:
            sys.stdout.write(run(command))
        except LinkLost:
            save()
            sys.stderr.write("adb: connection reset by peer\n")
            return 2
        sys.stdout.flush()
    save()
    return 0


sys.exit(main())
'''.replace("STOCK_HOME", repr(STOCK_HOME)).replace("NEW_HOME", repr(NEW_HOME))

# Stands in for tools\tv.py during tools\disable.ps1, as in test_home_guard: answers the queries the script and
# recovery.py make, and records what a batch would send.
DISABLE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]
with open(STATE_PATH) as f:
    state = json.load(f)


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if command.startswith("cmd package query-activities"):
        if state["claims_fail"]:
            sys.exit(1)
        return "".join(f"  {c if '/' in c else c + '/.MainActivity'}\n" for c in state["claims"])
    if command.startswith("cmd package resolve-activity"):
        return (state["home"] or "") + "\n"
    if command.startswith("settings get global "):
        return "null\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    # recovery.py imports this module and calls connect() directly.
    return Device()


def main():
    args = sys.argv[1:]
    if "--batch" in args:
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                state["commands"].append(line)
        save()
        return 0
    if state["home_fails"] and args[-1].startswith("cmd package resolve-activity"):
        # The guard's own query is dropped while the connections after it work, as on a flaky link.
        sys.stderr.write("adb: connection reset by peer\n")
        return 1
    print(answer(args[-1]), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''

# Stands in for tools\b64push.py: the upload itself is not what these tests are about.
FAKE_PUSH = 'print("md5 remote: 0 local: 0 MATCH")\n'


@unittest.skipUnless(POWERSHELL, "Windows PowerShell 5.1 runs only on Windows")
class PowerShellCase(unittest.TestCase):
    def make_repo(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        # The scripts call tools\venv\Scripts\python.exe, which has to be a real Windows program here, unlike the
        # shell wrappers the POSIX tests use. A venv without pip is enough: the fakes import nothing outside the
        # standard library.
        venv.create(self.tools / "venv", with_pip=False)
        # recovery.py writes its record under the home folder, which Python finds through USERPROFILE.
        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"

    def powershell(self, script, *args, env=None, cwd=None):
        full = {k: v for k, v in os.environ.items() if k not in SCRUBBED}
        full.update(FAKE_TV_STATE=str(self.state_path), USERPROFILE=str(self.home), HOME=str(self.home),
                    **(env or {}))
        result = subprocess.run(
            [POWERSHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(script), *args],
            cwd=cwd or self.repo, env=full, capture_output=True, text=True, encoding="utf-8", errors="replace",
            timeout=300,
        )
        with open(self.state_path) as f:
            return result, json.load(f)

    @staticmethod
    def output(result):
        return result.stdout + result.stderr


class InstallScriptTest(PowerShellCase):
    def setUp(self):
        self.make_repo()
        self.launcher = self.repo / "launcher"
        (self.launcher / "app").mkdir(parents=True)
        shutil.copy2(ROOT / "launcher" / "build-and-install.ps1", self.launcher)
        (self.launcher / "app" / "build.gradle").write_text(
            "android {\n    defaultConfig {\n        minSdk 26\n    }\n}\n")
        # No JDK and no Android SDK: the build only has to leave an APK to push, and a marker to say it ran.
        self.marker = self.base / "built"
        (self.launcher / "gradlew.bat").write_text(
            "@echo off\n"
            "if not exist app\\build\\outputs\\apk\\release mkdir app\\build\\outputs\\apk\\release\n"
            "echo apk> app\\build\\outputs\\apk\\release\\app-release.apk\n"
            f"echo built> \"{self.marker}\"\n")
        java = self.base / "java" / "bin"
        java.mkdir(parents=True)
        (java / "java.exe").write_text("")
        (self.tools / "tv.py").write_text(INSTALL_TV)
        (self.tools / "b64push.py").write_text(FAKE_PUSH)

    def install(self, *args, **overrides):
        state = {
            "sdk": "34",
            "install_output": "Success\n",
            "home": STOCK_HOME,
            "stock_disabled": False,
            "start_fails": False,
            "set_home_fails": False,
            "enable_fails": False,
            "drop_after_handover": False,
            "link_lost": False,
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        return self.powershell(self.launcher / "build-and-install.ps1", "-TvHost", "192.168.1.50", *args,
                               env={"JAVA_HOME": str(self.base / "java"),
                                    "ANDROID_HOME": str(self.base / "android-sdk")},
                               cwd=self.launcher)

    def test_success_hands_home_over(self):
        result, state = self.install()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn("Installed.", self.output(result))
        commands = state["commands"]
        start = next(i for i, c in enumerate(commands) if "am start" in c)
        disable = next(i for i, c in enumerate(commands) if "pm disable-user" in c)
        self.assertLess(start, disable, "the launcher is started before the stock Home is disabled")
        self.assertTrue(state["stock_disabled"])
        self.assertEqual(state["home"], NEW_HOME)

    def test_launcher_that_does_not_start_leaves_stock_home_enabled(self):
        result, state = self.install(start_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not start", self.output(result))
        self.assertNotIn("Installed.", self.output(result))
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))
        self.assertFalse(state["stock_disabled"])
        # Home must not point at a launcher that does not start, even with the stock one still enabled.
        self.assertFalse(any("set-home-activity" in c for c in state["commands"]), state["commands"])
        self.assertEqual(state["home"], STOCK_HOME)

    def test_home_is_set_after_the_launcher_starts(self):
        result, state = self.install()
        self.assertEqual(result.returncode, 0, self.output(result))
        commands = state["commands"]
        start = next(i for i, c in enumerate(commands) if "am start" in c)
        homes = [i for i, c in enumerate(commands) if "set-home-activity" in c and NEW_HOME in c]
        self.assertTrue(homes, commands)
        self.assertLess(start, homes[0], "the home preference follows a launcher that came up")

    def rescue_file(self):
        files = list(self.home.glob("cleanyourtv-handover-*.txt"))
        self.assertLessEqual(len(files), 1)
        return files[0].read_text() if files else None

    def test_lost_connection_reports_incomplete_recovery(self):
        # The TV stops answering right after the stock launcher is disabled: the Home check gets no answer,
        # which used to stop Windows PowerShell on .Trim() of nothing before the rollback ran.
        result, state = self.install(drop_after_handover=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("null-valued", self.output(result))
        self.assertIn("stopped answering", self.output(result))
        self.assertIn("recovery is incomplete", self.output(result))
        self.assertTrue(any(f"pm enable --user 0 {STOCK}" in c for c in state["commands"]))
        text = self.rescue_file()
        self.assertIsNotNone(text, "an unreachable TV needs the recovery commands on disk")
        self.assertIn(f"pm enable --user 0 {STOCK}", text or "")

    def test_home_that_stays_on_another_launcher_is_rolled_back(self):
        result, state = self.install(set_home_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("owns Home again", self.output(result))
        self.assertIn("setupwraith", self.output(result))
        self.assertFalse(state["stock_disabled"])
        self.assertIsNone(self.rescue_file())

    def test_failed_rollback_is_not_reported_as_recovered(self):
        result, state = self.install(set_home_fails=True, enable_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("owns Home again", self.output(result))
        self.assertIn("recovery is incomplete", self.output(result))
        self.assertTrue(any(f"pm enable --user 0 {STOCK}" in c for c in state["commands"]))
        self.assertIsNotNone(self.rescue_file())

    def test_a_tv_below_min_sdk_is_stopped_before_the_build(self):
        result, state = self.install(sdk="25")
        self.assertNotEqual(result.returncode, 0)
        output = " ".join(self.output(result).split())   # PowerShell wraps a long error message
        self.assertIn("SDK 25", output)
        self.assertIn("minSdk 26", output)
        self.assertIn("Nothing was built", output)
        self.assertFalse(self.marker.exists(), "the APK must not be built for a set that refuses it")
        self.assertEqual(state["commands"], ["getprop ro.build.version.sdk"],
                         "the SDK probe is all that may reach the TV")

    def test_an_unanswered_getprop_does_not_block_the_install(self):
        result, _ = self.install(sdk="")
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertTrue(self.marker.exists())

    def test_install_refused_for_the_sdk_names_the_cause(self):
        result, state = self.install(
            install_output="Failure [INSTALL_FAILED_OLDER_SDK: Failed parse during installPackageLI: "
                           "requires newer sdk version]\n")
        self.assertNotEqual(result.returncode, 0)
        output = " ".join(self.output(result).split())
        self.assertIn("minSdk 26", output)
        self.assertIn("stock launcher was left untouched", output)
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))


class DisableScriptTest(PowerShellCase):
    def setUp(self):
        self.make_repo()
        for name in ("disable.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(DISABLE_TV)
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text(f"# My TV\ncom.example.before\n{STOCK}\ncom.example.after\n")

    def disable(self, extra_env=None, **overrides):
        state = {
            "installed": ["com.example.before", "com.example.after", STOCK],
            "disabled": [],
            "home": STOCK_HOME,
            "claims": [STOCK],
            "home_fails": False,
            "claims_fail": False,
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        result, state = self.powershell(self.tools / "disable.ps1", "-TvHost", "192.168.1.50",
                                        str(self.list_path), env=extra_env)
        return result, state["commands"]

    def records(self):
        return list((self.home / ".cleanyourtv").glob("recovery-*.json"))

    def test_the_owner_of_home_is_skipped_when_it_is_the_only_home(self):
        result, sent = self.disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
        self.assertEqual(sent[:2], ["pm disable-user --user 0 com.example.before",
                                    "pm disable-user --user 0 com.example.after"])
        self.assertIn(f"Skipping {STOCK}", self.output(result))
        self.assertEqual(len(self.records()), 1, "the record is written under the test's home folder")

    def test_a_second_home_lets_the_list_disable_the_owner(self):
        result, sent = self.disable(installed=["com.example.before", "com.example.after", STOCK,
                                               "com.gathin.tvhome"],
                                    home=NEW_HOME, claims=["com.gathin.tvhome", STOCK])
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_a_tv_that_cannot_say_who_owns_home_stops_the_run(self):
        result, sent = self.disable(home_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(sent, [], "nothing may reach the TV without the Home check")
        self.assertIn("could not ask the TV which app owns Home", " ".join(self.output(result).split()))
        self.assertEqual(self.records(), [], "no record of a run that did not start")


if __name__ == "__main__":
    unittest.main()
