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
- Button world and coordinates are saved with the trigger.
- `/gateworks selection` shows selection size and group count; `/gateworks clear` clears the current temporary selection.
- A 4,096-block limit on a single cuboid gesture.
- GitHub Actions workflow to compile the plugin and run tests.

**Current limitations:** This is the framework and user-interface stage, not the automation stage. Buttons are recorded against triggers but do not activate actions yet. Trigger region coordinates and button association are persisted, while the administrator's in-progress selection workspace is temporary and is lost on server restart. The book pages are refreshed when the administrator changes the selection or uses the refresh action. The visualizer draws individual block perimeters and may cap particle output for performance on very large selections. Status monitoring, action execution, HubControl integration, cooldowns, anti-spam protection, and diagnostics remain future stages.

## Project goals

- Configure mechanisms through an in-game written-book interface rather than inventory-menu GUIs.
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
6. **Book interface** — one configuration book per mechanism, plus a temporary selection workspace.
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

Copy `build/libs/GateWorks-0.1.1.jar` into the Paper server's `plugins` directory and restart the server. This is an early development build; use a test server, not a production server.

## Roadmap and contributing

See [ROADMAP.md](ROADMAP.md) for milestones and [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance.

## License

MIT. See [LICENSE](LICENSE).
