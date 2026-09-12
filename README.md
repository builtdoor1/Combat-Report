# Combat Report

Measures how you fight, and writes it up.

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.11**. Hit record, play some fights, hit stop, and you get a page covering your hits, your combos, your jumps and your trades. By *builtdoor*.

> **This is an alpha.** Every statistic is checked against hand-computed values and the
> mixin targets are verified against decompiled 1.21.11 sources, but the build has not yet
> been through a real fight. Expect to be the first person to find out. If it misbehaves,
> [open an issue](../../issues).

It never touches your gameplay. It cannot help you aim, reach further, click faster or reset better. It only watches and writes down what already happened.

**A recording never stops on its own.** It runs from the moment you press record until you press stop — one continuous session, however many fights are in it. Leaving the server saves it rather than throwing it away.

**The empty parts are trimmed from the numbers, not from the recording.** Combat is considered active from the moment you swing at another player or take a hit from one until eight seconds after the last hit either way. Everything outside that — walking back, gearing up, waiting in queue — is removed when the report is summarised, so it never reaches a statistic. That matters most for the time-based figures: combo frequency measured across a lobby wait is not a number about you.

Events are captured continuously and filtered afterwards, which is the only order that works. A jump thrown a fraction of a second *before* the opening hit of a fight is a reset attempt, and at the instant it happens no fight has started yet — dropping it at capture time would lose exactly the jump most worth seeing.

---

## Contents

