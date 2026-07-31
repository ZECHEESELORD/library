# Minestom NPC behavior dioramas

This is the standalone, runnable showcase for `npc-behavior-api` on Minestom. It is intentionally a set of persistent walk-up scenes rather than a startup smoke sequence: join `localhost:25565`, walk into an NPC's attention radius, interact with it, and use `/npcdemo help` for imperative API controls.

Run it with:

```text
./gradlew runMinestomNpcDioramas
```

The scenery is ordinary static Minestom blocks. Routines only target immutable `AnchorRef` values and never mutate blocks, inventories, recipes, or NPC positions.

## Scenes

| Diorama | Cast and personalities | Behavior shown |
|---|---|---|
| Library | Elowen (`WARM`), Pip (`CURIOUS`), Tamsin (`DISTRACTED`) | Lectern writing, cataloguing, shelf distraction, tuned acquisition/acknowledgement barks, silent and custom multi-variant voices, a three-person conversation, an overlapping registration, and House label/bubble stacking |
| Forge | Mara (`CONFIDENT`), Niko (`NERVOUS`) | Anvil forging, table crafting, prop inspection, deep/frog delivery, routine acknowledgement, a disjoint conversation that may run concurrently with the library, and generic versus per-NPC interruption lines |
| Watch post | Iona (`NEUTRAL`), Orin (`SLEEPY`), Wren (`CONFUSED`) | Steady/natural attention contrast, spyglass scanning, crouch/lean proxies, maps and lanterns, varied personality timing, and explicit configure of an initially inert House mannequin |

Together the cast covers all eight personality presets. Every actor has authored base equipment so routine cleanup visibly restores the latest native prop state.

The custom routines supplement all four shipped routines and deliberately cover the full sequence builder: `lookAt`, `sweep`, `stance`, `equip`, `equipOneOf`, `clear`, sound-bearing and silent `gesture`, `swing`, `useItem`, `sound`, and `wait`.

## Controls

- `/npcdemo library`, `/npcdemo forge`, `/npcdemo watchpost`: move the player to a viewing point. NPCs never move away from their authored anchors.
- `/npcdemo queue`, `/npcdemo now`, `/npcdemo clear`: exercise global FIFO `speak`, preemptive `speakNow`, and `clearSpeech`, including styled and long wrapped Components.
- `/npcdemo lectern|review|catalogue|shelf|anvil|craft|inspect|guard|sleepy|route`: invoke `perform` and report its cleanup completion.
- `/npcdemo attend`: acquire a five-second `attendTo(Identified)` lease for the issuing player.
- `/npcdemo snapshot`: print the immutable librarian behavior snapshot.
- `/npcdemo pause`, `/npcdemo resume`: demonstrate `disable` and clean `configure` lifecycle transitions.

Use two or more clients to observe the acquisition stack: each engaged viewer gets a personal attention composition while outside observers see the latest canonical target. The behavior layer observes a normalized interaction before the application handler; that interaction drives the profile's attention-scoped bubble/voice as well as the disposable `"Oh, hello"`-style chat callback. Use and attack remain distinguishable to that callback.

Shutdown closes outstanding playbacks, attention leases, conversation registrations, and finally `MinestomEntityPlatform.closeAsync()`.
