# GateWorks roadmap

This roadmap distinguishes implemented behavior from planned work. Milestone scope may change as features are tested.

## 0.1.0 — project and selection prototype

- [x] Establish the public project and document the design goals.
- [x] Create a Paper plugin project targeting the 26.3 API line and Java 25.
- [x] Add the first selection-wand interaction prototype.
- [x] Support individual block selection.
- [x] Support shift-left-click cuboid selection, with a size limit.
- [x] Support removing one selected block or its entire selection group.
- [x] Add selection unit tests and a GitHub Actions build workflow.
- [ ] Verify compilation and tests on GitHub Actions.
- [ ] Test actual wand interactions on the target Paper server.

## 0.2.0 — selection workflow and book interface

- Add a temporary selection workspace and server-authoritative session identity.
- Add per-player selection highlighting without changing real world blocks.
- Prototype written-book navigation and clickable choices on an unmodified client.
- Add attach-to-trigger workflow and recoverable drafts.
- Define behavior for disconnects, lost books, copied books, and concurrent edits.

## 0.3.0 — trigger and action engine

- [x] Model physical trigger devices separately from region selections and command actions.
- [x] Add single-command and ON/OFF command-toggle configuration through the Trigger Book.
- [x] Execute configured commands as the server console.
- [x] Initialize state-based toggle alignment from the physical device without executing an action.
- [x] Add initial per-trigger cooldown and repeated-activation lockout controls.
- [x] Add two-material region toggling and combined command-plus-region execution.
- [x] Add Primary and Secondary material configuration to the Trigger Book.
- [x] Separate region preview from region editing controls.
- [ ] Expand the curated solid-block material catalog.
- [ ] Add structure-template swapping as a future action type (backend architecture only for now).
- [ ] Add listener coordination, delays, and requested/observed/applied state transitions.
- [ ] Add safe transitional-state handling and confirmed action outcomes.
- [ ] Expand protection responses to safe control disable/restore and optional player consequences.

## 0.4.0 — status providers

- Define a provider interface for external status sources.
- Add scoreboard monitoring.
- Add a HubControl integration adapter after confirming its API contract.
- Poll asynchronously where needed and apply world changes on the server thread.
- Distinguish unknown, unavailable, transitional, and confirmed states.

## 0.5.0 — protection and diagnostics

- Add configurable cooldowns and activation-window anti-spam rules.
- Add safe temporary control removal and restoration.
- Add optional, explicitly configured player consequences.
- Add per-mechanism diagnostics and bounded recent-activity history.
- Record action origin, timestamp, outcome, and useful failure details.

## 1.0.0 — production readiness

- Complete permission and security review.
- Test persistence, recovery, upgrades, and failure handling.
- Test supported Paper versions and document compatibility.
- Publish installation, administrator, and contributor documentation.
- Publish a release JAR and checksums.
