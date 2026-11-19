# GateWorks

**A configurable automation and control framework for Minecraft servers.**

GateWorks is an open-source, server-side Paper plugin for building mechanisms that respond to player interactions, monitor external conditions, manage logical states, and perform configurable actions.

Players use an ordinary, unmodified Minecraft Java client. No client-side mod is required.

> **Current status: 0.1.1 — early development.** This milestone provides a selection-wand prototype with persistent visual selection outlines. It is not yet the complete automation system described below.

## What works in 0.1.1

- Paper plugin bootstrap and `/gateworks` command (`/gw` alias).
- A uniquely tagged selection wand, obtained with `/gateworks wand`.
- Left-click to add an individual block.
- Shift + left-click to add a cuboid between the previous selection point and the clicked block.
- Right-click to remove one selected block.
- Shift + right-click to remove the entire selection group containing that block.
- **Automatic visual highlighting:** selected groups receive a particle outline, refreshed every half-second.
- Highlighting is enabled by default in this test build and is visible only to the selecting administrator.
- Individual selected blocks are outlined as small cubes; cuboid groups are outlined by their outer bounds.
- `/gateworks selection` to show selection size and group count.
- `/gateworks clear` to clear the current temporary selection and its outline.
- A 4,096-block limit on a single cuboid gesture.
- Unit tests for selection grouping and removal.
- GitHub Actions workflow to compile the plugin and run tests.

**Visual feedback details:** The outline uses client-visible particles and does not place, replace, or modify any world blocks. The current prototype outlines the bounding box of each selection group. If blocks are removed from the middle of a group, its outline still follows the group's remaining outer bounds. A per-player particle budget limits rendering work for large or numerous selections.

**Important limitations:** Selection data is currently in memory only and is lost when the server restarts. The book interface, trigger engine, status monitoring, action engine, HubControl integration, persistence, cooldowns, anti-spam protection, and diagnostics are not implemented yet.

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
