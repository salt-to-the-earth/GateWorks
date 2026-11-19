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
- A temporary **Selection Book** is automatically issued when the administrator first uses the wand. It displays the current selection size and offers clickable actions.
- The Selection Book can start creation of a named trigger or display clickable choices for existing triggers.
- Creating or attaching a selection issues that trigger's permanent **Trigger Book** and removes the temporary Selection Book.
- Trigger names and unique IDs are saved in the plugin configuration. Selected block coordinates and selection groups are also recorded with the trigger.
- The Trigger Book can start button association. The administrator then uses the GateWorks wand to click the physical Minecraft button.
- Button world and coordinates are saved with the trigger.
- `/gateworks selection` shows selection size and group count; `/gateworks clear` clears the current temporary selection.
- A 4,096-block limit on a single cuboid gesture.
- GitHub Actions workflow to compile the plugin and run tests.

**Current limitations:** This is the framework and user-interface stage, not the automation stage. Buttons are recorded against triggers but do not activate actions yet. Trigger region coordinates and button association are persisted, while the administrator's in-progress selection workspace is temporary and is lost on server restart. The book pages are refreshed when the administrator changes the selection or uses the refresh action. Status monitoring, action execution, HubControl integration, cooldowns, anti-spam protection, and diagnostics remain future stages.

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
