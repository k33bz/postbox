#!/usr/bin/env python3
"""Boot a real Fabric server with the freshly built postbox jar and run scripted checks.

The unit tests cover postage math, the config sanitizer and the mail-store file handling; this
answers "does the jar actually load and work on a real server of this Minecraft version". No bots
and no client: every check goes in through the server console, the config/ files postbox reads and
writes, and the server's own answers. Standard library only. (Adapted from k33bz/pathways.)

Boot 1, fresh world, with a config/postbox.json written beforehand:
  boot      postbox logs its init line, the BlockItem mixin applies (bootstrap), the server is Done
  config    out-of-range values in the file come back clamped (maxQueued -5 -> 1)
  store     config/postbox_mail.json is materialized
  commands  /mail and /postbox answer (players only, from the console)
  outbox    a spool request with a valid UUID is ingested and lands in the recipient's queue as a
            written book carrying the text (ItemStack codec + book components on this version);
            one with a malformed UUID is dropped without crashing the tick
  lost mail a slain wandering trader drops the Lost Mail bundle (traderMailChance 1.0 here)
  couriers  a leftover express courier (no scene running) is removed when it loads
Boot 2, same world, after garbage was written over config/postbox_mail.json:
  store     the server still starts, the damaged store is kept as postbox_mail.json.corrupt-*
            byte for byte, and a fresh store is written

Placing a mailbox, the send dialog and the inbox GUI need a player; that stays with the mineflayer
harness. Which Minecraft: the newest stable release of this line. Writes
build/server-test/versions.json for the README badges and a Markdown summary to
$GITHUB_STEP_SUMMARY. Exit code 0 = every check passed.

Usage: python3 scripts/server_test.py [--jar build/libs/x.jar] [--workdir build/server-test]
"""
import argparse
import glob
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "k33bz/postbox server-test (github actions)"}

# Log lines that mean the mod (or the server) failed.
FATAL = re.compile(
    r"Mixin apply failed|InvalidInjectionException|InvalidMixinException|MixinApplyError"
    r"|Critical injection failure|Exception ticking world|Encountered an unexpected exception"
    r"|Could not execute entrypoint|Error executing task|Unbound values in registry"
    r"|Failed to load registries|Error generating chunk|Exception in server tick loop")

INIT = r"\[postbox\] v\S+ initialized"
GARBAGE = '{"boxes": [{"id": "ci", "owner": '   # a store cut off mid-write


# ---------------------------------------------------------------- downloads

def http_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def download(url, dest):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r, \
            open(dest, "wb") as f:
        shutil.copyfileobj(r, f)


