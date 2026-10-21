"""Fail CI if the deliverable violates the launcher's packaging constraints."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

apk = Path("app/build/outputs/apk/debug/app-debug.apk")
output = Path("verification")
output.mkdir(exist_ok=True)
sdk = Path(os.environ["ANDROID_HOME"])
build_tools = sdk / "build-tools" / "35.0.0"
def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.STDOUT)
badging = run(build_tools / "aapt", "dump", "badging", apk)
permissions = run(build_tools / "aapt", "dump", "permissions", apk)
signature = run(build_tools / "apksigner", "verify", "--verbose", "--print-certs", apk)
assert "package: name='com.eko.home'" in badging, badging
assert "sdkVersion:'30'" in badging, badging
assert "targetSdkVersion:'30'" in badging, badging
assert "leanback-launchable-activity:" in badging, badging
assert "uses-permission:" not in permissions, permissions
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert "classes.dex" in names
    assert "AndroidManifest.xml" in names
    assert not any(n.startswith("lib/") for n in names), "Native libraries would need a separate ARM32 audit"
    assert archive.testzip() is None
assert apk.stat().st_size < 5 * 1024 * 1024, "Unexpected size regression"
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(output / "SHA256SUMS").write_text(digest + "  app-debug.apk\n")
(output / "apk-badging.txt").write_text(badging)
(output / "apk-permissions.txt").write_text(permissions)
(output / "apk-signature.txt").write_text(signature)
report = {"commit": os.environ.get("GITHUB_SHA", "local"), "apk_bytes": apk.stat().st_size, "sha256": digest, "min_sdk": 30, "target_sdk": 30, "native_libraries": False, "requested_permissions": [], "hardware_verified": False}
(output / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a") as summary:
    summary.write("## EKO Home APK verified\n\nCommit: " + report["commit"] + "\n\nSize: " + str(report["apk_bytes"]) + " bytes\n\nSHA-256: \x60" + digest + "\x60\n\nValid APK/signature, Android 11 minimum, no permissions or native libraries. Hardware HDMI/Home behavior remains unverified.\n")
print(json.dumps(report, indent=2))
