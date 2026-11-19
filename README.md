# GateWorks

**A configurable automation and control framework for Minecraft servers.**

GateWorks is an open-source, server-side Paper plugin for building mechanisms that respond to player interactions, monitor external conditions, manage logical states, and perform configurable actions.

Players use an ordinary, unmodified Minecraft Java client. No client-side mod is required.

> **Development status:** Early development. The first release is a foundation, not a feature-complete automation system. Features described in the roadmap are planned and must not be assumed to be implemented yet.

## Project goals

- Configure mechanisms through an in-game written-book interface rather than clunky inventory menus.
- Use a selection wand to identify individual blocks and cuboid regions.
- Separate physical **triggers**, external **status monitors**, and configurable **actions**.
- Keep requested state separate from confirmed state. For example, requesting a server start does not mark a portal ON; a status monitor must confirm that the server is running.
- Provide configurable cooldowns and anti-spam protection.
- Include diagnostics and an activity history in every mechanism's configuration.
- Keep configuration data on the server; books are interfaces, not the authoritative data store.
- Integrate with other plugins through documented adapters, beginning with the HubControl environment.

## Planned architecture

1. **Trigger engine** — buttons, levers, pressure plates, and other supported interactions.
2. **State engine** — OFF, ON, and transitional/error states, with explicit confirmation rules.
3. **Status providers** — scoreboard, plugin API, and other carefully scoped status sources.
4. **Action engine** — commands and block changes, with request actions kept separate from state-transition actions.
5. **Selection and regions** — individual block selections and cuboid groups, with per-player visual feedback.
6. **Book interface** — one configuration book per mechanism, plus a temporary selection workspace.
7. **Protection** — cooldowns, anti-spam lockouts, safe control restoration, optional configured consequences, and administrator bypass.
8. **Diagnostics** — validation, source connectivity, last confirmed status, recent events, and actionable error details.

## Compatibility

- Target platform: Paper.
- Target API line: Paper / Minecraft 26.3.
- Target Java: Java 25.
- Client requirement: unmodified Minecraft Java client.

The project deliberately avoids Paper internals (NMS) unless a documented API limitation makes an isolated compatibility layer necessary.

## Building

Install JDK 25 and Gradle (or use the included Gradle Wrapper once it is added).

```bash
./gradlew build
```

The plugin JAR will be in `build/libs/`.

## Installation

Copy the built `GateWorks-<version>.jar` into the Paper server's `plugins` directory, then restart the server. Consult the release notes before using a development build on a production server.

## Development principles

- Never report an external state as confirmed merely because a command was submitted.
- Never let a listener's state update accidentally execute a player's ON/OFF request command.
- Never overwrite a block during automatic restoration if another player or plugin has changed that location.
- Never trust a copied or edited book as authorization; validate all configuration identities and permissions server-side.
- Keep potentially destructive features opt-in and visible in diagnostics.

## Roadmap

See [ROADMAP.md](ROADMAP.md) for planned milestones and [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance.

## License

MIT. See [LICENSE](LICENSE).
