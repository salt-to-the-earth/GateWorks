# Contributing to GateWorks

Thanks for your interest in GateWorks. Contributions, bug reports, documentation improvements, and test cases are welcome.

## Before opening a pull request

1. Open an issue for substantial changes so the design can be discussed first.
2. Keep changes focused and explain the behavior they add or alter.
3. Add or update tests for logic that can be tested without a running Minecraft server.
4. For Paper event behavior, include the Paper version and steps used to reproduce or verify it.
5. Do not commit server worlds, live plugin data, credentials, API tokens, or private configuration.

## Development environment

- JDK 25
- Gradle 9.2.1 or compatible Gradle 9.x
- Paper API 26.3

Run:

```bash
gradle clean test build
```

## Design rules

- Keep trigger request actions separate from listener-driven state-transition actions.
- A requested state is not a confirmed state.
- Prefer stable Paper APIs over server internals.
- Do not overwrite a block during restoration if the location has changed since removal.
- Treat books as user interfaces, never as the authoritative source of permissions or configuration.
- Keep potentially harmful consequences disabled by default and explicitly configurable.
- Document limitations honestly; a feature is not complete merely because it compiles.

## Pull requests

Describe the problem, the change, how it was tested, and any compatibility implications. Do not claim runtime verification unless the change was tested on a running server.