- [What it measures](#what-it-measures) · [Install](#install) · [Using it](#using-it) · [Reading the report](#reading-the-report)
- [What this can and cannot show](#what-this-can-and-cannot-show) · [Files](#files) · [Under the hood](#under-the-hood) · [Building](#building)

---

## What it measures

### 1 · Hits

| Figure | What it is |
|---|---|
| **Accuracy** | Landed hits as a share of swings thrown at an opponent |
| **Spread of landed hits** | What kind of hit each landed swing was: pick, kb, crit, sweep or plain |
| **Spread of misses** | The same five buckets over the swings that *missed*, plus how often each kind lands at all |
| **Average range** | Average hit distance, reported as a 0.3-wide band, e.g. `2.5–2.8` |
| **3 block accuracy** | Share of landed hits past 2.9 blocks |

The five hit types are mutually exclusive and cover every landed hit, so the spread sums to 100%. That is not a choice this mod made — it falls out of vanilla's own logic:

| Type | Vanilla condition |
|---|---|
| **Pick** | Attack charge at or below 90%, i.e. swung before the cooldown finished |
| **KB** | Charged, and sprinting — the full-knockback hit |
| **Crit** | Charged, falling, and *not* sprinting |
| **Sweep** | Charged, grounded, near-stationary, sword in the main hand, and not a crit |
| **Plain** | Charged, but none of the above — an axe, or a sword swung while walking |

A crit requires not sprinting, and a sweep requires neither a crit nor a sprint hit, so they can never collide. **Plain** exists because those four do not cover everything, and a spread that quietly dropped the leftovers would not add up.

**Misses are typed too.** Every input the classification reads — charge, sprint, fall, footing, weapon — is a fact about the swing you threw, not about the hit you did not get, so a miss carries the type it *would have been* had it connected. That is the only way to type a miss at all: a whiff never reaches the method where vanilla decides, so the type has to be taken at the moment of the swing.

Alongside each bucket the miss spread prints that type's **land rate**, because the two answer different questions and the second is the one worth acting on:

> Half your misses being sprint hits means nothing if sprint hits are also half of everything you throw. A type dominates the misses by dominating the swings. The land rate is what says a kind of swing is actually letting you down — "pick swings are 32% of your misses **and** only land 41% of the time" is a sentence you can do something about.

### 2 · Combos

A combo is **2 or more** hits on the same opponent with no gap longer than 700 ms — roughly a sword's full cooldown recharge plus grace.

| Figure | What it is |
|---|---|
| **Average combo** | Hits per combo you landed |
| **Average received combo** | Hits per combo they landed on you |
| **Combo frequency** | Average time from the end of one combo to the start of the next, within a fight |

Isolated single hits are **not** averaged in as one-hit combos. Counting them would drag the average toward 1 and turn a figure about how well you combo into a figure about how often you poke. They are reported separately instead, so nothing is hidden.

### 3 · Jumps

| Figure | What it is |
|---|---|
| **Jump reset accuracy** | Of jumps timed close to a hit, the share that landed in the 0–80 ms window |
| **Average timing** | How long after the hit you jumped; negative means you jumped early |
| **Jump punishment** | Share of your jumps in combat where they hit you before you touched the ground |

A jump more than 200 ms from a hit is **not scored as a reset attempt** rather than counted as a failed one. A jump a fifth of a second either side of taking damage is just a jump that happened nearby, and scoring it drags the average around. It still counts toward the punishment rate if it happened in combat.

Jumps made while out of combat are dropped entirely — with two exceptions, both of which are combat by definition even though no fight had opened when the jump was thrown: a hit arriving within the attempt window, and a hit landing on you while you are still in the air.

### 4 · Momentum and trades

A **trade** is one hit each, landed within 400 ms of the other. Each hit is spent once, so a four-hit combo answered by a single hit is one trade, not four.

| Figure | What it is |
|---|---|
| **Pushing forward** | Share of trades you came out of moving *toward* them |
| **Average momentum** | Signed velocity along the you→opponent axis, as a share of sprint speed |
| **Damage wins** | Share of decided trades where you dealt more than you took |

**Momentum is not speed.** How fast you are moving says nothing on its own; what says something is the direction. Your velocity is projected onto the horizontal line between you and your opponent and only that component is kept — positive is pushing forward, negative is backing off. It is sampled 150 ms after the exchange, late enough for the knockback to have moved you and early enough that it has not decayed into whatever you did next. Values past 100% are normal: knockback moves you faster than you can run.

Draws are excluded from the damage win rate rather than counted as half. "You win 60% of the trades that were decided" is a claim the data supports; folding draws into the denominator makes the number move when nothing changed.

So is any trade where the opponent was never seen to lose health. From this side a hit absorbed by a shield and a hit whose result never made it back across a bad connection are the same observation, and scoring both as losses would make your win rate a function of your ping.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 or newer for Minecraft 1.21.11.
2. Drop these into `.minecraft/mods`:

   | File | Where | Needed? |
   |---|---|---|
   | `combat-report-<version>+1.21.11.jar` | [Latest release](../../releases/latest) | Yes |
   | Fabric API | [Modrinth](https://modrinth.com/mod/fabric-api) | Yes |
   | Mod Menu | [Modrinth](https://modrinth.com/mod/modmenu) | Optional, adds a button that opens the screen |

No config library needed — the settings live on the mod's own screen.

> Not sure where `.minecraft` is? On Windows press `Win+R`, type `%appdata%\.minecraft`, press Enter.

---

## Using it

Press **`K`** to open the Combat Report screen. From there:

- **Start / Stop Recording** — the big button. It shows a running timer while recording.
- The status line says whether the mod currently counts you as **in combat**. The recording does not pause when it says otherwise — it keeps running, and that stretch is trimmed out of the report at the end.
- **The list** shows every report you have saved. Click one and press **Open report**, or double-click it, to open it in your browser.
- **Open reports folder** takes you straight to the files.
- **HUD / Chat / Auto-open** are the only three settings.

You can also bind **Start/Stop Recording** to its own key under *Options → Controls → Combat Report*. It is unbound by default, because starting a recording by accident mid-fight is worse than binding a key once.

While recording, a small `REC 1:24` indicator sits in the top-left with the combat state under it.

Leaving a server mid-recording stops and saves it rather than throwing it away.

---

## Reading the report

Two files land in `.minecraft/config/combat_report/reports/`:

- **`report-<time>.html`** is the report. Double-click it; it opens in your browser, needs no internet, and fetches nothing.
- **`report-<time>.json`** is the same numbers in a form anyone can check.

The page has the four sections above, plus three charts: the range of every swing against the vanilla 3.0 line, a two-panel jump chart, and the momentum you left each trade with. Hovering any point shows its exact value.

Hover any mark for its detail. On the jump chart, a jump that appears on both panels lights up on both at once — hover a punished mark in the strip and its twin in the timing panel shows you exactly when that jump was thrown.

The jump chart has two panels because the section answers two questions over two different populations. The top is reset timing — only attempts appear, placed by how long after the hit you jumped, with a ring on any that were also punished in the air. The bottom is one mark per jump in combat, oldest first: the denominator the punishment rate is measured over, with tall red marks for the jumps that got hit before landing. Attempts are a subset of those, so the panels hold different numbers of marks — and that gap is the point. A jump punished with no hit nearby never appears on the timing axis at all.

Numbers with no denominator print as a dash, not as zero. An empty recording should look empty, not terrible.

---

## What this can and cannot show

**Everything here is measured on your client.** That is a real limit, not a disclaimer:

- **"Landed" is your client's prediction.** A hit is recorded when the game decides your swing connected. The server can still reject it for reach, cooldown, a shield, or invulnerability frames, and nothing here hears about that.
- **Reach is what your game saw.** Other players' positions are interpolated locally and the server saw them slightly differently depending on your ping. These figures will not match a server anti-cheat's exactly.
- **Damage figures need the server to share opponent health.** Many do; some do not. Where it is not shared, the damage-win figure is withheld rather than printed as zero — a zero there would read as you doing no damage rather than as nothing being measured. The report says so explicitly when that happens.
- **Sweep secondary targets are invisible.** Vanilla resolves them server-side; the client only ever sees the primary target.
- **Spear attacks are counted but not measured.** See below.
- **Hits on mobs are ignored.** This measures PvP. A click that connects with a crystal, a pet or an armour stand is dropped rather than blamed on whoever was nearest your crosshair.

There is no signature, no checksum and no upload. The files are yours, they go nowhere, and equally they prove nothing to a stranger — anyone can edit a JSON file. This is a mod for seeing what your own fights looked like, not for winning an argument.

---

## Files

```
.minecraft/config/combat_report/
├── config.json        # the three settings
└── reports/           # saved reports (.html) and their data (.json)
```

---

## Under the hood

<details>
<summary><b>How a fight is bounded</b></summary>

A fight opens on the first combat event against a player — a swing thrown at them, or a hit taken from them — and closes eight seconds after the last one, or when they die, disconnect, or get more than 16 blocks away.

It is closed **at the last combat event**, not at the moment the idle timer expires, so a fight does not gain eight seconds of dead air on the way out. That matters: those eight seconds would land in the combat-time denominator of every fight.

**Misses open a fight too.** If only landed hits did, a duel that starts with three whiffs and then a hit would record just the hit, and the accuracy figure would quietly become 100%.

</details>

<details>
<summary><b>How hits are classified, and why it is exact</b></summary>

The classification is a transcription of vanilla `Player.attack(Entity)` in 1.21.11, not an approximation:

```java
float g    = getAttackStrengthScale(0.5F);
boolean bl  = g > 0.9F;                       // charged
boolean bl2 = isSprinting() && bl;            // sprint hit
boolean bl3 = bl && canCriticalAttack(entity);// crit
boolean bl4 = isSweepAttack(bl, bl3, bl2);    // sweep
```

`canCriticalAttack` and `isSweepAttack` are private, so they are reimplemented from the same source — but every method *they* call is public, so nothing has to guess or reach through an accessor mixin.

The timing is the load-bearing part. `MultiPlayerGameMode.attack` runs `player.attack(entity)` and then `player.resetAttackStrengthTicker()`. The charge is what separates a pick hit from a charged one, so it has to be read at the **head** of that method. One instruction later it reads zero.

</details>

<details>
<summary><b>Swings, misses and reach</b></summary>

- Every attack click is caught at `Minecraft.startAttack()`, which fires whether or not anything was in reach. `MultiPlayerGameMode.attack` never sees a whiff, so this is the only way to measure one.
- Vanilla's own guards (`missTime`, `hitResult`, `isHandsBusy`) are re-checked there, so clicks vanilla itself discards do not enter the data as phantom swings.
- A miss is attributed to the player closest to your crosshair within 6 blocks and 30°. Swinging at open air with nobody near you records nothing — that is not a miss at anything, and counting it would make accuracy a measure of how often you flick your mouse.
- **Reach is eye to the nearest point of the hitbox**, which is exactly what vanilla checks: `box.distanceToSqr(getEyePosition()) < range * range`. It is *not* where your aim ray crosses the hitbox — that is a different and always-larger number, and it makes legitimate vanilla hits read above 3.0 blocks. Measured on tick positions, because that is what the game validates against; an interpolated eye against a tick-position hitbox is worth a quarter of a block at sprint speed.

</details>

<details>
<summary><b>Spears, and why they are left out</b></summary>

1.21.11 spears are not just another melee weapon. Each one carries three components that change how the attack works:

- **`PIERCING_WEAPON`** — `Minecraft.startAttack` routes the click to `MultiPlayerGameMode.piercingAttack`, which sends a `STAB` action and lets the **server** decide what was hit. It never calls `player.attack(entity)`. So this client is never told whether a stab landed.
- **`MINIMUM_ATTACK_CHARGE = 1.0`** — every click thrown before the cooldown is full is discarded by vanilla without swinging anything.
- **`ATTACK_RANGE`** reaching up to 6.5 blocks, against the 3.0 these figures are built around.

An earlier build recorded every spear attack as a **miss**, which is simply wrong, and undercharged clicks as misses on top of that. Both are now fixed: undercharged clicks are not recorded at all, because vanilla threw no swing, and spear attacks are counted and excluded, with the count printed on the report so the exclusion is visible rather than silent.

Inferring a landed stab from the opponent losing health would be possible, but it is weaker evidence than the rest of the section is built on, and a 6.5-block weapon folded into a range figure would corrupt the one number that section exists to give.

</details>

<details>
<summary><b>Hits taken, and why not knockback magnitude</b></summary>

Damage taken is read at `LivingEntity.handleDamageEvent`, and the source it is handed has already been resolved against your client's level — so it **names the attacker** rather than leaving a hit to be inferred from how hard you were thrown. Received combos and trades both depend on knowing who hit you, which a knockback-magnitude heuristic cannot tell you.

Damage *amounts* come from watching health fall, since a damage event carries no number. Absorption is added in before differencing, so a hit soaked by a golden apple still registers.

The window that matches a health drop to a hit looks **forward** in time and widens with your latency. A drop caused by your click can only arrive after it, one round trip later; a symmetric window would let a hit steal the previous hit's damage, which inside a combo is only half a second away. The damage read also waits far longer than the momentum sample for the same reason — reading it at the momentum deadline scored every high-ping trade as a loss.

</details>

<details>
<summary><b>Jumps</b></summary>

- A jump is the real `jumpFromGround()` call, so knockback that throws you upward can never be mistaken for one.
- Neither jump question can be answered when you jump, so jumps are held open. A hit shortly *before* the jump makes it a reset attempt; a hit shortly *after* makes it a mistimed one, recorded with a negative delta. A hit landing while you are still airborne makes it a punished jump, which is only knowable once you land.
- **No ping compensation, deliberately.** Both events are observed on your own client's tick clock, and latency delays both equally, so it cancels. Winding only the hit timestamp back by the estimated latency mixes a server-frame time with a client-frame one and adds a flat bias of half your ping to every result.

</details>

<details>
<summary><b>Momentum</b></summary>

Velocity is measured as the distance you actually moved each tick, not `getDeltaMovement()`. Delta movement is what the physics *intended*; after a knockback impulse into a wall the two disagree, and the report is about where the fight actually went.

The sprint-speed reference is scaled by your movement-speed attribute, so a Speed effect does not read as superhuman momentum.

</details>

---

## Building

```bash
./gradlew build
```

The jar lands in `build/libs` named for both versions, e.g. `combat-report-1.0.0+1.21.11.jar` — the mod version says what changed, the Minecraft version says whether the file will load.

```bash
./gradlew reportPreview
```

Renders a report from synthetic data into `build/preview` and checks the statistics against hand-computed values — that the spread sums to 100, that the range band contains the average it describes, that single hits stay out of the combo average, that draws stay out of the win rate, and that an empty session prints dashes instead of zeros. It runs without Minecraft, because everything downstream of the recorded data is deliberately game-free.

```bash
./gradlew runClient -PmixinDebug
```

Writes every patched class to `run/.mixin.out`.

---

## Credits

By **builtdoor**. MIT licensed.
