# Entity Manual Verification

## Paper

- Start the Paper example plugin and confirm the native villager, temporary armor stand, and `Meredith` service entity appear near spawn.
- Right-click the native villager and `Meredith`, then left-click the native villager, and confirm interaction logs are emitted.
- Wait for the scheduled smoke steps and confirm the villager renames, glows, becomes silent, teleports, and the temporary armor stand despawns.
- Confirm the House presentation shows exactly three mounted mini armor-stand lines for `Meredith`: colored NPC name, gray bracketed description, and yellow bold `CLICK`, with `0.3` spacing between lines.
- Confirm hovering the base entity does not reveal an extra hidden label or client tooltip line.
- Confirm native Paper without Citizens logs the explicit unsupported note for `creative:player_like_humanoid`.

## Paper + Citizens

- Install Citizens, start the same Paper example plugin, and confirm `Gideon` appears as the player-like humanoid service entity.
- Right-click and left-click `Gideon` and confirm the interaction logs are emitted through the Citizens bridge.
- Wait for the scheduled smoke step and confirm `Gideon` glows, teleports, and still keeps the mounted 3-line armor-stand House presentation with no extra hover label.
- Confirm the bridge is only used for the humanoid case and the native villager and `Meredith` still use the native Paper backend.

## Minestom

- Start the unified Minestom dev harness and join the server on `localhost:25565`. Run `/testnpcs reset` if needed, then confirm the native villager, temporary armor stand, `Meredith`, and `Gideon` appear near spawn.
- Right-click and attack the native villager, `Meredith`, and `Gideon`, and confirm the in-game interaction responses appear.
- Confirm `Meredith` opens the menu gallery and `Gideon` reprints the dev-harness command summary.
- Confirm `Meredith` and `Gideon` both render the structured mounted 3-line armor-stand House presentation: colored name, gray bracketed description, yellow bold `CLICK`, `0.3` spacing, and no extra hover label.
- Confirm unsupported behavior stays explicit rather than silent: Minestom still has no AI toggle capability, and non-living invulnerability remains type-limited.
