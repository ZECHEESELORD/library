# Local Dev Loop

For rapid visual iteration, use the embedded Minestom dev harness as the primary inner loop.

Run:

```bash
./gradlew runMinestomExample
```

Then join `localhost:25565`.

Use these in-game commands:

- `/testmenus tabs|list|profile|farming|museum|slot5|canvas`
- `/testmessages all|notices|topics|clicks|block`
- `/testsoundfx all|menu|npc|confirm|deny|levelup|discovery`
- `/testnpcs reset|clear`

Why this is the default loop:

- no plugin jar copying
- no external Paper or Velocity server bootstrap
- menus, messages, sound cues, and House entities all come from the current code in one server process
- reset and preview actions happen in game instead of through rebuild-and-move steps

The unified preview entrypoint is:

- `:platform:minestom:minestom-example`
- `sh.harold.creative.library.example.minestom.MinestomExampleBootstrap`

## Recommended Workflow

Use Minestom for fast iteration and Paper for occasional compatibility checks.

- start the Minestom dev harness once
- make formatting changes in `menu-core`, `house-service-entity`, `message-core`, or the Minestom adapters
- if you run from your IDE in debug mode, use normal JVM HotSwap for simple method-body edits
- restart the example only when you make structural changes that HotSwap cannot apply
- use the in-game commands to reopen menus, resend message catalogs, replay sounds, or reset the NPC preview after changes
- once the format looks right, spot-check it on Paper

Paper is still useful for verification, but it is the wrong primary inner loop for this repo because plugin reload is not a trustworthy development primitive.
