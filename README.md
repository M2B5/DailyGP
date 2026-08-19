# DailyGP

Runs one fully automated Grand Prix a day on a [TimingSystem](https://github.com/FrostHexABG/TimingSystem)
track, drawn at random from a tagged pool of tracks.

## How a day runs

1. **Track selection** (`schedule.track-selection-time`) — a track is drawn at random from every
   track carrying the `track-tag` tag (`DailyGP` by default). Only open tracks with a start region
   and grid positions are eligible, because a heat cannot be loaded without them, and the tracks in
   the [excluded track queue](#excluded-track-queue) are held back. The Grand Prix is announced so
   players know where and when they will be racing.
2. **Qualifying opens** (`schedule.start-time`) — a TimingSystem event is created with a qualifying
   round and a final round, each holding one heat:
   - qualifying gets a time limit of `qualifying.duration-minutes` and starts immediately, empty;
   - the final gets `grand-prix.pits` pit stops (only on tracks with a pit region), a lap count
     calculated from `race.duration-minutes` and the track's [reference time](#lap-count), and DRS
     (`race.drs`, on by default) and push to pass (`race.push-to-pass`, off by default).
3. **Drivers join** — there is nothing to sign up to in advance. Players run `/dailygp join` at any
   point while qualifying is running and are put straight onto the grid; driving through the start
   region begins their session. Late joiners share the session clock, so qualifying ends at the same
   moment for everyone and joining late costs running time rather than extending the session. The
   field is capped at `grand-prix.max-drivers`, or at the number of grid positions on the drawn track
   if that is lower.
4. **End of qualifying** — the first driver to complete their final lap opens a
   `qualifying.finish-grace-seconds` window for everyone else, counted down on a boss bar.
   Qualifying is finished when that window closes, or as soon as every driver is done.
5. **The final** — the qualifying round is finished, which seeds the grid with the qualifying order,
   and the final is loaded and counted down. Only drivers who took part in qualifying are on the
   grid: TimingSystem allows late entries into qualifying heats only.
6. **End of the Grand Prix** — the winner crossing the line opens a `race.finish-grace-seconds`
   window for the rest of the field, again counted down on the boss bar, after which the final and
   the event are finished. The classification and the winner's statistics are saved, and the event
   then [removes itself](#event-cleanup) from TimingSystem.

A qualifying session nobody joins is called off when its time is up, and both sessions are guarded by
a watchdog, so a heat that nobody ever finishes - because every driver logged off, for example -
cannot leave the event running indefinitely.

## Chat and boss bar

The plugin broadcasts three things and nothing else:

1. **The track announcement**, when the day's track is drawn.
2. **The info card**, when qualifying opens:

   ```
            » DAILY GRAND PRIX «
   ---------------------------------
    Track: Nordschleife
    Race: 24 laps · 1 pit stop
    Qualifying: open for another 10 minutes
    Drivers: 0/12
                 [ JOIN NOW ]
   ---------------------------------
   ```

   `[ JOIN NOW ]` joins qualifying on click. The card is also sent to players a couple of seconds
   after they join the server while qualifying is running, so anyone arriving mid-session knows there
   is one, and it is what `/dailygp info` shows whenever there is a Grand Prix to describe.
3. **One line when qualifying ends and the final starts.**

Everything else - drivers joining, the winner, cancellations, watchdog timeouts - goes to the server
log only, so chat stays quiet.

The two finish grace periods are shown on TimingSystem's event countdown boss bar ("Qualifying ends
in" and "Race ends in"), which every driver and spectator of the event sees. It is taken down as soon
as the session it belongs to ends.

Replies to `/dailygp` commands still go to whoever ran the command, and TimingSystem's own heat
messages - the starting countdown, lap times, finishes and results - are unaffected.

### Colours

Chat, the info card and the holograms share one palette, defined in `Palette`: red headings framed in
blue (`» LIKE THIS «`), white values, grey labels and dark grey dividers. Changing the constants there
restyles every part of the plugin at once.

## Overtaking aids

`race.drs` (default `true`) and `race.push-to-pass` (default `false`) are applied to the final only.
Both only mean anything when there is a car ahead to catch — DRS opens for a driver who crosses a
detection region close behind another, and push to pass is a rechargeable boost for attacking or
defending — so neither is applied to qualifying, which drivers run on their own with collisions off.

DRS needs DRS detection and activation regions on the track. On a track without them it simply never
opens, and the plugin notes that in the log when the race is created. TimingSystem keeps DRS shut for
the first lap.

## Excluded track queue

The `grand-prix.excluded-track-queue-length` most recently raced tracks are held back from the draw,
so the same track cannot keep coming up. Each track that is drawn goes to the front of the queue and
the oldest one drops off, becoming available again. The queue survives restarts in
`plugins/DailyGP/excluded_tracks.yml` and stores track ids, so renaming a track does not lose its
place.

Keep the length below the number of tagged tracks. If every eligible track is in the queue there is
nothing left to draw, so the queue is ignored for that one draw and a warning is logged rather than
the server going without a Grand Prix. Set it to `0` to disable the queue.

## Event cleanup

A Grand Prix leaves nothing behind in TimingSystem. Once the classification has been written to
`last_grand_prix.yml` and the winner's statistics to `player_stats.yml`, the event is removed with
`EventDatabase.removeEventHard`, so `/event list` does not fill up with one dead `DailyGP-...` entry
per day. Everything the plugin needs afterwards - the results board, the win counts, the streaks -
comes from its own files, not from the event.

Events are also removed when a Grand Prix is cancelled, and when the server shuts down mid-race: the
plugin's state lives in memory, so an event left half-run could never be finished or recorded after a
restart, only orphaned. If you would rather keep finished events for inspection through `/event` and
`/heat`, drop the `deleteEvent()` call from `finishEvent()` in `GrandPrixManager`.

## Statistics and leaderboards

Every Grand Prix that produces a winner is recorded in `plugins/DailyGP/player_stats.yml`: the
winner's total goes up, their streak grows, and every other driver's current streak ends. A win is
only recorded when the leading driver completed the full race distance, so a race stopped before
anyone finished leaves the statistics — and everybody's streak — untouched. The classification of the
most recent race is kept separately in `plugins/DailyGP/last_grand_prix.yml`.

Three holographic leaderboards present that data, drawn with
[FancyHolograms](https://modrinth.com/plugin/fancyholograms):

| Board | Shows |
|-------|-------|
| Latest Grand Prix | Finishing positions and race times of the most recent race; drivers who did not go the full distance are shown as DNF with their lap count |
| All-time wins | The drivers with the most Grand Prix wins |
| Win streaks | The longest run of consecutive wins anyone has achieved |

They are refreshed whenever a Grand Prix finishes — the data cannot change at any other time — and
are recreated at their configured locations on `/dailygp reload`. FancyHolograms is a soft dependency
and every board is optional: without the plugin, or without a location configured under `holograms`
in the config, the Grand Prix runs identically and the board is simply not shown.

## Lap count

TimingSystem does not record how long a track physically is, so a time trial time set on it is used
as the measure instead:

```
laps = round(race.duration-minutes / (reference lap time * race.pace-multiplier))
```

The reference is the **5th best** time on the track, not the outright record, so that a single
cheated time that has not been deleted yet cannot inflate the lap count and leave the race running
far longer than `race.duration-minutes`. On a track with fewer than five recorded times the slowest
of those available is used, which errs the same way: towards a slightly short race rather than a
very long one.

The pace multiplier accounts for race laps being slower than a time trial hot lap. Stage tracks
always run a single lap, and a track that has never been completed falls back to TimingSystem's own
`finals.laps` setting.

## Commands

| Command                | Permission      | Description                        |
|------------------------|-----------------|------------------------------------|
| `/dailygp info`        | `dailygp.use`   | Current Grand Prix status          |
| `/dailygp join`        | `dailygp.use`   | Join the running qualifying session |
| `/dailygp drivers`     | `dailygp.use`   | List the drivers taking part       |
| `/dailygp now`         | `dailygp.admin` | Call a Grand Prix on a fresh track right now |
| `/dailygp reload`      | `dailygp.admin` | Reload the configuration           |
| `/dailygp selecttrack` | `dailygp.admin` | Draw today's track now             |
| `/dailygp start`       | `dailygp.admin` | Open qualifying for the scheduled Grand Prix now |
| `/dailygp cancel`      | `dailygp.admin` | Cancel the current Grand Prix      |

`/dgp` is an alias of `/dailygp`.

### Racing outside the schedule

`/dailygp now` runs a complete Grand Prix without waiting for the daily schedule: it draws a fresh
track and opens qualifying immediately, replacing a Grand Prix that was only waiting for its
scheduled start. A Grand Prix that is already on track is never replaced - cancel it first.

`/dailygp start`, by contrast, opens qualifying early for the *scheduled* Grand Prix, on the track
that was already drawn and announced.

## Setup

1. Create the track tag once: `/ts tag create DailyGP`.
2. Select each track that should be in the pool and toggle the tag on it: `/trackedit tag DailyGP`.
3. Adjust `plugins/DailyGP/config.yml` and run `/dailygp reload`.

## REST API

TimingSystemRESTApi exposes the Grand Prix over HTTP when both plugins are installed, through
`/api/v4/readonly/dailygp` (live state), `/dailygp/results/latest`, `/dailygp/stats`,
`/dailygp/stats/:uuidorusername` and `/dailygp/excluded-tracks`. DailyGP does not depend on the REST
API, and the REST API only registers those routes when DailyGP is present, so either plugin runs
perfectly well on its own. The data comes from the same managers the plugin uses, so nothing is
duplicated.

## Building

Joining a running qualifying heat uses `EventDatabase.heatDriverNewLate` and
`Heat.addLateDriverToGrid`, which are not in any published TimingSystem release yet, so the plugin
builds against the TimingSystem source checked out next to it. Install that first:

```bash
cd ../TimingSystem && mvn install
```

Then build the plugin:

```bash
mvn clean package
```

Requires Java 21, Paper 1.21.4 and a TimingSystem build that supports late qualifying entries. Rerun
the TimingSystem install whenever its source changes. FancyHolograms 2.8.0 is compiled against but
only needed at runtime if you want the leaderboards.
