"""Exercise deployment decisions without a daemon, registry or production data."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "vps" / "pull.sh"
NEW = "registry.example.org/oko/backend@sha256:" + "a" * 64
OLD = "registry.example.org/oko/backend@sha256:" + "b" * 64
FAKE_DOCKER = r'''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args = sys.argv[1:]
image = os.environ.get("OKO_BACKEND_IMAGE", "")
with open("calls.jsonl", "a") as log:
    log.write(json.dumps({"args": args, "image": image}) + "\n")
scenario = os.environ.get("SCENARIO", "")
if args[0] == "pull":
    sys.exit(1 if scenario == "pull-fails" else 0)
if args[:2] == ["image", "inspect"]:
    print(os.environ["CANDIDATE"])
elif args[0] == "inspect":
    print("healthy")
elif "config" in args:
    print(json.dumps({"services": {"backend": {"image": "registry.example.org/oko/backend:dev"}}}))
elif "ps" in args:
    if Path("state/current-image").exists():
        print("backend-container")
elif "up" in args and args[-2:] == ["db", "backend"]:
    if scenario == "rollback-fails" or (scenario == "update-fails" and image == os.environ["CANDIDATE"]):
        sys.exit(1)
'''


class PullDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "bin").mkdir()
        (self.root / "state").mkdir()
        docker = self.root / "bin" / "docker"
        docker.write_text(FAKE_DOCKER)
        docker.chmod(0o755)
        self.env = dict(os.environ, OKO_DEPLOY_DIR=str(self.root), CANDIDATE=NEW,
                        PATH=str(self.root / "bin") + os.pathsep + os.environ["PATH"])
        self.env.pop("OKO_BACKEND_IMAGE", None)

    def run_pull(self, scenario="", current=None):
        if current:
            (self.root / "state/current-image").write_text(current + "\n")
        self.env["SCENARIO"] = scenario
        result = subprocess.run(["bash", str(SCRIPT)], env=self.env, capture_output=True, text=True)
        calls = [json.loads(line) for line in (self.root / "calls.jsonl").read_text().splitlines()]
        return result, calls

    def state(self, name="current-image"):
        return (self.root / "state" / name).read_text().strip()

    def test_first_install_pins_digest_and_checks_proxy(self):
        result, calls = self.run_pull()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state(), NEW)
        updates = [c for c in calls if "up" in c["args"]]
        self.assertEqual(len(updates), 3)
        self.assertTrue(all(c["image"] == NEW for c in updates))
        self.assertTrue(all("--no-build" in c["args"] for c in updates))
        self.assertTrue(any("exec" in c["args"] for c in calls))

    def test_success_retains_previous_digest(self):
        result, _ = self.run_pull(current=OLD)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state(), NEW)
        self.assertEqual(self.state("previous-image"), OLD)

    def test_unchanged_healthy_image_does_not_force_restart(self):
        result, calls = self.run_pull(current=NEW)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any("--force-recreate" in c["args"] for c in calls))
        self.assertTrue(any("up" in c["args"] for c in calls), "Must recover stopped proxies")

    def test_registry_failure_leaves_running_stack_and_state_untouched(self):
        result, calls = self.run_pull("pull-fails", current=OLD)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state(), OLD)
        self.assertFalse(any("up" in c["args"] for c in calls))

    def test_failed_update_restores_previous_image_but_reports_failure(self):
        result, calls = self.run_pull("update-fails", current=OLD)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state(), OLD)
        self.assertIn("Previous application image restored", result.stderr)
        self.assertTrue(any(c["image"] == OLD and "up" in c["args"] for c in calls))

    def test_failed_rollback_is_reported_and_does_not_mark_candidate_current(self):
        result, _ = self.run_pull("rollback-fails", current=OLD)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state(), OLD)
        self.assertIn("Rollback failed", result.stderr)

    def test_missing_digest_never_changes_stack(self):
        self.env["CANDIDATE"] = "<no value>"
        result, calls = self.run_pull(current=OLD)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any("up" in c["args"] for c in calls))


if __name__ == "__main__":
    unittest.main()
