# Entity Capability Matrix

Legend:
- `S`: supported as implemented
- `D`: degraded or type-limited
- `U`: unsupported

## Backend Matrix

| Backend case | spawn | despawn | teleport | rename | name visibility | glowing | gravity | invulnerable | silent | AI toggle | interaction callback | House 3-line rendering | service entity wrapper |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Paper native generic entity | S | S | S | S | S | S | S | S | S | D | D | U | U |
| Paper native House service entity | S | S | S | S | S | S | S | S | S | D | D | S | S |
| Paper + Citizens humanoid House service | S | S | S | S | S | S | S | S | S | U | D | S | S |
| Minestom native generic entity | S | S | S | S | S | S | S | D | S | U | S | U | U |
| Minestom native House service entity | S | S | S | S | S | S | S | D | S | U | S | S | S |
| Minestom mannequin House service | S | S | S | S | S | S | S | S | S | U | S | S | S |

## Notes

- Paper native rejects `creative:player_like_humanoid` at spawn time. Use the optional Citizens bridge for that case.
- Paper interaction callbacks currently map right-click to `SECONDARY` and damage to `ATTACK`. There is no distinct native `PRIMARY` callback in the current Paper adapter.
- Paper AI toggle is capability-gated and only appears on mob-like entities that actually expose AI control.
- Citizens support is intentionally limited to player-like humanoid entities. It is not a generic Paper entity backend.
- Minestom invulnerability is type-limited. Living entities support it directly; non-living entities reject `invulnerable(true)` explicitly.
- Minestom currently exposes no `AiCapable` implementation. AI control remains unsupported rather than faked.
- House rendering is always the structured 3-line model when the service wrapper is used: anchor name plus backend-owned role and prompt lines.
