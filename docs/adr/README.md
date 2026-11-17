# Architecture decision records

Short records of decisions that are expensive to reverse. Newest last.

## ADR 0001 — One cache identity for every input that changes a prayer time

**Context.** Cached monthly timetables were keyed by location, method and school only, so
changing the high-latitude rule kept serving the old table while the offline engine used the
new rule.

**Decision.** The cache key includes the latitude-adjustment rule (`lat|lon|method|school|
month|latitudeRule`). Every consumer that can change a time is part of the identity.

**Consequences.** A rule or school change costs one network round trip per month view, which is
correct: the alternative is showing a table that does not match the user's settings.

## ADR 0002 — Split the night only when the twilight angle is unreachable

**Context.** The engine fell back to fixed 108°/105° hour angles when `cos(hourAngle)` left
`[-1, 1]`, which produced plausible but arbitrary Fajr/Isha times at high latitudes.

**Decision.** Use the configured rule (middle of night, one seventh, twilight angle) only when
no astronomical twilight exists for that day; interval-based Isha methods are never overridden.
Night length comes from that day's own sunset/sunrise, so it follows the season.

**Consequences.** Ordinary latitudes are bit-for-bit unchanged; polar days get a documented,
scholarly-recognised approximation instead of a magic number.

## ADR 0003 — Preserve an undecryptable database, never delete it

**Context.** Recovery from a keystore/passphrase mismatch deleted the SQLCipher file and
recreated it empty, silently destroying prayer logs, Qadha, Hifz and Zakat history.

**Decision.** Copy the unreadable file (and its sidecars) into `files/recovery/`, record a
`DatabaseRecovery`, then create a fresh database. The Privacy screen names the copy.

**Consequences.** Startup still succeeds, but a user with a lost key learns that their records
exist and where. Support can ask for that file instead of guessing.

## ADR 0004 — Screens declare their own navigation chrome

**Context.** Top- and bottom-bar visibility lived in two independent `when` chains over screen
names, which drifted whenever a screen was added.

**Decision.** `SakinahScreen` carries `showTopBar` / `showBottomBar`; the scaffold reads them.

**Consequences.** Adding a screen forces an explicit chrome choice, and a restored back stack
from an older build drops unknown names instead of crashing.
