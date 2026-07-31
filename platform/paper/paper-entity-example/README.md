# Paper entity and NPC behavior example

This plugin builds two managed dioramas beside the default-world spawn. It requires PacketEvents 2.13.0, declares Folia support, uses only async entity lifecycle methods, and does not change permanent world blocks. Lecterns, shelves, tables, and the anvil are nonpersistent managed `BLOCK_DISPLAY` scenery; `PaperEntityPlatform.closeAsync()` removes them with the NPCs.

## Scenes

The library has five independently authored roles:

- Elowen, a warm librarian, writes at a lectern and occasionally reshelves notes.
- Orrin, a curious archivist, searches and reshelves books.
- Mira, a distracted scribe, works with paper, books, ink, and a quill.
- Tamsin, a confused herpetology researcher, searches the wrong shelves.
- Alda, a sleepy night clerk, studies slowly at a second lectern.

The forge has three roles:

- Bran, a confident blacksmith, performs the shipped anvil routine and a custom tool inspection.
- Pip, a nervous apprentice, handles materials at a smithing table.
- Sera, a neutral quartermaster, checks stock at a barrel.

Together these roles cover all eight personality presets. Every NPC has tuned attention, acquisition/acknowledgement gestures and barks, interaction lines, prop-completion lines, weighted idle routines, cooldowns, authored base equipment, and one of several voice profiles. Most have per-NPC interruption overrides; Mira and Pip intentionally fall back to their topic's generic pool. House labels remain permanent while disposable speech is layered above them.

Three ambient conversation registrations demonstrate a four-person cast, a three-person forge cast, and two overlapping library casts. Interacting with Elowen during a conversation exercises the interruption cascade; after behavior observes the normalized `USE` or `ATTACK`, the application callback also sends the player an intentionally simple "Oh, hello" chat response. That chat message stands in for a future real dialogue system.

The custom reshelving and inspection routines complement the four shipped routines. Across them, the example uses `lookAt`, `sweep`, both stances, `equip`, `equipOneOf`, `clear`, sound-bearing and silent gestures, both hands, `swing`, `useItem`, explicit sounds, waits, timing bands, custom sound sets, and anchor offsets. Routine cleanup restores the latest authored base equipment rather than a routine-start snapshot.

## Runtime controls

Use `/npcdemo help` or its `testnpcs` alias. The controls are explicit so the ambient scenes stay stable:

- `/npcdemo say` queues two global FIFO speech bubbles.
- `/npcdemo now` supersedes disposable queued speech with `speakNow`.
- `/npcdemo clear` clears visible and pending speech.
- `/npcdemo cancel` cancels the most recently remembered `NpcPlayback`.
- `/npcdemo study` explicitly performs the librarian routine.
- `/npcdemo forge` explicitly performs the blacksmith routine.
- `/npcdemo attention` holds a five-second manual `attendTo(Identified)` lease for the issuing player.
- `/npcdemo snapshot` reads `profile()` and prints the immutable behavior snapshot.
- `/npcdemo baseprop` changes Elowen's live authored main-hand equipment; active routine cleanup restores this new value.
- `/npcdemo off` disables Elowen's profile and restores native presentation.
- `/npcdemo on` atomically configures the original immutable profile again.

Ordinary acquisition remains automatic and per viewer. With multiple players nearby, each sustained viewer sees the relevant personal attention overlay while the real mannequin presents the latest canonical target to observers.

## Verification

From the repository root:

```text
./gradlew :platform:paper:paper-entity-example:test
```

`PaperNpcBehaviorCatalogTest` verifies all personality presets are represented, every profile has authored worldbuilding pools, the weighted cooldown and bark setup is intact, and the custom routines cover the builder primitives missing from the shipped routines.
