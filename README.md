# GateWorks

**A configurable automation and control framework for Minecraft servers.**

GateWorks is an open-source, server-side Paper plugin for building mechanisms that respond to player interactions, monitor external conditions, manage logical states, and perform configurable actions.

Players use an ordinary, unmodified Minecraft Java client. No client-side mod is required.

> **Current status: early development.** The current milestone establishes the selection wand, written-book workflow, named trigger records, and physical button association. It is not yet the complete automation system described below.

## What works in the current development build

- Paper plugin bootstrap and `/gateworks` command (`/gw` alias).
- A uniquely tagged selection wand, obtained with `/gateworks wand`.
- Left-click to add an individual block.
- Shift + left-click to add a cuboid between the previous selection point and the clicked block.
- Right-click to remove one selected block.
- Shift + right-click to remove the entire selection group containing that block.
- Per-player particle highlighting, refreshed every half-second without modifying world blocks.
- A **Temp Selection** book is automatically issued when the administrator first uses the wand. It separates selection information from the page used to attach a selection to a trigger.
- Selecting a supported trigger device (button, lever, pressure plate, or tripwire hook) registers a trigger with the temporary name **Temp Trigger** and issues its separate book. Clicking the trigger name opens chat naming. The trigger book separates its overview from its region-selection page.
- Creating or attaching a selection issues that trigger's permanent **Trigger Book** and removes the temporary Selection Book.
- Trigger names and unique IDs are saved in the plugin configuration. Selected block coordinates and selection groups are also recorded with the trigger.
- The Trigger Book can reissue itself when its physical trigger device is selected again with the wand. Its region page starts a fresh region-editing session. Attaching a selection saves its block coordinates and groups, clears the active selection, and removes the Temp Selection book.
- Button, lever, pressure-plate, and tripwire-hook device coordinates are saved with each trigger.
- The Trigger Book includes a command-action page: buttons default to one command, while state-based devices default to an ON/OFF command toggle. Administrators can change the mode and edit commands from the book.
- Commands execute as the server console. Pulse devices act on their rising edge; state-based devices use their physical ON/OFF transitions when configured for toggle mode. Existing state is synchronized at startup without executing a command.
- Every Temp Trigger Book includes spam-protection controls. The initial protection implementation provides a configurable per-trigger cooldown, per-player repeated-attempt threshold/window, temporary activation lockout, and optional player notification.
- Triggers with a saved region include Action 2: administrators choose Primary and Secondary block types from a Creative-style, read-only inventory selector opened by clicking the material names in the Trigger Book. Categories include Building, Natural, Wood, Redstone, Functional, Decorative, and Special blocks. The selector includes Air, Barrier, Light (maximum level 15), Nether Portal, End Portal, End Gateway, End Portal Frame, and other technical blocks. The Secondary material defaults to Air; the Primary defaults to the first selected block's current material. Each activation replaces every saved region position with the Secondary type; the next activation restores the Primary type. Command Action 1 continues to run alongside the region change.
- The Trigger Book remains the main configuration interface. The temporary inventory selector is only for choosing a block, shows player-readable names and the selected material, and prevents players from taking or moving its icons. The region preview and region-editing controls occupy separate Trigger Book pages.
- `/gateworks selection` shows selection size and group count; `/gateworks clear` clears the current temporary selection.
- A 4,096-block limit on a single cuboid gesture.
- A configurable command status listener on each Trigger Book. It polls every 5 seconds by default (adjustable from 1 to 3,600 seconds), compares the captured command response against State A and State B, and updates the logical state and region only after a recognized response. The listener never runs Action 1 commands. Failed or unrecognized checks mark the listener OFFLINE, retain the last confirmed state and region, and save a simple error message for on-demand display.
- GitHub Actions workflow to compile the plugin and run tests.

**Current limitations:** This remains an early development build. Every selected position is replaced uniformly, so original mixed materials and block-specific data (such as container contents, sign text, or orientation) are not preserved. Portal block materials are placed as blocks only; GateWorks does not construct or activate portals, which remains the responsibility of the server's portal plugin. The trigger device itself is rejected if included in its own region. Structure-template swapping is intentionally deferred and is not exposed in the user interface. The command listener captures messages sent back to its command sender; commands that do not report a response to that sender will appear OFFLINE with a generic error. Some third-party commands may require a dedicated integration if they only report results through another channel. HubControl integration, confirmed action outcomes, temporary control removal/restoration, player-harm responses, and expanded diagnostics remain future stages. Redstone-event player attribution for notifications is best-effort because Bukkit's redstone event does not identify the activating player. Trigger region coordinates are persisted, while the administrator's in-progress selection workspace is temporary and is lost when the server restarts. The visualizer draws individual block perimeters and may cap particle output for performance on very large selections.

## Project goals

- Configure mechanisms through an in-game written-book interface, using temporary inventory selectors only when a player needs to choose a material or other resource.
- Use a selection wand to identify individual blocks and cuboid regions.
- Separate physical **triggers**, external **status monitors**, and configurable **actions**.
- Keep requested state separate from confirmed state. Requesting a server start must not mark a portal ON; a status monitor must confirm that the server is running.
- Keep trigger request actions separate from state-transition actions.
- Provide configurable cooldowns and anti-spam protection.
- Include diagnostics and activity history in every mechanism's configuration.
- Keep configuration data on the server; books are interfaces, not the authoritative data store.
- Integrate with other plugins through documented adapters, beginning with HubControl.

## Planned architecture

1. **Trigger engine** — buttons, levers, pressure plates, and other supported interactions.
2. **State engine** — OFF, ON, transitional, unknown, and error states, with explicit confirmation rules.
3. **Status providers** — scoreboard, plugin API, and other carefully scoped status sources.
4. **Action engine** — commands and block changes, with request actions kept separate from state-transition actions.
5. **Selection and regions** — individual block selections and cuboid groups, with per-player visual feedback.
6. **Book and selection interfaces** — one configuration book per mechanism, plus focused temporary inventory selectors for choosing materials and future resources.
7. **Protection** — cooldowns, anti-spam lockouts, safe control restoration, optional configured consequences, and administrator bypass.
8. **Diagnostics** — validation, source connectivity, last confirmed status, recent events, and actionable error details.

## Compatibility target

- Server platform: Paper.
- API line: Paper / Minecraft 26.3.
- Java: 25.
- Client: ordinary, unmodified Minecraft Java client.

The project avoids Paper internals (NMS) unless a documented API limitation makes an isolated compatibility layer necessary.

## Building

Install JDK 25 and Gradle 9.2.1 or a compatible Gradle 9.x release.

```bash
gradle clean test build
```

The plugin JAR will be created in `build/libs/`.

## Installation

Copy `build/libs/GateWorks-0.1.0.jar` into the Paper server's `plugins` directory and restart the server. This is an early development build; use a test server, not a production server.

## Roadmap and contributing

See [ROADMAP.md](ROADMAP.md) for milestones and [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance.

## License

MIT. See [LICENSE](LICENSE).
