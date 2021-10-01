# CHANGELOG

### v0.1.7

- Added `source` method with `initValue` and / or `endValue` to have a `Source` with a initial data or closing one (Only
  for that source not the topic).
- Added `apply` for `SubPub` to construct without the `new` keyword.

### v0.1.6

- Fixed issue of faulty math logic for `BroadcastHub` buffer size.

### v0.1.5

- `SubEngine` now holds 1 state for each topic with a corresponding `Cascade`.
- `Cascade` will now make a `BroadcastHub` configured `Sourc`e as a single source of truth.
- Moved `onComplete` callback Sink into the `Cascade`'s `apply`.
- Added extensions to create `BroadcastHub` immediately.

## v0.1

Working Pub/Sub system for distributed, concurrent safe, topic based streaming system. 
