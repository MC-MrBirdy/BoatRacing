# Changelog

## 1.1.2 — 2026-03-29
### Added
- **PlaceholderAPI integration**: BoatRacing now registers `%boatracing_*%` placeholders for holograms/scoreboards.
- **Persistent aggregate stats**: new `stats.yml` storage for player wins, team wins, best race, and best lap values used by placeholders.
- **Race back command**: added `/boatracing race back` so players can manually return to their saved pre-lobby location after race flows.
- **Post-race lobby return UX**: after a race ends or is cancelled, participants are teleported to the configured race lobby, receive a clickable back shortcut, and have a 3-minute window to return.
- **French language bundle**: added bundled `messages_fr.yml` (community translation) with complete race/setup/team/admin/gui coverage.
- **Additional community language bundles**: added bundled `messages_es_419.yml`, `messages_pt_BR.yml`, `messages_pt_PT.yml`, `messages_de.yml`, `messages_it.yml`, `messages_pl.yml`, `messages_tr.yml`, `messages_ja.yml`, and `messages_ko.yml`, expanding built-in language coverage.

### Changed
- **Setup Wizard compact mode**: wizard prompts were shortened and reorganized by step to reduce chat text while keeping actionable buttons.
- **Registration announce source**: registration announce text is now language-specific in `messages_<lang>.yml` (`race.registration.announce`) instead of `config.yml`.
- **Registration announce placeholders**: `race.registration.announce` now consistently supports `{track}`, `{laps}`, `{cmd}`, `{label}` across bundled language files.
- **Lobby return flow**: pre-lobby locations are preserved during race start, and race-back return entries now expire automatically after 3 minutes (in-memory only).
- **Race help and tab-complete**: `race back` is now included in help output and tab suggestions for players with `boatracing.race.back`.
- **Language bundle loading**: language selection now supports bundled and custom `messages_<lang>.yml` files from the plugin folder, with safe fallback to English.
- **Translation status headers**: bundled language headers now consistently mark `messages_en.yml` and `messages_es.yml` as official translations, and all other bundled languages as unofficial community translations.
- **Chinese locale split**: kept `messages_zh_TW.yml` for Taiwan Traditional Chinese and added `messages_zh_CN.yml` for Mainland Simplified Chinese.
- **Race back window configurability**: the `/boatracing race back` availability window is now configurable with `racing.lobby.back-window-seconds`.

### Fixed
- **Build break in TeamManager**: fixed malformed package declaration in `TeamManager.java` that caused compilation failure.
- **Race boat entity cleanup**: race-spawned boats/rafts are now tracked and removed deterministically on finish/cancel/reset, preventing leftover vehicle entities in the world.
- **Race back expiry notification**: players now receive an automatic message when the back window expires, instead of only seeing it after manually running `/boatracing race back`.

### Docs
- **Placeholder reference added**: README now documents available `%boatracing_*%` placeholders and team lookup formats.
- **Lobby-back docs updated**: README and QA checklist now document the clickable `race back` flow and 3-minute return window as part of 1.1.2.

## 1.1.1 — 2026-03-13
### Added
- **Registration lobby mechanic (optional)**: new `racing.lobby.*` config block to send registered players to a lobby location while registration is open, with optional return to their previous location on leave/cancel.
- **Lobby setup command**: added `/boatracing setup setlobby` to save the admin's current position as the registration lobby and enable it instantly.
- **Lobby i18n messages**: added localized feedback when players are teleported to the race lobby and when they are returned.
- **SimpleScore integration hook**: when SimpleScore is present, BoatRacing now uses its viewer hide/show flow during races to avoid sidebar ownership conflicts and restore the external scoreboard cleanly after stop/cancel.

### Fixed
- **Race track selection flow for active track**: `race open/join/leave/force/start/stop/status` no longer force a disk reload when the requested track is already the active one (notably `unsaved`), preventing stale-state issues.
- **Track reload consistency**: `TrackConfig.load()` now clears all in-memory collections (`customStartSlots`, `bestTimes`, and others) before reading from disk.
- **Registration timer race-loop bug**: fixed a state bug where `race open` timer callbacks could survive manual `start/force/stop` flows and re-trigger race starts (infinite restart behavior). Registration sessions are now invalidated/cancelled atomically.
- **Manual start stale-callback guard**: `/boatracing race start <track>` now closes the registration window first to prevent old registration callbacks from restarting/overlapping race state.
- **External sidebar handoff reliability**: improved race HUD/sidebar cleanup so external sidebar plugins (notably SimpleScore and TAB) recover cleanly after race stop/cancel without requiring relog.

