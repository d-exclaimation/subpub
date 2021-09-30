# CHANGELOG

### v0.1.6

- Fixed issue of faulty math logic for `BroadcastHub` buffer size.

### v0.1.5

- `SubEngine` now holds 1 state for each topic with a corresponding `Cascade`.
- `Cascade` will now make a `BroadcastHub` configured `Sourc`e as a single source of truth.
- Moved `onComplete` callback Sink into the `Cascade`'s `apply`.
- Added extensions to create `BroadcastHub` immediately.

## v0.1

Working Pub/Sub system for distributed, concurrent safe, topic based streaming system. 
