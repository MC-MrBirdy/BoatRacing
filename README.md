<!-- Language switcher with flags (hatscripts circle-flags) -->
<p align="right">
	<a href="#en" title="English">
		<img src="https://hatscripts.github.io/circle-flags/flags/gb.svg" width="18" height="18" alt="English" /> English
	</a>
</p>

<a id="en"></a>
# BoatRacing

[![Modrinth](https://img.shields.io/modrinth/v/boatracing?logo=modrinth&label=Modrinth)](https://modrinth.com/plugin/boatracing) [![Downloads](https://img.shields.io/modrinth/dt/boatracing?logo=modrinth&label=Downloads)](https://modrinth.com/plugin/boatracing) [![Minecraft](https://img.shields.io/badge/Minecraft-1.19--26.1-3b82f6)](https://modrinth.com/plugin/boatracing/versions) [![Java](https://img.shields.io/badge/Java-17%2B-22c55e)](https://adoptium.net/) [![Servers](https://img.shields.io/badge/Servers-Bukkit%20%7C%20Spigot%20%7C%20Paper%20%7C%20Purpur-f59e0b)](https://modrinth.com/plugin/boatracing)

[![Compatible with SimpleScore](https://img.shields.io/badge/Compatible%20with-SimpleScore-3fb950)](https://github.com/RuiPereiraDev/SimpleScore) [![Compatible with TAB](https://img.shields.io/badge/Compatible%20with-TAB-3fb950)](https://github.com/NEZNAMY/TAB)

[![bStats](https://bstats.org/signatures/bukkit/BoatRacing.svg)](https://bstats.org/plugin/bukkit/BoatRacing/26881)

[![Languages](https://img.shields.io/badge/Languages-16-0ea5e9)](#available-languages) [![Official](https://img.shields.io/badge/Official-2-22c55e)](#available-languages) [![Community](https://img.shields.io/badge/Community-14-f59e0b)](#available-languages)

An F1‒style ice boat racing plugin for Bukkit/Spigot (compatible with Paper/Purpur) with a clean, vanilla‒like GUI. Manage teams, configure tracks with the built‒in BoatRacing selection tool, run timed races with checkpoints, pit area penalties, and a guided setup wizard.

> Status: Public release (1.1.5)

<a id="snapshot-261-warning"></a>
> [!WARNING]
> Snapshot name: **snapshot-26.1-gui-fallback-01**
> Snapshot only for Paper 26.1.
> If your server version is not 26.1, please wait for the stable release.
> This build includes a temporary reflective fallback implementation for AnvilGUI, and some GUI/Anvil flows may still contain errors on certain server builds or forks.

<details>
<summary><strong>Snapshot 26.1 Note (snapshot-26.1-gui-fallback-01)</strong></summary>

- Snapshot target: Paper 26.1 first validation build.
- Implementation type: temporary reflective fallback for AnvilGUI close-event compatibility (`handleInventoryCloseEvent`).
- Known risk: GUI/anvil interactions may still fail on some 26.1 dev builds and non-Paper forks.
- Plan: keep this fallback until an official AnvilGUI release ships a stable 26.1 close-event fix.

</details>

See the changelog in [CHANGELOG.md](https://github.com/Jaie55/BoatRacing/blob/main/CHANGELOG.md).

This is how we test the plugin to validate its behavior after each update: see the QA checklist in [CHECKLIST.md](CHECKLIST.md)

<details>
<summary><strong>What's New (1.1.5)</strong></summary>

Practice mode, map vote flow upgrades, and track record placeholder refresh fixes:

Added:
- `/boatracing race practice <track>` starts a solo practice race on a ready track, so one player can train even if normal race minimum players is higher.
- Dedicated permission `boatracing.race.practice` (default `true`) so practice can be granted/revoked independently of race-admin permissions.
- Practice telemetry persisted in `practice-stats.yml` (best/last run, best/last lap, best/last sector per section).
- New practice placeholders for player current-track and explicit track tokens (run/lap/section metrics), plus track-practice state alias `%boatracing_track_practicerunning_<track>%`.
- Bundled Swedish community translation (`messages_sv.yml`, language code `sv`).

Changed:
- Same-track race/practice mutual lock now also applies during pre-start countdown, while other tracks remain independent (you can still practice on track2 if track1 has a race).
- Practice countdown/race split/lap/result messages are private to the practicing player (no global race-style broadcasts).
- `/boatracing race voteopen` now supports opening a vote with all saved tracks via `all` (or no explicit track list).
- Vote-start broadcast now includes both clickable UI (`/boatracing race voteui`) and a plain typed command instruction (`/{label} race vote <track>`) for mixed/Bedrock clients.
- When vote ends (timeout or `voteclose`), winner resolution now attempts to auto-open winner registration; if auto-open is not possible, fallback next-step command remains in chat.

Fixed:
- `%boatracing_track_best_*_<track>%` and `%boatracing_track_top_1..3_*_<track>%` now use the freshest available track data (live race session first, then track file), avoiding stale best-time values.
- `%boatracing_track_best_player%` and `%boatracing_track_best_time%` now follow the same resolution path as token placeholders so current-track records update after an improved race time.

</details>

<details>
<summary><strong>What's New (1.1.4)</strong></summary>

This 1.1.4 release combines everything delivered in `snapshot-26.1-gui-fallback-01` plus the final release additions.

- **Added**: track best-record placeholders by token `%boatracing_track_best_player_<track>%`, `%boatracing_track_best_time_<track>%`, and `%boatracing_track_best_time_ms_<track>%` for per-track record labels.
- **Added**: track top-3 placeholders by token `%boatracing_track_top_1_*_<track>%`, `%boatracing_track_top_2_*_<track>%`, and `%boatracing_track_top_3_*_<track>%` (`player`, `time`, `time_ms`) for podium/leaderboard layouts.
- **Changed**: includes the Paper 26.1 GUI/Anvil reflective compatibility path introduced during the snapshot validation cycle.
- **Changed**: race start respects `racing.min-players-to-start` (global), with optional per-track override in `tracks/<name>.yml` under `racing.min-players-to-start`.
- **Changed**: blocked starts use language key `race.not-enough-players` with `{min}` and `{current}` and enforce the same threshold across `start`, `force`, admin race GUI start, and registration timeout auto-start.
- **Fixed**: scoreboard tie-break by checkpoint arrival; on the same lap/checkpoint, the racer who entered first remains ahead (no equal-checkpoint swap).
- **Fixed**: setup wizard Done step now uses `/boatracing race open unsaved` when no named track is selected, avoiding the invalid `/boatracing race open <track>` placeholder token.
- **Docs**: README documents track record placeholder rows and CHECKLIST includes explicit validation steps.

</details>

<details>
<summary><strong>What's New (1.1.3)</strong></summary>

Track-aware placeholders and spawn reliability improvements:
- **Track-scoped race placeholders**: added `%boatracing_track_race_running_<track>%`, `%boatracing_track_race_registering_<track>%`, and `%boatracing_track_race_status_<track>%` to support per-track displays in scoreboards/holograms.
- **Compatibility aliases for prior naming**: `%boatracing_track_racerunning_<track>%` and `%boatracing_track_raceregistering_<track>%` map to the same values.
- **Parallel race sessions by track**: races are now managed per track session, so different tracks can run registration/races at the same time.
- **Map vote command flow**: added `/boatracing race voteopen`, `vote`, `voteui`, `votestatus`, and `voteclose` to coordinate map selection in chat.
- **Vote-start instructions for mixed clients**: when a map vote opens, players receive both a clickable `/boatracing race voteui` action and a plain typed-command instruction (`/{label} race vote <track>`) for clients where chat click actions are unavailable.
- **Selected boat variant reliability**: race boat/raft variants now re-apply after spawn with delayed retries to reduce cases where selected variants appear as default OAK.
- **No dismount during pre-start and race**: racers are now prevented from manually exiting boats/rafts during the 5-light countdown and while the race is active.
- **Reward command compatibility hardening**: reward parsing now supports both `commands` (list) and legacy `command` (single string), with safer fallback behavior for missing per-position keys (including 1st place).

</details>

<details>
<summary><strong>What's New (1.1.2)</strong></summary>

Placeholders, wizard UX and i18n refinements:
- **PlaceholderAPI integration**: BoatRacing now registers `%boatracing_*%` placeholders for holograms/scoreboards (player/team data, live race values, records, wins and top rankings).
- **Persistent race stats**: new aggregated stats storage (`stats.yml`) for player wins, team wins, best race and best lap.
- **Wizard readability pass**: setup wizard prompts are now more compact and step-focused to reduce chat noise.
- **Registration announce fully i18n-based**: registration broadcast template now lives in `messages_<lang>.yml` (`race.registration.announce`) instead of `config.yml`.
- **Lobby back flow**: added `/boatracing race back`; after race finish/cancel players return to the waiting lobby, get a clickable back hint, and can return to their pre-lobby location within a 3-minute in-memory window.
- **Expanded bundled language coverage**: added and reviewed community bundles for `fr`, `pt_BR`, `pt_PT`, `es_419`, `de`, `it`, `pl`, `tr`, `ja`, and `ko`; Chinese is now split into `zh_TW` (Taiwan, Traditional) and `zh_CN` (Mainland, Simplified).
- **Race boat cleanup reliability**: race-spawned boats/rafts are now tracked and removed on finish/cancel/reset to prevent leftover vehicle entities.

</details>

<details>
<summary><strong>What's New (1.1.1)</strong></summary>

Lobby and stability updates:
- **Optional registration lobby zone**: new `racing.lobby.*` config block. When enabled, players are teleported to a configurable lobby zone/location when they join registration.
- **Quick lobby command**: new `/boatracing setup setlobby` command saves your current position as the registration lobby and enables it automatically.
- **Return to previous location**: with `racing.lobby.return-on-leave: true`, players return to their original location when they leave registration or when registration is cancelled.
- **Active track safety**: race commands no longer force a disk reload when the requested track is already active (notably `unsaved`), avoiding stale in-memory state issues.
- **Track reload consistency**: `TrackConfig.load()` now clears all in-memory collections before reading from disk.
- **SimpleScore compatibility hook**: when SimpleScore is installed, BoatRacing now integrates with its hide/show viewer flow during races to prevent sidebar ownership conflicts and restore the external scoreboard after stop/cancel.
- **Registration restart-loop fix**: fixed a state/timer issue where an old `race open` countdown could survive `start/force/stop` paths and re-trigger race starts unexpectedly.
- **Setup clickable UX**: setup wizard/admin tips now suggest commands in chat when arguments are needed, so players can tab-complete before execution.
- **Lobby messages translated**: lobby teleport/return feedback added to EN/ES/zh_TW/zh_CN/ru message files.

Explicit compatibility:
- **SimpleScore**: BoatRacing includes explicit compatibility with SimpleScore.
- GitHub: https://github.com/RuiPereiraDev/SimpleScore
- Modrinth: https://modrinth.com/plugin/simplescore
- **TAB**: BoatRacing includes explicit compatibility with TAB.
- GitHub: https://github.com/NEZNAMY/TAB
- Modrinth: https://modrinth.com/plugin/tab-was-taken

</details>

<details>
<summary><strong>What's New (1.1.0)</strong></summary>

Languages and player controls:
- **Multi-language support**: messages are now fully translatable. Configure language in config.yml (`language`). Bundled options are `en`, `es`, `es_419`, `fr`, `pt_BR`, `pt_PT`, `de`, `it`, `pl`, `tr`, `ja`, `ko`, `sv`, `zh_TW`, `zh_CN`, and `ru`, and custom bundles are also supported by adding `messages_<lang>.yml` to the plugin folder. Reload with `/boatracing reload` to switch languages without restart.
- **Player-controlled race management**: new config option `player-actions.allow-player-race-start` (default: false) lets non-admin players open, start, force-start and stop races. Can be overridden per-track via `racing.allow-player-start: true` in individual track configs.
- **Reward system**: full customizable race-end rewards. Configure under `racing.rewards` with position-specific commands, messages and broadcasts. Supports placeholders: {player}, {position}, {time}, {track}, {laps}. Per-track rewards override the global config.
- **Performance**: PlayerMoveEvent throttle — checkpoint detection now only triggers when entering a new block, not every sub-meter movement.
- **Complete i18n infrastructure**: all plugin messages (race, setup, team, admin) updated to use the new externalized message system with dynamic placeholder support.

Previous versions:
- **1.0.9**: Compatibility across 1.19–1.21.11; safe boat/raft materials; Bukkit/Spigot classification on Paper.

</details>

<details>
<summary><strong>What's New (1.0.9)</strong></summary>

Compatibility and fixes:
- Official support: Bukkit/Spigot/Paper/Purpur 1.19 → 26.1.1. Requires Java 17+.
- Safer boat types across versions: dynamic Material resolution for boats/rafts (including Bamboo Raft and Pale Oak variants) avoids NoSuchFieldError on older APIs and removes CraftLegacy warnings.
- Classified as a Bukkit/Spigot plugin on Paper (paper-plugin.yml excluded from the JAR). Paper-only APIs replaced with Bukkit-safe calls.
- Docs: README, CHANGELOG and QA checklist updated (EN/ES).
 
Updater cadence:
- Background checks still run every 5 minutes. When a new version is first detected during runtime, a console WARN is printed immediately (once per version).
- Hourly reminder aligned to the top of each hour (00:00, 01:00, …) while outdated (respects `updates.console-warn`).
- Admin join: always notifies in chat (if enabled), never prints to console on player join.

</details>

<details>
<summary><strong>What's New (1.0.8)</strong></summary>

Improvements and toggles:
 - Customizable HUD: new config flags to show/hide parts of the sidebar and ActionBar.
	 - `racing.ui.scoreboard.show-position|show-lap|show-checkpoints|show-pitstops|show-name`
	 - `racing.ui.actionbar.show-lap|show-checkpoints|show-pitstops|show-time`
 - Pitstops on HUD: when `racing.mandatory-pitstops > 0`, show “PIT A/B” on the sidebar and “Pit A/B” in the ActionBar (gated by the toggles above).
 - Registration broadcast now includes the track name and the exact join command (language key `race.registration.announce` in `messages_<lang>.yml`).
 - Sidebar order switched to “L/CP - Name”; removed centering/padding; names shown as-is (keeps leading '.' for Bedrock).
 - Finish attempt message: crossing the finish line without all required checkpoints now shows a clear player message (in addition to the denial sound).
 - Setup Wizard: new optional step “Mandatory pit stops” with quick buttons [0] [1] [2] [3].
 - Setup command: `/boatracing setup setpitstops <n>` sets and persists `racing.mandatory-pitstops`.
 - Results broadcast: podium medals 🥇/🥈/🥉 and rank colors for top‑3; keeps a penalty suffix when present; names rendered safely (keeps leading '.' for Bedrock).
 - Wizard flow: if a default pit is already set in step 4, the wizard automatically advances to Checkpoints (team pits remain optional).
 - Permissions: introduced wildcard `boatracing.*`. Admins still get absolutely all plugin permissions, now by explicit children under `boatracing.admin` to avoid circular inheritance.
 - Tab-complete: players (non-admin) see `join|leave|status|vote|voteui|votestatus` under `/boatracing race`; admin-only verbs (`open|start|force|stop|voteopen|voteclose`) are suggested only to admins.
 
Updater:
 - Console notice restored: a single WARN shortly after startup when outdated, plus an hourly reminder while still outdated (respects `updates.console-warn`).
 - Admin join: when an admin joins, a quick check runs (throttled) and notifies them within seconds if a new update was just published.

</details>

<details>
<summary><strong>What's New (1.0.7)</strong></summary>

Bugfixes and quality-of-life:
 - Console update check noise removed: only a single WARN shortly after startup when you are outdated (respecting `updates.console-warn`). Periodic 5‑minute checks remain but are silent.
 
 - Stability: network errors during update checks are logged at most once per server run.
 
 Removal:
 - The built‑in hiding of vanilla scoreboard numbers has been removed. If you want to hide the sidebar’s right‑side numbers, use an external plugin for now while a future built‑in approach is evaluated.
 
 UI:
 - Scoreboard redesigned: centered rows, compact “Name - L X/Y CP A/B” layout, rank colors (1=gold, 2=silver-ish, 3=bronze-ish), and your own name in green.

</details>

<details>
<summary><strong>What's New (1.0.6)</strong></summary>

Improvements and tweaks:
 - New sidebar leaderboard: the sidebar now shows the top‑10 positions in real time. Personal stats moved to the ActionBar.
 - Personal HUD: your Lap, CP and Elapsed Time now appear in the ActionBar, updated every 0.5s.
 - Sector and finish gaps: compact messages show your time gap vs the lap/finish leader at each checkpoint and at lap finish (and vs winner at race finish).
 - Start lights jitter: optional random jitter added to the lights‑out delay via `racing.lights-out-jitter-seconds`.
 - Live leaderboard: sidebar shows the top‑10 positions; your Lap/CP/Time are shown in the ActionBar (auto‑created on race start and cleaned up on stop/reset).
 - Vanilla numbers hidden: the sidebar’s right‑side numbers are hidden natively when supported by your server (Paper 1.20.5+); no TAB plugin required.
 - Layout polish: names are left‑aligned and the whole " - Lap X/Y [CP]" block is centered. Removed the decorative separator and arrow prefix, compact/dynamic padding based on the longest visible name. Long names are truncated with "..." and no extra padding is added after the ellipsis. Your own name is highlighted in green (no bold).
 - FIN label: standardized to “FINISHED”.
 - Display names: supports EssentialsX displayName and strips common rank wrappers like [Admin]/(Rank) at the start for cleaner alignment.

</details>

<details>
<summary><strong>What's New (1.0.5)</strong></summary>

Fixes and polish:
 - Team member persistence: team members are preserved across updates/reloads/startup; loading restores teams without re‑applying capacity limits.
 - Setup pit command: `/boatracing setup setpit [team]` accepts team names with spaces when quoted (e.g., "/boatracing setup setpit \"Toast Peace\""); tab‑completion suggests quoted names when the input starts with a quote.
 - Config defaults: on plugin update or `/boatracing reload`, new default keys are merged into your existing `config.yml` without overwriting your changes.
 - Boat/Raft type: racers are mounted in their selected wood variant (including chest variants and rafts) instead of always OAK; works across API versions with a safe fallback.

</details>

<details>
<summary><strong>What's New (1.0.4)</strong></summary>

- Team-specific pit areas: new unified command `/boatracing setup setpit [team]` sets the default pit when no team is provided, or the pit for a specific team when a team name is given. Tab‑completion suggests team names.
- Mandatory pitstops: new `racing.mandatory-pitstops` config (default 0). When > 0, racers must complete at least that many pit exits before they are allowed to finish; pitstops are counted on exiting the pit area and persist for the whole race.
- Wizard: Pit step updated to mention default pit vs per‑team pits and to guide the flow with clickable tips.
 - Config updates: on plugin updates/reloads, new `config.yml` keys are merged into your existing file without overwriting your changes.
 - Boat type: racers are mounted in their selected boat/raft wood variant (including chest variants) instead of always OAK; works across API versions with a safe fallback.
- Permissions: players can use `join`, `leave`, and `status` by default; only `open|start|force|stop` remain admin‑only. Removed extra runtime checks that could block players with permissive defaults.
- Boats: spawned boats now respect the player’s selected wood type robustly across API versions; falls back to OAK if the enum value is not available.
- Per‑player start slots and grid ordering: new setup commands `/boatracing setup setpos <player> <slot|auto>` and `/boatracing setup clearpos <player>`. On race start, players bound to a slot are placed there first; remaining racers are ordered by their best recorded race time on that track (fastest first), and racers without a time are placed last.
- Setup show: now also displays the presence of team‑specific pits and the number of custom start positions configured.
 - Wizard (Starts): shows optional buttons for per‑player custom slots — setpos/clearpos/auto — and displays the number of custom slots configured.

</details>

<details>
<summary><strong>What's New (1.0.3)</strong></summary>

- Admin Tracks GUI: manage multiple named tracks — Create and select, Delete (with confirmation), and Reapply selected. Requires `boatracing.setup`.
- Admin Race GUI: manage race lifecycle from a GUI — open/close registration, start/force/stop, quick-set laps, remove registrants, and handy setup tips.
- Terminology: “loaded” → “selected”; “pit lane” → “pit area”.
- All track configuration lives per‑track under `plugins/BoatRacing/tracks/<name>.yml`.
	- On startup, a legacy `track.yml` (if present) is migrated automatically to `tracks/default.yml` (with in‑game admin notice).
- Setup Wizard UX: concise, colorized, clickable. Adds a Laps step and an explicit Finish button; navigation buttons now use emojis (⟵, ℹ, ✖) and the blank line is placed at the top of the block for readability.
- Selection tool: built‑in wand (Blaze Rod). Left‑click = Corner A, right‑click = Corner B. Richer `/boatracing setup selinfo` diagnostics.
- Race commands now require a track argument: `open|join|leave|force|start|stop|status <track>`.
- Race lifecycle: “race stop” cancels registration and any running race for that track. Starts enforce unique grid slots, face forward (pitch 0), and auto‑mount racers into their selected boat. “force” and “start” use only registered participants.
- Tab‑completion: for race subcommands that take `<track>` (including `status`), it suggests existing track names.
- Admin Tracks GUI: after creating a track, sends a clickable tip to paste `/boatracing setup wizard` in chat.
- Messages remain English‑only; denial texts are hardcoded.

- Start lights + false starts: configure exactly 5 Redstone Lamps and enjoy an F1-style left-to-right light-up countdown (no redstone wiring needed). Moving forward during the countdown (false start) applies a configurable time penalty.
 - Race permissions: split by subcommand. Players can `join`, `leave`, and `status` by default; admin actions `open|start|force|stop` require `boatracing.race.admin` (or `boatracing.setup`).
- Pit area and checkpoints are now optional. Track readiness only requires at least one start slot and a finish line; the wizard labels Pit area and Checkpoints as “(optional)” and lets you skip them.
- Removed “Save as…” from the Tracks GUI (create/select, delete, and reapply remain).
 - New: live in‑race scoreboard per participant showing Lap, Checkpoints, and Elapsed Time.
 - New: crossing the pit area counts as finish for lap counting once all lap checkpoints are completed (still applies pit penalty when enabled).

</details>

## Available Languages

Official translations: <img src="https://hatscripts.github.io/circle-flags/flags/gb.svg" width="16" height="16" alt="English" /> <img src="https://hatscripts.github.io/circle-flags/flags/es.svg" width="16" height="16" alt="Espanol" />
[![en official](https://img.shields.io/badge/en-official-22c55e)](#available-languages) [![es official](https://img.shields.io/badge/es-official-22c55e)](#available-languages)

Community translations: <img src="https://hatscripts.github.io/circle-flags/flags/fr.svg" width="16" height="16" alt="French" /> <img src="https://hatscripts.github.io/circle-flags/flags/br.svg" width="16" height="16" alt="Portuguese Brazil" /> <img src="https://hatscripts.github.io/circle-flags/flags/pt.svg" width="16" height="16" alt="Portuguese Portugal" /> <img src="https://hatscripts.github.io/circle-flags/flags/mx.svg" width="16" height="16" alt="Spanish Latin America" /> <img src="https://hatscripts.github.io/circle-flags/flags/de.svg" width="16" height="16" alt="German" /> <img src="https://hatscripts.github.io/circle-flags/flags/it.svg" width="16" height="16" alt="Italian" /> <img src="https://hatscripts.github.io/circle-flags/flags/pl.svg" width="16" height="16" alt="Polish" /> <img src="https://hatscripts.github.io/circle-flags/flags/tr.svg" width="16" height="16" alt="Turkish" /> <img src="https://hatscripts.github.io/circle-flags/flags/jp.svg" width="16" height="16" alt="Japanese" /> <img src="https://hatscripts.github.io/circle-flags/flags/kr.svg" width="16" height="16" alt="Korean" /> <img src="https://hatscripts.github.io/circle-flags/flags/se.svg" width="16" height="16" alt="Swedish" /> <img src="https://hatscripts.github.io/circle-flags/flags/tw.svg" width="16" height="16" alt="Chinese (Taiwan, Traditional)" /> <img src="https://hatscripts.github.io/circle-flags/flags/cn.svg" width="16" height="16" alt="Chinese (Mainland, Simplified)" /> <img src="https://hatscripts.github.io/circle-flags/flags/ru.svg" width="16" height="16" alt="Russian" />
[![fr community](https://img.shields.io/badge/fr-community-f59e0b)](#available-languages) [![pt_BR community](https://img.shields.io/badge/pt_BR-community-f59e0b)](#available-languages) [![pt_PT community](https://img.shields.io/badge/pt_PT-community-f59e0b)](#available-languages) [![es_419 community](https://img.shields.io/badge/es_419-community-f59e0b)](#available-languages) [![de community](https://img.shields.io/badge/de-community-f59e0b)](#available-languages) [![it community](https://img.shields.io/badge/it-community-f59e0b)](#available-languages) [![pl community](https://img.shields.io/badge/pl-community-f59e0b)](#available-languages) [![tr community](https://img.shields.io/badge/tr-community-f59e0b)](#available-languages) [![ja community](https://img.shields.io/badge/ja-community-f59e0b)](#available-languages) [![ko community](https://img.shields.io/badge/ko-community-f59e0b)](#available-languages) [![sv community](https://img.shields.io/badge/sv-community-f59e0b)](#available-languages) [![zh_TW community](https://img.shields.io/badge/zh_TW-community-f59e0b)](#available-languages) [![zh_CN community](https://img.shields.io/badge/zh_CN-community-f59e0b)](#available-languages) [![ru community](https://img.shields.io/badge/ru-community-f59e0b)](#available-languages)

Available codes and names:
- `en` English (official)
- `es` Español (España) (official)
- `es_419` Español (Latinoamerica)
- `fr` Francais
- `pt_BR` Portugues (Brasil)
- `pt_PT` Portugues (Portugal)
- `de` Deutsch
- `it` Italiano
- `pl` Polski
- `tr` Turkce
- `ja` Japanese
- `ko` Korean
- `sv` Svenska
- `zh_TW` Chinese (Taiwan, Traditional)
- `zh_CN` Chinese (Mainland, Simplified)
- `ru` Russian

## Features
- Team GUI for players: browse teams, open team view, join/leave, manage your racer number and boat type, and optionally rename/change color/disband from the GUI when enabled in config.
- Admin tooling: dedicated GUIs for teams, players, race control, and named tracks, plus command equivalents for scripting or console-style workflows.
- Named tracks: each track lives in its own YAML file and can override core race settings such as laps, mandatory pit stops, registration time, penalties, and player race-start permissions.
- Built-in setup flow: Blaze Rod selection wand, cuboid region tools, clickable setup tips, and a compact guided wizard with auto-advance where possible.
- Grid control: custom per-player start slots, auto placement for the rest, and fallback ordering by best recorded track time.
- Race systems: ordered checkpoints, optional pit area, optional mandatory pit stops, false-start penalties, registration lobby teleport/return, 5-light countdown, and live results broadcasting.
- Multi-track race orchestration: independent race sessions per track and map-vote commands for admins/players in chat.
- HUD and scoreboard: in-race sidebar plus ActionBar with per-section config toggles, safe scoreboard restoration after races, and compatibility flow for external scoreboards.
- Persistent stats: `stats.yml` stores wins, best race and best lap so PlaceholderAPI, holograms, scoreboards, and NPCs can show live and historical data.
- i18n: bundled message packs for `en`, `es`, `es_419`, `fr`, `pt_BR`, `pt_PT`, `de`, `it`, `pl`, `tr`, `ja`, `ko`, `sv`, `zh_TW`, `zh_CN`, and `ru`; `en`/`es` are official and the rest are community translations. All are hot-reloadable with `/boatracing reload`.
- Rewards, updates, and metrics: per-position race rewards, Modrinth update checks, and optional bStats metrics.

## Requirements
- Java 17+
- A Bukkit-compatible server running Minecraft 1.19 to 1.21.11

## Supported Servers
- CraftBukkit
- Spigot
- Paper
- Purpur
- Folia

Other Bukkit-compatible forks may work, but they are not officially verified in this repository.

## Platform Notes
- Single plugin jar for the Bukkit family. `plugin.yml` targets the Bukkit API, and the Paper metadata also marks the plugin as Folia-supported.
- Proxies such as Velocity and BungeeCord are not execution targets for BoatRacing gameplay logic.
- Sponge and Forge hybrid servers such as Mohist, Magma, or Arclight are not supported by this jar.

## Install
1. Download the latest BoatRacing jar from Modrinth: https://modrinth.com/plugin/boatracing
2. Put it in your `plugins/` folder.
3. Start the server once to generate config, messages, teams, racers, stats, and track files.
4. Optionally install PlaceholderAPI if you want `%boatracing_*%` placeholders.

## Usage
Quick flow:
1. Run `/boatracing teams` and create or join a team.
2. Set your racer number and boat type.
3. Create or select a track from the Admin Tracks GUI, or start directly with the setup wand on the active track.
4. Configure starts, finish, lights, and optional pit/checkpoints.
5. Open registration with `/boatracing race open <track>`.
6. Let players join, then start or force-start the race.

Root command groups:
- `/boatracing teams` opens the player team GUI.
- `/boatracing race ...` manages registration and race lifecycle.
- `/boatracing setup ...` configures the active track.
- `/boatracing admin` opens the admin GUI.
- `/boatracing reload` reloads config, messages, teams, racers, and stats.
- `/boatracing version` shows plugin version and update status.

## Track Setup
Use the built-in BoatRacing selection wand to define cuboid regions.

- Left-click a block with the wand: set Corner A.
- Right-click a block with the wand: set Corner B.
- Wand item: Blaze Rod named `BoatRacing Selection Tool`.

Setup commands:
- `/boatracing setup help` shows the setup command list.
- `/boatracing setup wand` gives the selection wand.
- `/boatracing setup addstart` adds your current position as a start slot.
- `/boatracing setup clearstarts` removes all start slots.
- `/boatracing setup setfinish` saves the current selection as the finish region.
- `/boatracing setup setpit [team]` saves the current selection as the default pit or a team-specific pit.
- `/boatracing setup addcheckpoint` appends a checkpoint in order.
- `/boatracing setup clearcheckpoints` removes all checkpoints.
- `/boatracing setup addlight` adds the Redstone Lamp you are looking at as a start light.
- `/boatracing setup clearlights` removes all start lights.
- `/boatracing setup setlaps <n>` saves the lap count for the active track.
- `/boatracing setup setpitstops <n>` saves mandatory pit stops for the active track.
- `/boatracing setup setlobby` stores your current location as the registration lobby and enables it.
- `/boatracing setup setpos <player> <slot|auto>` binds a player to a custom start slot or clears the binding with `auto`.
- `/boatracing setup clearpos <player>` removes a custom start slot binding.
- `/boatracing setup show` prints a summary of starts, lights, finish, pit, team pits, checkpoints, custom slots, pit stops, and active per-track overrides.
- `/boatracing setup selinfo` prints selection debug information.

Setup rules:
- A track is race-ready when it has at least one start slot and a finish region.
- Pit area is optional.
- Checkpoints are optional.
- Start lights are optional for track validity, but if used the system expects exactly 5.
- Team-specific pit regions are supported alongside the default pit region.

### Guided Setup Wizard
Start it with `/boatracing setup wizard`.

Wizard flow:
- Starts
- Finish
- Start lights
- Pit area
- Checkpoints
- Mandatory pit stops
- Laps

Wizard behavior:
- The wizard is chat-driven and uses clickable buttons for the next action.
- It auto-advances when a step becomes valid.
- Optional steps support skip.
- Sub-actions like `back`, `status`, `cancel`, `skip`, `next`, and `finish` exist, but are intentionally not shown in tab-completion to keep the entrypoint simple.
- The Starts step also exposes quick buttons for custom start-slot management.

## Racing and Registration
Race commands:
- `/boatracing race help`
- `/boatracing race open <track>`
- `/boatracing race join <track>`
- `/boatracing race leave <track>`
- `/boatracing race back`
- `/boatracing race start <track>`
- `/boatracing race force <track>`
- `/boatracing race practice <track>`
- `/boatracing race stop <track>`
- `/boatracing race status <track>`
- `/boatracing race voteopen [all|<track1> <track2> ...] [seconds]`
- `/boatracing race vote <track>`
- `/boatracing race voteui`
- `/boatracing race votestatus`
- `/boatracing race voteclose`

Race behavior:
- Players must belong to a team before joining registration.
- Registration loads track settings before opening, including any per-track `racing.*` overrides.
- Multiple race sessions can run in parallel on different tracks.
- A player can only be registered/racing in one session at a time.
- Tab-complete includes `vote`, `voteui`, and `votestatus` for players, plus `voteopen` and `voteclose` for admins.
- `/boatracing race vote` without `<track>` opens the vote UI when a map vote is active (same behavior as `/boatracing race voteui`).
- `/boatracing race voteopen all [seconds]` (or `/boatracing race voteopen [seconds]`) opens a vote with all saved tracks.
- Opening a vote with `/boatracing race voteopen ...` requires `boatracing.race.voteopen` (also granted by `boatracing.race.admin` or `boatracing.setup`).
- `/boatracing race voteopen ...` announces both a clickable vote UI action (`/boatracing race voteui`) and a plain vote command instruction (`/{label} race vote <track>`).
- When map voting ends, the winning track registration is opened automatically (if the track is ready and not busy), so players immediately receive the normal join command announcement.
- `start` and `force` use registered players only.
- `start`, `force`, GUI start, and registration timeout auto-start require at least `racing.min-players-to-start` online registered players.
- `practice` starts a solo race directly on the selected track (ready track required), without waiting for registration/min-player checks.
- The minimum start threshold defaults to `1`, can be overridden per track via `tracks/<name>.yml` (`racing.min-players-to-start`), and shows `race.not-enough-players` when not met.
- Grid order is: custom start slot bindings first, then best recorded track times, then players without a recorded time.
- Boats are spawned using each player’s selected boat type with cross-version-safe material resolution.
- If checkpoints exist, a lap only counts once all checkpoints have been collected in order.
- Pit entry can apply a time penalty and pit exits count toward mandatory pit-stop requirements.
- False starts can apply an additional penalty during the light countdown.
- If registration lobby is enabled, joining registration teleports players there.
- Leaving registration or registration cancellation can return players to their previous location (`racing.lobby.return-on-leave`).
- After a race finish or race cancel, participants are returned to the lobby and receive a clickable `/boatracing race back` hint in chat.
- The saved pre-lobby return location is kept in memory for 3 minutes; after that, `/boatracing race back` expires for that race cycle.
- Results are sorted by elapsed time plus penalties and broadcast to online players.
- The winner updates persistent player/team stats used by placeholders.

Player-managed races:
- By default, race control is admin-only.
- Non-admin players can also open/start/force/stop races when `player-actions.allow-player-race-start: true` is enabled globally.
- A track can override that via `tracks/<name>.yml` with `racing.allow-player-start: true|false`.

## Tab Completion
Root suggestions:
- `teams`, `race`, `setup`, `admin`, `reload`, `version` depending on permissions.

Teams suggestions:
- `create`, `rename`, `color`, `join`, `leave`, `boat`, `number`, `confirm`, `cancel`
- `join` suggests team names.
- `color` suggests all dye colors.
- `boat` suggests supported boat and chest-boat variants, plus bamboo rafts.

Race suggestions:
- Everyone sees `help`, `join`, `leave`, `status`, `vote`, `voteui`, `votestatus`.
- Players with `boatracing.race.back` also see `back`.
- Players with `boatracing.race.practice` also see `practice`.
- Players with `boatracing.race.voteopen` (or admin-capable users) also see `voteopen`.
- Admin-capable users also see `open`, `start`, `force`, `stop`, `voteclose`.
- Track-taking subcommands (`open`, `join`, `leave`, `force`, `start`, `practice`, `stop`, `status`, `vote`) suggest existing named tracks.

Setup suggestions:
- `help`, `addstart`, `clearstarts`, `setfinish`, `setpit`, `addcheckpoint`, `clearcheckpoints`, `addlight`, `clearlights`, `setlaps`, `setpitstops`, `setlobby`, `setpos`, `clearpos`, `show`, `selinfo`, `wand`, `wizard`
- `setpit` suggests team names, quoting names with spaces when needed.
- `setpos` and `clearpos` suggest online and known offline player names.
- `setpos` also suggests `auto` and available slot numbers.

Admin suggestions:
- `help`, `team`, `player`, `tracks`
- `team` suggests `create`, `delete`, `rename`, `color`, `add`, `remove`
- `player` suggests `setteam`, `setnumber`, `setboat`

## Admin Commands and GUI
`/boatracing admin` opens the admin hub.

Admin GUI sections:
- Teams: create teams, open team view, rename, recolor, add/remove members, delete teams.
- Players: assign/remove team, set racer number, set boat type.
- Race: open/close registration, start/force/stop, quick-set laps, custom laps, and remove registrants.
- Tracks: create/select/delete named tracks and reapply the selected track from disk.

Command equivalents:
- `/boatracing admin team create <name> [color] [firstMember]`
- `/boatracing admin team delete <name>`
- `/boatracing admin team rename <old> <new>`
- `/boatracing admin team color <name> <DyeColor>`
- `/boatracing admin team add <name> <player>`
- `/boatracing admin team remove <name> <player>`
- `/boatracing admin player setteam <player> <team|none>`
- `/boatracing admin player setnumber <player> <1-99>`
- `/boatracing admin player setboat <player> <BoatType>`
- `/boatracing admin tracks`

## Permissions
- `boatracing.*` (default: false): wildcard for the full plugin.
- `boatracing.use` (default: true): base meta permission.
- `boatracing.teams` (default: true): access to `/boatracing teams`.
- `boatracing.version` (default: true): access to `/boatracing version`.
- `boatracing.reload` (default: op): access to `/boatracing reload`.
- `boatracing.update` (default: op): receive in-game update notices.
- `boatracing.setup` (default: op): access to track setup, wizard, selection, and setup GUIs.
- `boatracing.admin` (default: op): access to the admin hub and admin management features.
- `boatracing.race.join` (default: true): join registration.
- `boatracing.race.leave` (default: true): leave registration.
- `boatracing.race.back` (default: true): return to the saved pre-lobby location.
- `boatracing.race.status` (default: true): check track race status.
- `boatracing.race.practice` (default: true): start solo practice mode on a ready track.
- `boatracing.race.voteopen` (default: op): open map voting with `/boatracing race voteopen`.
- `boatracing.race.admin` (default: op): manage races with `open`, `start`, `force`, `stop`, and `voteclose`.

Permission notes:
- `boatracing.admin` grants the other plugin permissions through explicit children.
- Players can always see the `race` root suggestion, but actual subcommands remain permission/config-gated.
- Non-admin race management is controlled by config and per-track overrides, not by a separate extra permission node.

## Configuration
Core:
- `prefix`: chat prefix.
- `language`: bundled values are `en`, `es`, `es_419`, `fr`, `pt_BR`, `pt_PT`, `de`, `it`, `pl`, `tr`, `ja`, `ko`, `sv`, `zh_TW`, `zh_CN`, and `ru`.
- `max-members-per-team`: team size limit.

Player actions:
- `player-actions.allow-team-create`
- `player-actions.allow-team-rename`
- `player-actions.allow-team-color`
- `player-actions.allow-team-disband`
- `player-actions.allow-set-boat`
- `player-actions.allow-set-number`
- `player-actions.allow-player-race-start`

Updates and metrics:
- `updates.enabled`
- `updates.console-warn`
- `updates.notify-admins`
- `bstats.enabled`

Global racing defaults:
- `racing.laps`
- `racing.mandatory-pitstops`
- `racing.pit-penalty-seconds`
- `racing.registration-seconds`
- `racing.min-players-to-start`
- `racing.false-start-penalty-seconds`
- `racing.enable-pit-penalty`
- `racing.enable-false-start-penalty`
- `racing.lights-out-delay-seconds`
- `racing.lights-out-jitter-seconds`

Registration lobby:
- `racing.lobby.enabled`
- `racing.lobby.return-on-leave`
- `racing.lobby.back-window-seconds`
- `racing.lobby.world`
- `racing.lobby.x`
- `racing.lobby.y`
- `racing.lobby.z`
- `racing.lobby.yaw`
- `racing.lobby.pitch`

HUD toggles:
- `racing.ui.scoreboard.show-position`
- `racing.ui.scoreboard.show-lap`
- `racing.ui.scoreboard.show-checkpoints`
- `racing.ui.scoreboard.show-pitstops`
- `racing.ui.scoreboard.show-name`
- `racing.ui.actionbar.show-lap`
- `racing.ui.actionbar.show-checkpoints`
- `racing.ui.actionbar.show-pitstops`
- `racing.ui.actionbar.show-time`

Rewards:
- `racing.rewards.enabled`
- `racing.rewards.positions.<place>.commands`
- `racing.rewards.positions.<place>.messages`
- `racing.rewards.positions.<place>.broadcast`
- Legacy compatibility: `racing.rewards.positions.<place>.command` (single string) is also accepted, but `commands` list is recommended.
- Recommended schema hygiene: keep `commands: []` explicitly present for each configured position block (including `1`).
- Supported reward placeholders: `{player}`, `{position}`, `{time}`, `{track}`, `{laps}`

Message templates:
- Registration announce text is language-specific and lives in `messages_<lang>.yml` under `race.registration.announce`.
- That template supports `{track}`, `{laps}`, `{cmd}`, and `{label}`.
- Minimum-player start warning is language-specific under `race.not-enough-players` and supports `{min}` and `{current}`.

Per-track overrides:
- Track files under `plugins/BoatRacing/tracks/<name>.yml` can override `racing.*` values.
- Common override example: `racing.min-players-to-start` for tracks that require larger grids.
- `setup show` prints the active override set for the current track.

## Updates and Metrics
- Update source: Modrinth.
- Startup can print a WARN if the plugin is outdated.
- Silent background checks run every 5 minutes.
- A newly detected version during runtime triggers a one-time console WARN for that version.
- Hourly reminder warnings remain active while outdated if `updates.console-warn` is enabled.
- Admin players with `boatracing.update` can receive in-game notices on join.
- bStats is enabled by default and can be disabled with `bstats.enabled`.

## Storage
- `plugins/BoatRacing/teams.yml`: team membership, colors, names, and current team leader.
- `plugins/BoatRacing/racers.yml`: per-player racer numbers and boat types.
- `plugins/BoatRacing/stats.yml`: player wins, team wins, best race, and best lap.
- `plugins/BoatRacing/tracks/<name>.yml`: one file per track containing starts, finish, pit areas, team pits, checkpoints, lights, custom slots, best times, and per-track racing overrides.

Legacy migration:
- If an old `plugins/BoatRacing/track.yml` is found, it is migrated to `plugins/BoatRacing/tracks/default.yml` (or `default_N.yml` if needed).

## Compatibility
- Minecraft: 1.19 to 1.21.11
- Java: 17+
- Server families: CraftBukkit, Spigot, Paper, Purpur, Folia
- Optional PlaceholderAPI support through a soft dependency
- SimpleScore compatibility hook for hiding/restoring external sidebars during races
- Compatible with TAB environments for scoreboard usage, without requiring a TAB-specific dependency
- Bundled languages: English, Spanish, French, Portuguese (Brazil), Portuguese (Portugal), German, Italian, Polish, Turkish, Japanese, Korean, Swedish, Chinese (Taiwan, Traditional), Chinese (Mainland, Simplified), Russian
- Note: `pt_BR`, `pt_PT`, `de`, `it`, `pl`, `tr`, `ja`, `ko`, and `sv` are bundled community files and may still require translation review.
- Custom languages are supported via `messages_<lang>.yml` in the plugin data folder and can be selected with `language: "<lang>"`.

## Placeholders (PlaceholderAPI)
If PlaceholderAPI is installed, BoatRacing registers `%boatracing_*%` placeholders for scoreboards, holograms, NPCs, and static displays.

Resolution rules:
- Missing text-like data usually resolves to `-`.
- Missing numeric data usually resolves to `0` or `-1` depending on the placeholder.
- `%boatracing_player_*%` uses the viewer as context.
- `%boatracing_player_*_<player>%` uses an explicit player name or UUID and is ideal for NPCs and static holograms.
- Team leader placeholders always resolve the current saved leader of that team.
- Track-scoped race placeholders (`%boatracing_track_race_*_<track>%`) resolve against the race session of the requested track.
- Track-scoped practice placeholders (`%boatracing_track_practice_running_<track>%`) resolve whether the requested track is currently in practice mode (including countdown).
- Track-scoped best-record placeholders (`%boatracing_track_best_*_<track>%`) resolve against the requested track token, not only the currently selected track.
- Compatibility aliases are available: `%boatracing_track_racerunning_<track>%` and `%boatracing_track_raceregistering_<track>%`.
- Compatibility alias is available for practice-running: `%boatracing_track_practicerunning_<track>%`.
- For track-scoped race placeholders, tracks without an active session resolve as `false` (`running`/`registering`) or `idle` (`status`).
- `<track>` tokens support underscores for spaces (for example `My_Track`).

### Category: Viewer Player and Team

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_player_name%` | Viewer player name | `Driver: jaie55` | Only the viewer sees their own value |
| `%boatracing_player_team_name%` / `%boatracing_player_team_id%` / `%boatracing_player_team_color%` | Viewer team identity | `Team: Sharks` / `Team ID: sharks` / `Color: AQUA` | Viewer context |
| `%boatracing_player_team_leader_name%` / `%boatracing_player_team_leader_id%` | Current leader of the viewer's team | `Leader: jaie55` | Viewer context |
| `%boatracing_player_team_players%` / `%boatracing_player_team_player_count%` | Viewer team roster and size | `Members: jaie55, KiluGod` / `Members: 2` | Viewer context |
| `%boatracing_player_number%` / `%boatracing_player_boat%` | Viewer racer number and selected boat | `Number: 7` / `Boat: OAK_BOAT` | Viewer context |

### Category: Viewer Live Race State

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_player_race_running%` / `%boatracing_player_race_registering%` | Whether the viewer is currently racing or currently registered in their own race session | `Running: true` / `Registering: false` | Viewer context |
| `%boatracing_player_practice_running%` | Whether the viewer is currently in a solo practice session | `Practice running: true` | Viewer context |
| `%boatracing_track_race_running_<track>%` / `%boatracing_track_race_registering_<track>%` / `%boatracing_track_race_status_<track>%` | Track-scoped race state (`running`, `registering`, `idle`) for a specific track token | `Harbor running: true` / `Harbor status: running` / `Desert status: idle` | Same for every viewer |
| `%boatracing_track_racerunning_<track>%` / `%boatracing_track_raceregistering_<track>%` | Backward-compatible aliases for track running/registering booleans | `Harbor running(alias): true` / `Desert registering(alias): false` | Same for every viewer |
| `%boatracing_track_practice_running_<track>%` / `%boatracing_track_practicerunning_<track>%` | Track-scoped practice state (true while practice countdown or run is active) | `Harbor practice: true` / `Harbor practice(alias): true` | Same for every viewer |
| `%boatracing_player_current_time%` / `%boatracing_player_current_time_ms%` | Live timer for the viewer | `Time: 1:42.355` / `TimeMs: 102355` | Viewer context |
| `%boatracing_player_current_lap%` / `%boatracing_player_current_checkpoint%` | Viewer lap and next checkpoint progression | `Lap: 2` / `Checkpoint: 5` | Viewer context |
| `%boatracing_player_current_position%` / `%boatracing_player_current_pitstops%` / `%boatracing_player_finished%` | Viewer live position, pit count, and finish state | `Pos: 1` / `Pit stops: 0` / `Finished: false` | Viewer context |

### Category: Viewer Practice Stats

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_player_practice_best_run%` / `%boatracing_player_practice_best_run_ms%` | Viewer best complete practice run on current track | `Practice best run: 1:10.245` | Viewer context |
| `%boatracing_player_practice_last_run%` / `%boatracing_player_practice_last_run_ms%` | Viewer last complete practice run on current track | `Practice last run: 1:12.040` | Viewer context |
| `%boatracing_player_practice_best_lap%` / `%boatracing_player_practice_best_lap_ms%` | Viewer best practice lap on current track | `Practice best lap: 0:33.112` | Viewer context |
| `%boatracing_player_practice_last_lap%` / `%boatracing_player_practice_last_lap_ms%` | Viewer last practice lap on current track | `Practice last lap: 0:34.506` | Viewer context |
| `%boatracing_player_practice_best_sector_<section>%` / `%boatracing_player_practice_best_sector_ms_<section>%` | Viewer best section split for current track | `%boatracing_player_practice_best_sector_2%` -> `0:10.820` | Viewer context |
| `%boatracing_player_practice_last_sector_<section>%` / `%boatracing_player_practice_last_sector_ms_<section>%` | Viewer last section split for current track | `%boatracing_player_practice_last_sector_2%` -> `0:11.064` | Viewer context |
| `%boatracing_player_practice_best_run_<track>%` / `%boatracing_player_practice_best_run_ms_<track>%` | Viewer best complete practice run for a specific track token | `%boatracing_player_practice_best_run_harbor%` -> `1:10.245` | Viewer context |
| `%boatracing_player_practice_last_run_<track>%` / `%boatracing_player_practice_last_run_ms_<track>%` | Viewer last complete practice run for a specific track token | `%boatracing_player_practice_last_run_harbor%` -> `1:12.040` | Viewer context |
| `%boatracing_player_practice_best_lap_<track>%` / `%boatracing_player_practice_best_lap_ms_<track>%` | Viewer best lap for a specific track token | `%boatracing_player_practice_best_lap_harbor%` -> `0:33.112` | Viewer context |
| `%boatracing_player_practice_last_lap_<track>%` / `%boatracing_player_practice_last_lap_ms_<track>%` | Viewer last lap for a specific track token | `%boatracing_player_practice_last_lap_harbor%` -> `0:34.506` | Viewer context |
| `%boatracing_player_practice_best_sector_<track>_<section>%` / `%boatracing_player_practice_best_sector_ms_<track>_<section>%` | Viewer best section split for a specific track token | `%boatracing_player_practice_best_sector_harbor_3%` -> `0:12.404` | Viewer context |
| `%boatracing_player_practice_last_sector_<track>_<section>%` / `%boatracing_player_practice_last_sector_ms_<track>_<section>%` | Viewer last section split for a specific track token | `%boatracing_player_practice_last_sector_harbor_3%` -> `0:12.980` | Viewer context |

### Category: Viewer Records and Wins

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_player_track_best%` / `%boatracing_player_track_best_ms%` | Viewer best time on the current track | `Track PB: 0:59.443` | Viewer context |
| `%boatracing_player_best_race%` / `%boatracing_player_best_race_ms%` | Viewer best race overall | `Best race: 1:40.010` | Viewer context |
| `%boatracing_player_best_lap%` / `%boatracing_player_best_lap_ms%` | Viewer best lap overall | `Best lap: 0:28.911` | Viewer context |
| `%boatracing_player_wins%` / `%boatracing_player_team_wins%` | Viewer wins and viewer team wins | `Wins: 12` / `Team wins: 18` | Viewer context |

### Category: Global and Top Stats

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_teams_count%` / `%boatracing_teams_list%` | Total number of teams and the team list | `Teams: 4` / `Teams: Sharks, Rockets, Drift, Wave` | Same for every viewer |
| `%boatracing_track_name%` / `%boatracing_track_best_player%` / `%boatracing_track_best_time%` | Current track name and its best record | `Track: harbor` / `Track record: jaie55 - 0:58.772` | Same for every viewer |
| `%boatracing_track_best_player_<track>%` / `%boatracing_track_best_time_<track>%` / `%boatracing_track_best_time_ms_<track>%` | Best record for a specific track token | `%boatracing_track_best_time_harbor%` -> `0:58.772` / `%boatracing_track_best_player_harbor%` -> `jaie55` | Same for every viewer |
| `%boatracing_track_top_1_player_<track>%` / `%boatracing_track_top_1_time_<track>%` / `%boatracing_track_top_1_time_ms_<track>%` | Track top 1 holder and time | `%boatracing_track_top_1_player_harbor%` -> `jaie55` / `%boatracing_track_top_1_time_harbor%` -> `0:58.772` | Same for every viewer |
| `%boatracing_track_top_2_player_<track>%` / `%boatracing_track_top_2_time_<track>%` / `%boatracing_track_top_2_time_ms_<track>%` | Track top 2 holder and time | `%boatracing_track_top_2_player_harbor%` -> `KiluGod` / `%boatracing_track_top_2_time_harbor%` -> `1:00.120` | Same for every viewer |
| `%boatracing_track_top_3_player_<track>%` / `%boatracing_track_top_3_time_<track>%` / `%boatracing_track_top_3_time_ms_<track>%` | Track top 3 holder and time | `%boatracing_track_top_3_player_harbor%` -> `RacerX` / `%boatracing_track_top_3_time_harbor%` -> `1:01.004` | Same for every viewer |
| `%boatracing_track_practice_running_<track>%` / `%boatracing_track_practicerunning_<track>%` | Practice-running state for a specific track token | `%boatracing_track_practice_running_harbor%` -> `true` | Same for every viewer |
| `%boatracing_top_player_wins_name%` / `%boatracing_top_player_wins%` | Player with most wins | `Top wins: jaie55 (29)` | Same for every viewer |
| `%boatracing_top_team_wins_name%` / `%boatracing_top_team_wins%` | Team with most wins | `Top team: Sharks (77)` | Same for every viewer |
| `%boatracing_top_player_best_race_name%` / `%boatracing_top_player_best_race%` | Best race holder and time | `Best race: KiluGod - 1:38.404` | Same for every viewer |
| `%boatracing_top_player_best_lap_name%` / `%boatracing_top_player_best_lap%` | Best lap holder and time | `Best lap: jaie55 - 0:27.950` | Same for every viewer |

### Category: Team Lookup by Name
Use `<team>` with the team token. Team names with spaces can be addressed using underscores in docs/examples.

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_team_leader_name_<team>%` / `%boatracing_team_leader_id_<team>%` | Current leader of a specific team | `%boatracing_team_leader_name_sharks%` -> `Leader: jaie55` | Same for every viewer |
| `%boatracing_team_players_<team>%` | Player list of a specific team | `%boatracing_team_players_sharks%` -> `Members: jaie55, KiluGod` | Same for every viewer |
| `%boatracing_team_player_count_<team>%` | Member count of a specific team | `%boatracing_team_player_count_sharks%` -> `Members: 2` | Same for every viewer |
| `%boatracing_team_wins_<team>%` | Wins of a specific team | `%boatracing_team_wins_sharks%` -> `Wins: 77` | Same for every viewer |

### Category: Player Lookup by Name or UUID
Use `<player>` with an exact player name or UUID. These are the placeholders for NPCs, statue labels, fixed holograms, and leaderboard walls.

| Placeholder(s) | What it shows | Example text on screen | Visibility |
|---|---|---|---|
| `%boatracing_player_name_<player>%` | Target player name | `%boatracing_player_name_jaie55%` -> `Driver: jaie55` | Everyone sees the target player's data |
| `%boatracing_player_wins_<player>%` | Target player wins | `%boatracing_player_wins_jaie55%` -> `Wins: 12` | `jaie55` and `KiluGod` both see Jaie55's wins |
| `%boatracing_player_best_race_<player>%` / `%boatracing_player_best_race_ms_<player>%` | Target player best race | `%boatracing_player_best_race_jaie55%` -> `Best race: 1:40.010` | Everyone sees the target player's data |
| `%boatracing_player_best_lap_<player>%` / `%boatracing_player_best_lap_ms_<player>%` | Target player best lap | `%boatracing_player_best_lap_jaie55%` -> `Best lap: 0:28.911` | Everyone sees the target player's data |
| `%boatracing_player_track_best_<player>%` / `%boatracing_player_track_best_ms_<player>%` | Target player best time on the current track | `%boatracing_player_track_best_jaie55%` -> `Track PB: 0:59.443` | Everyone sees the target player's data |
| `%boatracing_player_team_name_<player>%` / `%boatracing_player_team_id_<player>%` / `%boatracing_player_team_color_<player>%` | Team info of the target player | `Team: Sharks` / `Team ID: sharks` / `Color: AQUA` | Everyone sees the target player's data |
| `%boatracing_player_team_leader_name_<player>%` / `%boatracing_player_team_leader_id_<player>%` | Current leader of the target player's team | `%boatracing_player_team_leader_name_jaie55%` -> `Leader: jaie55` | Everyone sees the target player's data |
| `%boatracing_player_team_wins_<player>%` | Wins of the target player's team | `%boatracing_player_team_wins_jaie55%` -> `Team wins: 18` | Everyone sees the target player's data |
| `%boatracing_player_number_<player>%` / `%boatracing_player_boat_<player>%` | Racer number and selected boat of the target player | `Number: 7` / `Boat: OAK_BOAT` | Everyone sees the target player's data |


## Notes
- Teams can have multiple members and a current saved leader. Leader placeholders always reflect the current leader, not a historical creator value.
- If the current leader leaves the team, leadership falls back to the next stored member automatically.
- Leaving a team as the last member deletes the team automatically.
- User-facing text is message-bundle based and can be customized in `messages_<lang>.yml`, then reloaded with `/boatracing reload`.

## Build (Developers)
- Maven project; produces `BoatRacing.jar` shaded. Run `mvn -DskipTests clean package`.

## License
Distributed under the MIT License. See `LICENSE`.