### Changed
- **Setup click actions UX**: setup wizard and admin setup tips now use suggest-in-chat behavior for commands requiring arguments, so players can tab-complete before executing.
- **Compatibility matrix clarified**: this plugin jar is intended for Bukkit-family servers (CraftBukkit/Spigot/Paper/Purpur) and now includes Folia-compatible scheduling paths.

### Docs
- **Platform scope documented**: README/CHECKLIST now explicitly state that Sponge requires a separate platform port and that Velocity/BungeeCord (proxy layer) cannot run gameplay logic from this plugin jar.
- **Explicit SimpleScore compatibility note**: docs now explicitly list compatibility with SimpleScore (GitHub: https://github.com/RuiPereiraDev/SimpleScore, Modrinth: https://modrinth.com/plugin/simplescore).
- **Explicit TAB compatibility note**: docs now explicitly list compatibility with TAB (GitHub: https://github.com/NEZNAMY/TAB, Modrinth: https://modrinth.com/plugin/tab-was-taken).

## 1.1.0 — 2026-03-12
### Added
- **Multi-language support**: messages system now supports English (default) and Español (España). Configure the language in config.yml via the `language` setting ("en" or "es"). All user-facing text is externalized in messages_en.yml or messages_es.yml (stored in the plugin data folder after first run).
- **New community translations**: added Traditional Chinese (`messages_zh_TW.yml`) and Russian (`messages_ru.yml`) language files. Both include a clear warning that they are unofficial translations and should be reviewed.
- **Player-controlled race management**: new config option `player-actions.allow-player-race-start` (default: false) lets non-admin players open, start, force-start and stop races globally. Per-track override available via `racing.allow-player-start` in tracks/<name>.yml (uses the track config override system to allow selective enablement per track).
- **Reward system**: fully customizable race completion rewards with support for finishing positions, configurable commands, player messages, and broadcast announcements. Positioned in config.yml under `racing.rewards` with enable/disable toggle, position-specific settings (1st/2nd/3rd/default), and placeholder support ({player}, {position}, {time}, {track}, {laps}).
- **Race performance optimization**: PlayerMoveEvent listener now throttles unnecessary checkpoint checks by comparing only when the player moves to a different block (not within the same block).
- **Complete i18n infrastructure**: MessageManager utility loads language files dynamically; all race, setup, team, and admin commands now use externalized messages. New messages can be added and will merge with defaults on reload without overwriting customizations.

### Changed
- **Permission flow refinement for race commands**: `race open/start/force/stop` now check track existence and load per-track settings before validating permissions, enabling per-track player-start override checks in a single canManageRace() helper.
- All hardcoded messages across the plugin (BoatRacingPlugin, RaceManager, AdminGUI, AdminRaceGUI, AdminTracksGUI, UpdateNotifier, SetupWizard, TeamGUI, TrackConfig) replaced with i18n `msg().get()` calls.
- Config option for team actions restructured: moved and renamed from various checks to `player-actions` section for consistency.

### Fixed
- **Setup Wizard i18n key mapping**: fixed wrong translation key paths in wizard navigation/summary prompts that could show raw keys (for example `setup.wizard.nav-label`) instead of localized text.
- **Scoreboard compatibility with external plugins**: race sidebar now preserves and restores each player's previous scoreboard instead of forcing the main scoreboard, preventing conflicts with plugins such as SimpleScore.

### Docs
- CHANGELOG and CHECKLIST updated for 1.1.0 with full feature list and verification steps.
- Version number incremented from 1.0.9 to 1.1.0.

## 1.0.9 — 2025-08-19
### Added / Changed
- Official support range declared: 1.19 → 1.21.11 (Bukkit/Spigot compatible; works on Paper/Purpur). Requires Java 17+; plugin.yml api-version set to 1.19.
- Documented supported servers: Purpur, Paper, Spigot, CraftBukkit (Bukkit-compatible forks may work; Folia/Sponge/Forge hybrids not supported).
- Classified as Bukkit/Spigot on Paper by excluding `paper-plugin.yml` from the jar and using only Bukkit-safe APIs for metadata.
- Documentation updated: README, CHANGELOG and QA checklist in EN/ES.
- Updater cadence: background check every 5 minutes; when a new version is first detected during runtime, print a console WARN immediately (once per version). Hourly console reminder aligned to each hour (00:00, 01:00, …) while outdated (config-gated). Admin join always shows an in‑game chat notice (if enabled) and never prints to console.

### Fixed
- Boat/Raft materials on mixed APIs: dynamic Material resolution for boat/raft variants (including Bamboo Raft and Pale Oak) removes NoSuchFieldError on older bases and avoids CraftLegacy warnings.
- Command metadata on Spigot: restored by replacing Paper-only `getPluginMeta()` with Bukkit `getDescription()`.

## 1.0.8 — 2025-08-16
### Added
- Config toggles to customize the sidebar and ActionBar visibility:
	- `racing.ui.scoreboard.show-position|show-lap|show-checkpoints|show-pitstops|show-name`
	- `racing.ui.actionbar.show-lap|show-checkpoints|show-pitstops|show-time`
- HUD pitstops: when `racing.mandatory-pitstops > 0`, show “PIT A/B” on the sidebar and ActionBar (config‑gated).
- Registration broadcast now includes the track name and the exact join command using `racing.registration-announce` template.
- Setup Wizard: new optional step to set “Mandatory pit stops” with quick buttons [0] [1] [2] [3].
- Setup command `/boatracing setup setpitstops <n>` to update and persist `racing.mandatory-pitstops`.
- Finish‑without‑checkpoints: added a clear player message when trying to finish without all required checkpoints for the lap (sound remains).
- Results broadcast now highlights the podium: 🥇/🥈/🥉 medals and rank colors for the top‑3.

### Changed
 - Race tab-complete now shows `join|leave|status` to all players; admin actions suggested only to admins.
- `race status` can be viewed by any player (keeps default permission true).
- Sidebar order switched to “L/CP - Name” and removed centering/padding.
- Names are shown as-is (keeps leading '.' for Bedrock players via Geyser/Velocity).
 - Results lines use safe name rendering (strip rank wrappers; preserve leading '.') and keep a penalty suffix when applicable.

### Fixed
- Minor cleanup and removal of unused variables in scoreboard rendering.
 - Prevented a potential permission recursion by defining `boatracing.admin` with explicit children instead of inheriting `boatracing.*`.
 - `/boatracing race leave <track>` now replies when registration is closed or when the player isn’t registered (no more silent no-op).
 - Setup Wizard (Pit): no longer repeats waiting for team pits when a default pit exists; the wizard now advances to Checkpoints automatically (team pits remain optional).
 - Updater: fixed missing console notice; now logs a WARN once on startup (if outdated) and also every hour while outdated. When an admin joins, a quick check runs (throttled) to notify them within seconds if a new update was just published.

## 1.0.7 — 2025-08-15
### Changed
- Update checks: removed periodic console spam; keep a single WARN shortly after startup when outdated (honors `updates.console-warn`). Periodic 5‑minute checks remain silent.
 - Scoreboard: redesigned layout with centered rows, compact labels, rank colors, and viewer highlight.
### Added
 
### Fixed
- Update checker logs network errors at most once per server run.
### Removed
- Internal hiding of vanilla scoreboard sidebar numbers has been removed entirely. If you want to hide the right‑side numbers, please use an external plugin while we work on a future built‑in implementation.
# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

## [1.0.6] - 2025-08-14
### Added
- Leaderboard sidebar: top‑10 live positions; personal stats moved to ActionBar.
- Configurable start “lights out” delay via `racing.lights-out-delay-seconds` to slow down the transition from all lit to GO.
- Optional “lights out” jitter via `racing.lights-out-jitter-seconds` (random 0..value seconds added to the delay).
- Sector and finish gaps: broadcasts a compact gap vs lap leader at each checkpoint and at lap finish; at race finish, gap vs winner.
 - Updates now point to Modrinth for downloads: https://modrinth.com/plugin/boatracing
 

### Fixed
- Pitstop as finish: crossing the configured pit area now counts as finish for lap progression once all checkpoints for the lap have been collected (pit time penalty still applies when enabled).
- Setup Wizard UX: clearer pit step text and a clickable “Clear checkpoints” action; checkpoints removal command advertised from the wizard.
 - Scoreboard layout polish: names left-aligned, the entire " - Lap X/Y [CP]" segment centered, compact/dynamic padding, and no extra padding after truncated names ("..."). Removed decorative separator and arrow prefix. Self entry highlighted in green (no bold). FIN label standardized to “FINISHED”.
 - Display name handling: prefer EssentialsX displayName when available and strip leading wrappers like [Rank]/(Rank)/{Rank} and punctuation for alignment.


## [1.0.5] - 2025-08-13
### Fixed
- Team member persistence: no members are lost after updates/reloads/startup; loader now restores members without enforcing capacity constraints.
- Setup pit command: `/boatracing setup setpit [team]` now supports team names with spaces by quoting them (e.g., "/boatracing setup setpit \"Toast Peace\""); tab‑completion suggests quoted names when the input starts with a quote.
- Config defaults: `config.yml` now merges new default keys on update/reload without overwriting user changes.
- Boat/raft type: placed vehicles now match the racer’s selected wood variant (including chest variants); no longer always spawns OAK. Compatible across API versions with safe fallbacks.


## [1.0.4] - 2025-08-13
### Added
- Team-specific pit areas via unified command `/boatracing setup setpit [team]` (tab‑completion for team names). Wizard updated accordingly.
- Mandatory pitstops via config `racing.mandatory-pitstops` (default 0). Pitstops increment on pit exit and are required to finish when > 0.
 - Config defaults: `config.yml` now merges new default keys on update/reload without overwriting user changes.
 - Boat/raft type: placed vehicles now match the racer’s selected wood variant (including chest variants); no longer always spawns OAK. Compatible across API versions with safe fallbacks.
 - Per-player custom start slots with `/boatracing setup setpos <player> <slot|auto>` and `/boatracing setup clearpos <player>`; tab completion for player names, `auto`, and slot numbers. Slots are 1-based in the command and stored 0-based.
 - Grid ordering by best recorded time per track (fastest first); racers without a time are placed after those with times.
 - Setup show now includes the presence of team-specific pits and the number of custom start positions configured.
 - Wizard (Starts): added optional clickable actions for custom start slots (setpos/clearpos/auto) and a counter of configured custom slots.

### Changed
- Permissions: players can use `join`, `leave`, and `status` by default; only `open|start|force|stop` remain admin‑only. Removed extra runtime permission checks for join/leave.

### Fixed
- Boats now spawn with the player’s selected wood type using a resilient enum mapping with safe fallback to OAK across API versions.

 

## [1.0.3]
### Added
- Admin Tracks GUI: create, select, save as, delete named tracks (with confirmation). Requires `boatracing.setup`.
- Admin Race GUI: manage race lifecycle (open/close registration, start/force/stop), quick-set/custom laps, and registrant removal.
- Active (selected) track name is displayed in Setup Wizard prompts, `/boatracing setup show`, and `/boatracing race status`.
- Tooltips (lore) for “Admin panel”, “Player view”, and “Refresh” buttons in GUIs.
- Quick navigation: from Teams GUI to Admin panel (admins only), and from Admin GUI back to player view.
- Refresh buttons in Teams and Admin GUIs.
 - Guided setup wizard with concise, colorized prompts and clickable actions. Adds a Laps step and an explicit Finish button; navigation buttons now use emojis (⟵, ℹ, ✖) and spacing puts a blank line at the top of the block.
 - Convenience selector: `/boatracing setup wand` to give the built-in selection tool.
- Team GUI: members can rename the team and change the team color when enabled via config (`player-actions.allow-team-rename` / `allow-team-color`). These actions notify all teammates.
 - Team GUI: optional member disband via config (`player-actions.allow-team-disband`). Disband uses a confirmation screen and notifies all teammates.
 - Per‑track storage: all track configuration is saved under `plugins/BoatRacing/tracks/<name>.yml`. On startup, a legacy `track.yml` is migrated to `tracks/default.yml` (or `default_N.yml`) with an in‑game admin notice.
 - Race commands now require a track argument: `open|join|leave|force|start|stop|status <track>`. Tab‑completion suggests existing track names for these.
 - Admin Tracks GUI: after creating a track, sends a clickable tip to paste `/boatracing setup wizard`.
 - Wizard labels Pit area and Checkpoints as “(optional)” and allows skipping them. Readiness requires only Starts and Finish.
 - Start lights: configure exactly 5 Redstone Lamps; race start uses an F1-style left-to-right countdown that lights lamps via block data (no redstone). New setup commands: `addlight` and `clearlights`. Wizard adds a dedicated “Start lights” step.
 - Registration: server-wide broadcast when a player joins or leaves registration.
 - False start penalties: moving forward during the start-light countdown applies a configurable time penalty (`racing.false-start-penalty-seconds`, default 3.0). Messages are in English.
 - New config flags: `racing.enable-pit-penalty` and `racing.enable-false-start-penalty` to toggle pit and false-start penalties.
 - Race permissions split: default-true for `boatracing.race.join`, `boatracing.race.leave`, and `boatracing.race.status`; admin actions require `boatracing.race.admin` (or `boatracing.setup`).
 - Live scoreboard: per-player sidebar showing Lap, Checkpoints, and Elapsed Time with periodic updates; created on race start and cleared on stop/reset/cancel.
 - Pitstop as finish: crossing the configured pit area now counts as finish for lap progression once all checkpoints for the lap have been collected (pit time penalty still applies when enabled).

### Changed
- Footer fillers switched from LIGHT_GRAY_STAINED_GLASS_PANE to GRAY_STAINED_GLASS_PANE for a darker look.
- Denial messages for protected actions are now hardcoded in English (no longer read from config).
- Configuration cleanup: removed obsolete `teams:` section and `messages.disallowed` from `config.yml`; documentation now reflects per‑track storage only.
- README updated to document `player-actions.*` flags and storage files.
 - Setup help and tab-completion updated to include `wizard` and `wand`.
 - Admin/user notifications for team deletion/removal now use neutral phrasing (no “by an admin”).
 - Tracks GUI: terminology switched to “selected” for the active track; "Create and select" loads the newly created track and suggests starting the setup wizard.
 - Disband button is hidden for members when `player-actions.allow-team-disband` is false.
- Race lifecycle: stop cancels registration and running race; start/force operate only on registered participants. Placement enforces unique starts, pitch=0, and auto‑mounts the selected boat.
- Terminology: “loaded” → “selected”; “pit lane” → “pit area”.
 - Race commands `open` and `start` no longer accept a laps argument; laps come from configuration/track.
 - Pit area and checkpoints made optional across setup, readiness checks, and runtime logic/documentation.
 - Tracks GUI: removed “Save as…”.
 - Setup help and tab-completion now include `addlight` and `clearlights`. `/boatracing race status` and `setup show` display the number of start lights.

### Fixed
- Removed the last references to `messages.disallowed.*` in `TeamGUI` that could cause confusion.
 - Selection handling improved: built-in selection tool (left/right click); `/boatracing setup selinfo` shows richer diagnostics.
- Admin GUI: clicking any dye item in the team view now opens the color picker (previously only LIME_DYE was handled).
 - Crash on boat spawn fixed by spawning BOAT/CHEST_BOAT directly and mounting players; removed unsupported boat wood-type setter.
 - Checkpoints persistence: saving as a list and loading from both list and legacy section formats; wizard and readiness checks now correctly detect added checkpoints. Fixed a false “missing checkpoint” on `race open` after setup.
 - Players wrongly blocked from `/boatracing race join` due to a global permission gate. Now join/leave/status are allowed by default and only admin actions are gated.

## [1.0.3] - 2025-08-12
- Public release noted in README. Core gameplay, teams, GUIs, WE/FAWE setup, racing, and update checks.

 [Unreleased]: https://github.com/Jaie55/BoatRacing/compare/v1.0.6...HEAD

[1.0.6]: https://github.com/Jaie55/BoatRacing/releases/tag/v1.0.6

[1.0.5]: https://github.com/Jaie55/BoatRacing/releases/tag/v1.0.5

[1.0.4]: https://github.com/Jaie55/BoatRacing/releases/tag/v1.0.4

[1.0.3]: https://github.com/Jaie55/BoatRacing/releases/tag/v1.0.3

