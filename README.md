# Postbox

An in-game **mail system** for Fabric dedicated servers. Entirely server-side — vanilla
clients connect with no mods, no resource pack. Mail is real: letters are book items,
postage is emeralds, and delivery takes time proportional to distance (unless you bribe
the post office).

**For Minecraft 26.3** (branch `main`), **26.2** (branch `26.2`) and **26.1.x** (branch `26.1`) · Java 25

| Branch | Minecraft | Status |
|---|---|---|
| `main` | 26.3 | [![build main](https://github.com/k33bz/postbox/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/k33bz/postbox/actions/workflows/build.yml?query=branch%3Amain) ![mod main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2Fmain%2Fmod.json) ![minecraft main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2Fmain%2Fminecraft.json) ![loader main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2Fmain%2Floader.json) ![fabric-api main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2Fmain%2Ffabric-api.json) ![sgui main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2Fmain%2Fsgui.json) ![server-test main](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2Fmain%2Fserver-test.json) |
| `26.2` | 26.2 | [![build 26.2](https://github.com/k33bz/postbox/actions/workflows/build.yml/badge.svg?branch=26.2)](https://github.com/k33bz/postbox/actions/workflows/build.yml?query=branch%3A26.2) ![mod 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.2%2Fmod.json) ![minecraft 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.2%2Fminecraft.json) ![loader 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.2%2Floader.json) ![fabric-api 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.2%2Ffabric-api.json) ![sgui 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.2%2Fsgui.json) ![server-test 26.2](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.2%2Fserver-test.json) |
| `26.1` | 26.1.x | [![build 26.1](https://github.com/k33bz/postbox/actions/workflows/build.yml/badge.svg?branch=26.1)](https://github.com/k33bz/postbox/actions/workflows/build.yml?query=branch%3A26.1) ![mod 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.1%2Fmod.json) ![minecraft 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.1%2Fminecraft.json) ![loader 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.1%2Floader.json) ![fabric-api 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.1%2Ffabric-api.json) ![sgui 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.1%2Fsgui.json) ![server-test 26.1](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fk33bz%2Fpostbox%2Fbadges%2F26.1%2Fserver-test.json) |

## Raising a mailbox

1. Place an **end rod**.
2. Place the **Rainbow Mailbox** head on top ([minecraft-heads.com head #39194](https://minecraft-heads.com/custom-heads/head/39194-rainbow-mailbox)).

The head snaps into a proper mailbox — stretched to real-mailbox proportions on its post —
with a floating `<Owner>'s Mailbox` label. Default cap: **1 mailbox per player**
(`maxBoxesPerPlayer`). Breaking the end rod dismantles the box: displays vanish, the head
drops back, and any letters still inside slide safely into your queue.

Admins can hand out heads with:

```
/give @p player_head[profile={name:"RainbowMailbox",properties:[{name:"textures",value:"<texture from MailboxHead.java>"}]}]
```

## Using a mailbox

- **Right-click** (owner only): opens the 3×3 **inbox**. Up to 9 letters; taking one
  backfills FIFO from your queue. A **red flag** stands while the inbox is nonempty.
- **Sneak + right-click** (anyone, any box — including your own): opens the **send form**,
  a native dialog with a recipient dropdown (online players first, then every known
  offline player), a multiline message field, a *send held book* toggle, and an
  *extra postage* slider.
- **Search note:** dialogs are static forms, so the Search field works via the **Filter**
  button — type a few letters, press Filter, and the form re-opens with the dropdown
  narrowed to matches. An empty search shows everyone again.
- The **message** becomes a signed "Postcard" book. Newlines typed into the multiline
  field travel as part of the dialog command — if your client strips them, prefer
  mailing a written book for multi-page prose.
- `/mail check` shows inbox/queue counts and every letter still in transit with its ETA.
- `/postbox testsend <player> [extra]` and `/postbox take` are the command twins of the send
  form and the inbox (for scripted test clients). Like the form and the inbox, they only work
  while you stand at your own mailbox.

## Postage & delivery

Letters are `written_book` / `writable_book` items only.

| situation | cost (emeralds) |
|---|---|
| dropping mail into the **recipient's own** box | **free**, instant (hand delivery) |
| recipient has a mailbox | `1 + ceil(chars/150) + ceil(distance/512)` |
| recipient has **no** mailbox | `1 + ceil(chars/150) + 3` (poste restante, queue-only, slow: 600 s) |

- Payment comes from your **inventory only**: emeralds, and emerald **blocks** count as 9.
- **No change, no refunds** — every overpaid emerald **halves** the remaining delivery
  time. A block split that overpays counts as express, and the *extra postage* slider is
  deliberate express.
- Delivery time: distance × 1 s per 100 blocks (min 10 s). Mail actually travels — the
  chime, flag, and actionbar `You have mail.` land when it arrives, and a login notice
  `You have mail (N).` greets returning players.
- Queues are per-player FIFO, capped (`maxQueued`, default 100). A send past the cap is
  **rejected up front** — mail is never silently dropped.

## The Express Courier

When a letter with **any** overpaid postage arrives at a mailbox in a loaded chunk, an
invulnerable **Express Courier** (wandering trader, no trades) and 1-2 llamas appear down
the road, walk to your box, pause for the hand-off, and wander off in a puff of smoke.
Pure theater — an unloaded chunk or a blocked path never delays the actual mail.

## Lost Mail

Any slain wandering trader (vanilla spawns included) has a `traderMailChance` (default
0.35) of dropping a **Lost Mail** bundle: 1-3 undelivered letters mixing gibberish
postcards from fictional senders with excerpts from famous real letters (Sullivan Ballou,
Seneca, van Gogh, Beethoven's Immortal Beloved, Abigail Adams, Pliny, Mozart, Dickinson,
Franklin, Keats).

## Config

`config/postbox.json` (gson, file-only for v1): postage knobs, delivery speeds, queue cap,
courier scene toggle, trader loot chance, sweep interval. Values are clamped to sane ranges when
loaded; a file that can't be parsed is left untouched and postbox runs on defaults until it's
fixed and the server restarts.

`config/postbox_mail.json` is the mail store. It is saved atomically (temp file, then move). If it
ever can't be parsed at boot, postbox copies it to `postbox_mail.json.corrupt-<millis>` before
starting with an empty store, so nothing is lost: repair the copy and put it back with the server
stopped.

## Store schema (for external tools)

```
{
  "boxes":     [ {id, owner, ownerName, dim, x, y, z, inbox:[letter]} ],
  "queues":    { "<uuid>": [letter, ...] },      // FIFO, oldest first
  "inTransit": [ letter, ... ],
  letter = { from, fromUuid, toUuid, toName, sentAtMs, arriveAtMs,
             postagePaid, express, boxId, stack: <ItemStack JSON> }
}
```

## Future (documented, not built)

- **Web inbox** at mc.kast.ro reading `postbox_mail.json` (read-only: boxes + queues per
  authenticated player, rendering `stack` book pages).
- **Discord bridge**: a bot appending letters to `queues{}` authored `Discord/<name>`
  (fromUuid empty, no box) — the in-game side already delivers queue letters on inbox
  open and counts them in `/mail check`.

## Building

```
./gradlew build
```

## Branches, CI and releases

| Branch | Minecraft | Jar |
|---|---|---|
| `main` | 26.3 | `postbox-<version>+26.3.jar` |
| `26.2` | 26.2 | `postbox-<version>+26.2.jar` |
| `26.1` | 26.1.2 | `postbox-<version>+26.1.2.jar` |

Identical code on every branch; only `gradle.properties` differs (loader, fabric-api, the bundled
sgui build and the dev run dir, following sanctuary's branch of the same Minecraft version). Each
jar only loads on its own Minecraft line.

Every push and PR builds, runs the unit tests, and boots a real Fabric server twice
(`scripts/server_test.py`): config clamping, the mail store, `/mail`, the outbox spool (a valid
request delivered as a written book, a malformed one dropped), Lost Mail from a slain trader,
leftover couriers removed, then a restart on a corrupt store that must be backed up, not wiped.
Placing a mailbox and the dialog/inbox need a player, so they stay with the mineflayer harness.

Releases are per line: push a tag `v<mod_version>+<minecraft_version>` (for example
`v0.1.3+26.1.2`) on that line's branch. CI checks the tag against the commit, builds, runs the
server test, and publishes the jar as a [GitHub release](../../releases) with notes from
`CHANGELOG.md`. Only `main` releases are marked latest.

## License

MIT
