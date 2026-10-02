# Changelog

## 0.1.3

Same release on every line: `main` (26.3), `26.2` and `26.1` (26.1.2).

Security review fixes:
- **The mail store can no longer be wiped (High).** If `config/postbox_mail.json` could not be parsed at boot, postbox started with an empty store and saved it straight over the file, losing every mailbox, queued letter and letter in transit (letters are real player items). Now the damaged file is first copied to `postbox_mail.json.corrupt-<millis>` (and postbox refuses to start if even that copy fails), and every save is atomic (temp file, then move), so a crash mid-save can no longer leave a half-written store in the first place. Stray `null` entries in a hand-edited store are dropped instead of crashing lookups. Unit tested (`MailFilesTest`).
- **`/postbox testsend` and `/postbox take` need you at your mailbox (Medium).** These permission-0 twins of the send form and the inbox (for scripted test clients) worked from anywhere in the world, so anyone could send from, and empty, their mailbox without going there. They now follow the same 8-block rule as the send dialog.
- **A malformed outbox request can't crash the server (Medium).** A spool file whose `toUuid` wasn't a UUID was accepted, then parsed inside the server tick at delivery, which crashed the server, and again on every restart because the letter had been saved. Requests are now validated (and normalized) on ingest, and delivery itself no longer throws on a bad recipient.
- **Config hardening (Low).** A `config/postbox.json` that fails to parse is no longer overwritten with defaults; postbox runs on defaults in memory and leaves the file alone. Every knob is clamped to a sane range on load (`postageCharsPerEmerald: 0` made the length charge free, a negative `maxQueued` rejected all mail, a chance above 1 or NaN broke the math). Unit tested (`PostboxConfigTest`).
- **No more immortal leftover couriers (Low).** Express-courier traders are invulnerable and never despawn; if the server stopped, or their chunk unloaded, mid-delivery they stayed in the world forever. Any courier whose scene is no longer running is now removed when it loads, and run tags carry a per-boot id so an old one can never look live.
- **Mailbox ids can't collide (Low).** Ids were game time + box count, which repeats after a dismantle in the same tick; two boxes sharing an id share display tags. New boxes get a random id (existing ids are kept).

Lines and build:
- **Minecraft 26.3.** `main` now targets 26.3; 26.2 moved to its own `26.2` branch; `26.1` stays. Pins follow sanctuary's branch of the same Minecraft version (26.3: loader 0.19.5, fabric-api 0.161.0+26.3, sgui 2.2.1+26.3; 26.2: loader 0.19.5, fabric-api 0.161.0+26.2, sgui 2.1.0+26.2; 26.1.2: loader 0.19.5, the loader gmc101 runs, fabric-api 0.155.3+26.1.2, sgui 2.0.0+26.1).
- **The 26.1 jar no longer bundles the 26.2 sgui.** sgui and the dev run dir were hard-coded in `build.gradle` (`sgui 2.1.0+26.2`, `run262`) and shared by every branch, so the 26.1 jar shipped a 26.2 sgui build. Fabric keeps only the newest bundled copy server-wide, so on a 26.1.2 server it also replaced sanctuary's sgui. Both are now per-line `gradle.properties` values.
- **Each jar only loads on its own Minecraft line.** `fabric.mod.json` takes its `minecraft` range from `minecraft_version`.
- **CI** (`.github/workflows/build.yml`): build, unit tests, and a real-server test (`scripts/server_test.py`, two boots, see the README), plus per-line README badges from the orphan `badges` branch.
- **Releases** (`.github/workflows/release.yml`): pushing `v<mod_version>+<minecraft_version>` on a line's branch checks the tag, builds, runs the server test and publishes the jar (with a `.sha256`) as a GitHub release, with notes from this changelog.

## 0.1.2

- Outbox: claim a request by deleting it first, so a request file that can't be deleted is skipped instead of mailing its recipient on every sweep.

## 0.1.1

- Outbox spool: other server-side mods can post system mail by dropping one JSON file per letter into `config/postbox_outbox/`.

## 0.1.0 and the deep review

- Postbox: Rainbow Mailboxes on end rods, book letters, emerald postage, distance-timed delivery, express wandering-trader couriers, Lost Mail.
- Deep-review fixes: the SNBT label injection (the player name in the mailbox `text_display` summon is escaped; exploitable on offline-mode servers), a bounded hand-delivery flood, owner-only dismantling, and a book duplication.
