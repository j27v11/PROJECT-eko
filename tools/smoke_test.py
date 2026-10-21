"""Android 11 emulator smoke test; never evidence of EKO HDMI/Home behavior."""
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

out = Path("smoke-evidence")
out.mkdir(exist_ok=True)
def adb(*args):
    return subprocess.check_output(["adb", *args], stderr=subprocess.STDOUT, text=True)
def key(code):
    adb("shell", "input", "keyevent", str(code))
    time.sleep(0.25)
def tree(name):
    adb("shell", "uiautomator", "dump", "/sdcard/eko-ui.xml")
    xml = adb("shell", "cat", "/sdcard/eko-ui.xml")
    (out / (name + ".xml")).write_text(xml)
    return ET.fromstring(xml)
def texts(root):
    return [n.attrib.get("text", "") for n in root.iter("node")]
def focused(root):
    return [n.attrib.get("text", "") for n in root.iter("node") if n.attrib.get("focused") == "true"]
def screenshot(name):
    (out / name).write_bytes(subprocess.check_output(["adb", "exec-out", "screencap", "-p"]))

adb("logcat", "-c")
adb("shell", "am", "start", "-W", "-n", "com.eko.home/.MainActivity")
time.sleep(2)
root = tree("home")
assert any(t.startswith("PS5") for t in texts(root)), texts(root)
assert any(t.startswith("Xbox") for t in texts(root)), texts(root)
assert any(t.startswith("PS5") for t in focused(root)), focused(root)
screenshot("home.png")
key(22)
assert any(t.startswith("Xbox") for t in focused(tree("right-focus")))
key(21)
assert any(t.startswith("PS5") for t in focused(tree("left-focus")))
key(23)
assert any("Source/Input" in t for t in texts(tree("input-fallback")))
key(4)
key(4)
for i in range(8):
    key(20)
for i in range(5):
    key(21)
assert "All apps" in focused(tree("all-apps-focus"))
key(23)
root = tree("all-apps-dialog")
assert any(n.attrib.get("resource-id") == "android:id/alertTitle" and n.attrib.get("text") == "All apps" for n in root.iter("node"))
key(4)
key(22)
assert "Inputs" in focused(tree("inputs-focus"))
key(22)
assert "Settings" in focused(tree("settings-focus"))
key(23)
assert "About & verification" in texts(tree("settings-dialog"))
key(4)
key(4)
assert any(t.startswith("PS5") for t in focused(tree("back-to-root")))
(out / "meminfo.txt").write_text(adb("shell", "dumpsys", "meminfo", "com.eko.home"))
logs = adb("logcat", "-d", "-s", "AndroidRuntime:E")
(out / "android-runtime.txt").write_text(logs)
assert "FATAL EXCEPTION" not in logs, logs
screenshot("final-home.png")
(out / "result.json").write_text(json.dumps({"scope": "Android 11 x86_64 emulator only", "checks": ["activity launch", "PS5/Xbox layout", "D-pad left/right focus", "input fallback", "all-apps navigation/dialog", "settings navigation/dialog", "Back to root", "no fatal AndroidRuntime exception"], "eko_hardware_verified": False}, indent=2))
print("PASS: emulator smoke checks. EKO HDMI/Home and ARM32 runtime remain untested.")
