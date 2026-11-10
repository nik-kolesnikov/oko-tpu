"""Release updater decisions without a registry, daemon or production data."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "vps" / "pull.sh"
SHA = "a" * 40
REPO = "ghcr.io/example/oko-tpu"
RELEASE = REPO + "/release@sha256:" + "1" * 64
BACKEND = REPO + "/backend@sha256:" + "2" * 64
FRONTEND = REPO + "/frontend@sha256:" + "3" * 64
OLD_BACKEND = REPO + "/backend@sha256:" + "4" * 64
OLD_FRONTEND = REPO + "/frontend@sha256:" + "5" * 64
FAKE_DOCKER = r'''#!/usr/bin/env python3
import json, os, shutil, sys
from pathlib import Path
args = sys.argv[1:]
with open("calls.jsonl", "a") as log:
    log.write(json.dumps({"args": args, "backend": os.environ.get("OKO_BACKEND_IMAGE"),
                          "frontend": os.environ.get("OKO_FRONTEND_IMAGE")}) + "\n")
scenario = os.environ.get("SCENARIO", "")
if args[0] == "pull":
    if scenario == "pull-fails" and args[1].endswith("/frontend:" + "a" * 40):
        sys.exit(1)
elif args[:2] == ["image", "inspect"]:
    image = args[-1]
    print(os.environ["RELEASE"] if "/release:" in image else
          os.environ["BACKEND"] if "/backend:" in image else os.environ["FRONTEND"])
elif args[0] == "create":
    print("fake-container")
elif args[0] == "cp":
    shutil.copytree(os.environ["BUNDLE"], args[-1], dirs_exist_ok=True)
elif "config" in args:
    if scenario == "invalid-config":
        sys.exit(1)
elif "ps" in args:
    print("backend-container")
elif "up" in args:
    if scenario == "update-fails" and os.environ.get("OKO_BACKEND_IMAGE") == os.environ["BACKEND"]:
        sys.exit(1)
'''


class ReleasePullTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / "bin").mkdir()
        (self.root / "state").mkdir()
        (self.root / "bundle").mkdir()
        (self.root / ".env").write_text("OKO_RELEASE_IMAGE=" + REPO + "/release:dev\n")
        (self.root / "compose.yml").write_text("old compose\n")
        (self.root / "Caddyfile").write_text("old caddy\n")
        (self.root / "state/current-image").write_text(OLD_BACKEND + "\n")
        (self.root / "state/current-frontend-image").write_text(OLD_FRONTEND + "\n")
        for name in ("compose.yml", "Caddyfile", "pull.sh"):
            shutil.copy(ROOT / "vps" / name, self.root / "bundle" / name)
        (self.root / "bundle/release.json").write_text(json.dumps({
            "sha": SHA, "backend": REPO + "/backend:" + SHA,
            "frontend": REPO + "/frontend:" + SHA}))
        docker = self.root / "bin/docker"
        docker.write_text(FAKE_DOCKER)
        docker.chmod(0o755)
        self.env = dict(os.environ, OKO_DEPLOY_DIR=str(self.root),
                        OKO_PULL_INSTALL_PATH=str(self.root / "bin/oko-pull"),
                        BUNDLE=str(self.root / "bundle"), RELEASE=RELEASE, BACKEND=BACKEND,
                        FRONTEND=FRONTEND,
                        PATH=str(self.root / "bin") + os.pathsep + os.environ["PATH"])

    def run_pull(self, scenario=""):
        self.env["SCENARIO"] = scenario
        result = subprocess.run(["bash", str(SCRIPT)], env=self.env, capture_output=True, text=True)
        calls = [json.loads(line) for line in (self.root / "calls.jsonl").read_text().splitlines()]
        return result, calls

    def state(self, name):
        return (self.root / "state" / name).read_text().strip()

    def test_success_updates_compose_both_images_and_updater(self):
        result, calls = self.run_pull()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state("current-release"), RELEASE)
        self.assertEqual(self.state("current-image"), BACKEND)
        self.assertEqual(self.state("current-frontend-image"), FRONTEND)
        self.assertEqual((self.root / "compose.yml").read_text(),
                         (self.root / "bundle/compose.yml").read_text())
        self.assertTrue((self.root / "bin/oko-pull").exists())
        updates = [c for c in calls if "up" in c["args"]]
        self.assertEqual(len(updates), 3)
        self.assertTrue(all(c["backend"] == BACKEND and c["frontend"] == FRONTEND for c in updates))

    def test_download_failure_leaves_old_stack_and_config(self):
        result, calls = self.run_pull("pull-fails")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.root / "compose.yml").read_text(), "old compose\n")
        self.assertEqual(self.state("current-image"), OLD_BACKEND)
        self.assertFalse(any("up" in c["args"] for c in calls))

    def test_invalid_config_restores_old_config_without_changing_state(self):
        result, _ = self.run_pull("invalid-config")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.root / "compose.yml").read_text(), "old compose\n")
        self.assertEqual(self.state("current-image"), OLD_BACKEND)

    def test_failed_update_restores_previous_images_and_config(self):
        result, calls = self.run_pull("update-fails")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.root / "compose.yml").read_text(), "old compose\n")
        self.assertEqual(self.state("current-image"), OLD_BACKEND)
        self.assertTrue(any(c["backend"] == OLD_BACKEND and "up" in c["args"] for c in calls))


if __name__ == "__main__":
    unittest.main()