def props():
    out = {}
    with open(os.path.join(ROOT, "gradle.properties")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def modrinth_file(project, mc, want_version=None):
    """Primary file of a Modrinth project's Fabric build for `mc` (exact version if given)."""
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    versions = http_json(f"https://api.modrinth.com/v2/project/{project}/version?{q}")
    if not versions:
        return None
    pick = next((v for v in versions if v["version_number"] == want_version), None) if want_version else None
    pick = pick or versions[0]
    f = next((x for x in pick["files"] if x.get("primary")), pick["files"][0])
    return pick["version_number"], f["url"], f["filename"]


# ---------------------------------------------------------------- server process

class Server:
    def __init__(self, workdir):
        self.workdir = workdir
        self.lines = queue.Queue()
        self.log = []
        self.fatal = []
        self.proc = None

    def start(self):
        self.proc = subprocess.Popen(
            ["java", "-Xms1G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=self.workdir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1)
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            line = line.rstrip("\n")
            self.log.append(line)
            if FATAL.search(line):
                self.fatal.append(line)
            self.lines.put(line)
        self.lines.put(None)  # process ended

    def send(self, cmd):
        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()

    def wait_for(self, pattern, timeout):
        """Next line matching `pattern` (regex) within `timeout` s, else None."""
        rx = re.compile(pattern)
        end = time.time() + timeout
        while time.time() < end:
            try:
                line = self.lines.get(timeout=max(0.05, end - time.time()))
            except queue.Empty:
                break
            if line is None:
                return None
            if rx.search(line):
                return line
        return None

    def drain(self):
        while True:
            try:
                self.lines.get_nowait()
            except queue.Empty:
                return

    def test(self, cmd):
        """Run an `execute if ...` command; True = passed, False = failed, None = no answer."""
        self.drain()
        self.send(cmd)
        line = self.wait_for(r"Test (passed|failed)", 10)
        if line is None:
            return None
        return "Test passed" in line

    def poll(self, cmd, want, timeout):
        """Repeat `cmd` until it answers `want` or `timeout` s pass. Returns the last answer."""
        end = time.time() + timeout
        got = None
        while time.time() < end:
            got = self.test(cmd)
            if got == want:
                return got
            time.sleep(1)
        return got

    def alive(self):
        self.drain()
        self.send("list")
        return self.wait_for(r"There are \d+ of a max", 15) is not None

    def stop(self):
        if self.proc and self.proc.poll() is None:
            try:
                self.send("stop")
                self.proc.wait(timeout=60)
            except Exception:
                self.proc.kill()



def test_minecraft(line):
    """Newest stable release of this Minecraft line: '26.1' -> '26.1.2' when that exists."""
    stable = [v["version"] for v in http_json("https://meta.fabricmc.net/v2/versions/game") if v["stable"]]
    same = [v for v in stable if v == line or v.startswith(line + ".")]
    return same[0] if same else line   # meta lists newest first


# What the server test actually booted with, for scripts/publish_badges.py (and humans).
TESTED = {}


def setup(workdir, jars, p, mc):
    """Fresh server dir with fabric loader, the newest fabric-api for `mc`, and `jars`."""
    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(os.path.join(workdir, "mods"), exist_ok=True)
    installer = next(i["version"] for i in http_json("https://meta.fabricmc.net/v2/versions/installer") if i["stable"])
    download(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{p['loader_version']}/{installer}/server/jar",
             os.path.join(workdir, "fabric-server-launch.jar"))
    # The newest fabric-api for this Minecraft version, as a real server would run, not the pin.
    api = modrinth_file("fabric-api", mc)
    if api is None:
        raise SystemExit(f"no fabric-api build on Modrinth for {mc}")
    download(api[1], os.path.join(workdir, "mods", api[2]))
    TESTED["fabric_api_tested"] = api[0]
    for jar in jars:
        shutil.copy(jar, os.path.join(workdir, "mods", os.path.basename(jar)))
    with open(os.path.join(workdir, "eula.txt"), "w") as f:
        f.write("eula=true\n")
    with open(os.path.join(workdir, "server.properties"), "w") as f:
        f.write("\n".join([
            "online-mode=false", "level-type=minecraft\\:flat", "spawn-protection=0",
            "view-distance=4", "simulation-distance=4",
            # Default 60: an empty server stops ticking, and nothing here ever joins.
            "pause-when-empty-seconds=-1",
            "enable-command-block=false", "sync-chunk-writes=false", "server-port=25597", ""]))
    return installer, api


def boot(workdir, results, label, init_pattern, timeout):
    """Start a server and wait for postbox's init line and Done. Returns (Server, ready).

    The Server comes back even when the boot failed, so its log (the crash) is kept."""
    s = Server(workdir)
    s.start()
    init = s.wait_for(init_pattern, timeout)
    results.append((f"{label}: postbox initialized", init is not None, init or _last_error(s)))
    done = s.wait_for(r"Done \(\d", timeout) if init else None
    results.append((f"{label}: server reached Done", done is not None, done or ("" if not init else _last_error(s))))
    return s, done is not None


def _last_error(s):
    """The most telling line of a failed boot, for the summary table."""
    for line in reversed(s.log):
        if re.search(r"ERROR|Exception|Incompatible|requires|Caused by", line):
            return line[-200:]
    return s.log[-1][-200:] if s.log else "server produced no output"


# ---------------------------------------------------------------- scenarios

# Only the server's own command errors count as a bad answer: postbox logs warnings on purpose
# (a malformed outbox request, a corrupt store) while the checks run.
def answer(s, cmd, ok_pattern, bad_pattern=r"Unknown or incomplete command|Incorrect argument|Unknown command", timeout=20):
    """Send a console command; (True|False|None, line): matched ok, matched bad, or no answer."""
    s.drain()
    s.send(cmd)
    rx_ok, rx_bad = re.compile(ok_pattern), re.compile(bad_pattern)
    end = time.time() + timeout
    while time.time() < end:
        line = s.wait_for(r".", max(0.1, end - time.time()))
        if line is None:
            break
        if rx_ok.search(line):
            return True, line
        if rx_bad.search(line):
            return False, line
    return None, ""


def read_json(path):
    try:
        with open(path) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def wait_until(fn, timeout, step=0.5):
    end = time.time() + timeout
    while time.time() < end:
        got = fn()
        if got:
            return got
        time.sleep(step)
    return fn()


def run_first_boot(s, results, workdir):
    def check(name, ok, detail=""):
        results.append((name, bool(ok), detail))
        print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}", flush=True)

    cfg_dir = os.path.join(workdir, "config")
    cfg = read_json(os.path.join(cfg_dir, "postbox.json")) or {}
    check("config: out-of-range values come back clamped (maxQueued -5 -> 1)",
          cfg.get("maxQueued") == 1 and cfg.get("traderMailChance") == 1.0 and "postageBase" in cfg,
          json.dumps(cfg)[:120])
    store = read_json(os.path.join(cfg_dir, "postbox_mail.json"))
    check("store: postbox_mail.json materialized", isinstance(store, dict) and "boxes" in store, str(store)[:80])

    ok, line = answer(s, "mail", r"Players only")
    check("commands: /mail answers", ok, line[-80:])
    ok, line = answer(s, "postbox take", r"Players only")
    check("commands: /postbox take answers", ok, line[-80:])

    # Outbox spool: one valid request, one with a malformed recipient UUID
    out = os.path.join(cfg_dir, "postbox_outbox")
    os.makedirs(out, exist_ok=True)
    to = str(uuid.uuid4())
    with open(os.path.join(out, "001-valid.json"), "w") as f:
        json.dump({"toUuid": to, "toName": "CiTester", "from": "The Gravekeeper",
                   "body": "hello from CI"}, f)
    with open(os.path.join(out, "002-bad.json"), "w") as f:
        json.dump({"toUuid": "not-a-uuid", "toName": "Nobody", "from": "CI", "body": "boom"}, f)

    def queued():
        st = read_json(os.path.join(cfg_dir, "postbox_mail.json")) or {}
        letters = (st.get("queues") or {}).get(to) or []
        return letters[0] if letters else None

    letter = wait_until(queued, 30)
    stack = json.dumps((letter or {}).get("stack"))
    check("outbox: a valid request is delivered to the recipient's queue",
          letter is not None and letter.get("from") == "The Gravekeeper", str(letter)[:120])
    check("outbox: the letter is a written book carrying the text",
          "written_book" in stack and "hello from CI" in stack, stack[:160])
    gone = wait_until(lambda: not os.listdir(out), 10)
    dropped = any("malformed toUuid" in l for l in s.log)
    check("outbox: a malformed UUID is dropped, not crashed on", gone and dropped and s.alive(),
          f"spool empty: {gone}, logged: {dropped}")

    # Lost Mail: a slain wandering trader drops the bundle (chance 1.0 in this config)
    s.send("forceload add 0 0")
    time.sleep(2)
    s.send("summon minecraft:wandering_trader 0 -60 0 {NoAI:1b,Tags:[\"ci_trader\"]}")
    time.sleep(1)
    s.send("kill @e[tag=ci_trader]")
    dropped_mail = s.poll('execute if entity @e[type=item,nbt={Item:{id:"minecraft:bundle"}}]', True, 10)
    check("lost mail: a slain wandering trader drops the bundle", dropped_mail,
          "log: " + str(any("dropped Lost Mail" in l for l in s.log)))
    s.send("kill @e[type=item]")

    # Couriers: a leftover courier (its scene is not running) is removed when it loads
    s.send('summon minecraft:wandering_trader 3 -60 3 {NoAI:1b,Tags:["postbox_courier","postbox_courier_deadr0"]}')
    gone = s.poll("execute if entity @e[tag=postbox_courier]", False, 10)
    check("couriers: a leftover courier is removed on load", gone is False,
          "log: " + str(any("leftover express courier" in l for l in s.log)))
    time.sleep(3)
    check("ticks run cleanly", s.alive() and not s.fatal, "; ".join(s.fatal[:2]))


def run_second_boot(s, results, workdir):
    def check(name, ok, detail=""):
        results.append((name, bool(ok), detail))
        print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}", flush=True)

    cfg_dir = os.path.join(workdir, "config")
    backups = glob.glob(os.path.join(cfg_dir, "postbox_mail.json.corrupt-*"))
    kept = backups and open(backups[0]).read() == GARBAGE
    logged = any("could not read the mail store" in l for l in s.log)
    check("store: a corrupt store is kept as .corrupt-* byte for byte", kept and logged,
          f"backups: {[os.path.basename(b) for b in backups]}, logged: {logged}")
    fresh = read_json(os.path.join(cfg_dir, "postbox_mail.json"))
    check("store: a fresh store is written next to it", isinstance(fresh, dict) and "boxes" in fresh, str(fresh)[:80])
    check("ticks run cleanly after the restart", s.alive() and not s.fatal, "; ".join(s.fatal[:2]))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar")
    ap.add_argument("--workdir", default=os.path.join(ROOT, "build", "server-test"))
    ap.add_argument("--boot-timeout", type=int, default=600)
    a = ap.parse_args()
    jar = a.jar or next((j for j in sorted(glob.glob(os.path.join(ROOT, "build", "libs", "*.jar")))
                         if not j.endswith(("-sources.jar", "-dev.jar"))), None)
    if not jar:
        raise SystemExit("no jar: run ./gradlew build first or pass --jar")
    p = props()
    mc = test_minecraft(p["minecraft_version"])
    TESTED["minecraft_tested"] = mc
    results, logs, fatal = [], [], []

    installer, api = setup(a.workdir, [jar], p, mc)
    # Written before the first boot: a bad value to be clamped, and a sure Lost Mail drop
    os.makedirs(os.path.join(a.workdir, "config"), exist_ok=True)
    with open(os.path.join(a.workdir, "config", "postbox.json"), "w") as f:
        json.dump({"maxQueued": -5, "traderMailChance": 1.0}, f)
    notes = [f"Minecraft {mc} (newest release of the {p['minecraft_version']} line), "
             f"Fabric loader {p['loader_version']} (installer {installer})",
             f"fabric-api {api[0]} (newest for {mc}; compiled against {p.get('fabric_api_version')})",
             f"under test: {os.path.basename(jar)} (sgui {p.get('sgui_version')} bundled)"]
    for n in notes:
        print("  " + n, flush=True)

    s, ready = boot(a.workdir, results, "first boot", INIT, a.boot_timeout)
    try:
        if ready:
            run_first_boot(s, results, a.workdir)
    finally:
        s.stop()
        logs += ["==== first boot ===="] + s.log
        fatal += s.fatal

    if ready:
        with open(os.path.join(a.workdir, "config", "postbox_mail.json"), "w") as f:
            f.write(GARBAGE)
        s, ready2 = boot(a.workdir, results, "restart on a corrupt store", INIT, a.boot_timeout)
        try:
            if ready2:
                run_second_boot(s, results, a.workdir)
        finally:
            s.stop()
            logs += ["==== restart on a corrupt store ===="] + s.log
            fatal += s.fatal

    results.append(("no mixin / tick / entrypoint errors in the logs", len(fatal) == 0, "; ".join(fatal[:3])))
    with open(os.path.join(a.workdir, "console.log"), "w") as f:
        f.write("\n".join(logs))
    failed = [r for r in results if r[1] is False]
    ran = [r for r in results if r[1] is not None]
    # Machine-readable record of this run for README badges (scripts/publish_badges.py).
    with open(os.path.join(a.workdir, "versions.json"), "w") as f:
        json.dump({
            "mod": p.get("mod_version"), "minecraft": p.get("minecraft_version"),
            "minecraft_tested": TESTED.get("minecraft_tested"),
            "loader": p.get("loader_version"), "fabric_api_compiled": p.get("fabric_api_version"),
            "fabric_api_tested": TESTED.get("fabric_api_tested"), "sgui": p.get("sgui_version"),
            "passed": len(ran) - len(failed), "total": len(ran),
            "sha": os.environ.get("GITHUB_SHA"), "branch": os.environ.get("GITHUB_REF_NAME"),
        }, f, indent=2)
    if failed:
        print("---- last 80 console lines ----")
        print("\n".join(logs[-80:]))
        print("---- end ----", flush=True)
    md = [f"### Server test: Minecraft {TESTED.get('minecraft_tested')}, postbox {p.get('mod_version')}", ""]
    md += [f"- {n}" for n in notes] + ["", "| Check | Result |", "|---|---|"]
    md += [f"| {n} | {'✅' if ok else '❌ ' + d.replace('|', '/')[:200]} |" for n, ok, d in results]
    md += ["", f"**{len(ran) - len(failed)}/{len(ran)} passed**"]
    print("\n".join(md))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("\n".join(md) + "\n")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
