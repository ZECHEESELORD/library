# Cooldown API

The cooldown library is a small host-neutral rate-limit surface for Paper, Minestom, and Velocity consumers.

## Local And Shared Keys

Cooldown sharing is explicit. Two call sites share a cooldown only when they deliberately compose the same key.

Use `LOCAL` for plugin-private cooldowns:

```java
CooldownKey key = CooldownKeys.localPlayer(
        "shops",
        "buy-confirm",
        playerId
);
```

Use a shared scope only when multiple participants should see one authoritative cooldown:

```java
CooldownKey key = CooldownKeys.sharedServerPlayer(
        "network",
        "auction-bid",
        playerId
);
```

These two keys are different even if the namespace, name, and player are the same.

## Policies

- `CooldownSpec.rejecting(window)` rejects while the cooldown is active and reports the remaining duration.
- `CooldownSpec.extending(window)` accepts repeated acquisition attempts and resets the expiry window from now.

## Deployment Modes

The library supports two deliberate deployment modes.

- Standalone mode: a plugin shades `cooldown-api` and `cooldown-core` and uses `InMemoryCooldownRegistry` locally.
- Shared-runtime mode: a plugin depends on an external cooldown sync or runtime family that uses the same `cooldown-api` contract to provide shared authority for shared-scope keys.

`LOCAL` keys remain local in both modes. Shared authority applies only to shared-scope keys.
