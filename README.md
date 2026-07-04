# Postbox

An in-game **mail system** for Fabric dedicated servers. Entirely server-side — vanilla
clients connect with no mods, no resource pack. Mail is real: letters are book items,
postage is emeralds, and delivery takes time proportional to distance (unless you bribe
the post office).

**For Minecraft 26.2** (branch `main`) and **26.1.x** (branch `26.1`) · Fabric Loader ≥ 0.19.3 · Java 25

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
courier scene toggle, trader loot chance, sweep interval. `config/postbox_mail.json` is
the mail store.

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

Branches: `main` = Minecraft 26.2, `26.1` = Minecraft 26.1.2 — identical code, only
dependency pins differ. CI builds both.

## License

MIT
