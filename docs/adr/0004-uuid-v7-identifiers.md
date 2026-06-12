# 0004. UUID v7 identifiers

- Status: Accepted
- Date: 2026-06-08
- Deciders: Kntro-Soft team

## Context

Aggregates need globally unique identifiers that can be generated client-side before persistence (a
DDD requirement). Auto-increment `BIGSERIAL` requires a database round-trip and leaks volume/ordering.
Random UUID v4 is global and client-generated but, as a primary key, fragments the PostgreSQL B-tree
index (random insertion points cause page splits and poor cache locality).

## Decision

Use **UUID v7** (time-ordered) as the identifier, stored in a **native PostgreSQL `uuid`** column.
v7 embeds a millisecond timestamp prefix, so generated ids are monotonically increasing → index
inserts land at the right edge of the tree (no fragmentation), while keeping UUID's global uniqueness
and non-guessability. Since JDK 25's `java.util.UUID` has no v7 factory, we generate ids with the
`uuid-creator` library (`UuidCreator.getTimeOrderedEpoch()`), centralized in `IdGenerator` and called
from the `AggregateRoot` constructor (not `@GeneratedValue`).

## Consequences

- Sequential-friendly primary keys: compact B-tree, better write throughput and cache locality vs v4.
- Native `uuid` storage is 16 bytes (vs 36 for `varchar`), with fast binary comparison.
- Ids exist before persistence, enabling clean aggregate construction and event references.
- A small external dependency (`uuid-creator`) until the JDK ships a v7 generator.
- v7 exposes approximate creation time in the id — acceptable; ids are not secrets.
