# TBLD Forge

This repository is a fork of [Forge](https://github.com/Card-Forge/forge), the open-source Magic: The Gathering rules engine. It is being adapted to run TumbledMTG (TBLD), a custom constructed format with thousands of original, fan-made and modified cards and its own mechanics, and to let players play TBLD online in a browser with full rules enforcement and a chess clock.

## Working with Brett

- Be blunt and brief. State what changed and what he should do; leave out the reasoning unless he asks.
- When one clarifying question decides the answer, ask only that question.
- Test before handing anything over. Every feature must pass an automated test that exercises it (see "Tests") before Brett is asked to try it.
- Explain build and tooling steps plainly; Brett is not experienced with Java tooling.

## Decisions

- Architecture: our own server runs headless Forge; players use a browser client. A modified Forge desktop client remains a fallback.
- XMage and contacting the Endstep developer are backup plans only. Do not reconsider them unless Brett raises them.
- TBLD card script files are named with a `tbld_` prefix (e.g. `forge-gui/res/cardsfolder/tbld/tbld_searing_touch.txt`), matching the "TBLD " prefix in Brett's Cockatrice/MSE files. The displayed card name (`Name:` in the script) has no prefix.
- Official cards are not needed in the final pool, except as reference scripts for writing TBLD cards. A TBLD card that shares a name with an official card replaces it (the official script is deleted).
- Cards are scripted in batches, from the Cockatrice XML entries that Brett supplies, as he reviews the card pool. His ability templating differs from Oracle wording; the `Oracle:` line uses his text.
- Engine changes for TBLD rules are made directly in the Forge code.

## TBLD rules implemented

| Rule or mechanic | Implementation | Test |
|---|---|---|
| Flash is a supertype (replaces the flash keyword and the instant type) | `CardType.Supertype.Flash`; `Card.hasFlashSupertype()`; checked in `SpellAbility.withFlash` and `SpellAbilityProperty` ("CouldCastTiming") | `TbldFlashSupertypeTest` |
| London mulligan bottoms cards after the hand is kept (CR 103.5) | `LondonMulligan.keep()`; the keep prompt states the number of cards; `InputLondonMulligan` has no Auto button and ignores OK until enough cards are chosen | `TbldWebServerTest` |

Not started: Scan, Journey, Upload, Spirited, Experience, luck counters, modified Clues, modified voting, turn-order-dependent starting emblems, no planeswalkers, the TBLD life total and deck rules.

## Web play

- `forge-gui-desktop/src/main/java/forge/tbld/server/TbldWebServer.java`: Netty HTTP and WebSocket server. Serves the client at `/` and one WebSocket per seat at `/ws?seat=N`. Starts a two-player match with a fixed mono-red test deck (`testDeck()`) once both seats connect. `{t:"restart"}` from either browser abandons the match and starts a new one.
- `BrowserSeat.java`: one seat in one match. It implements Forge's `IRemote` under a `ProtocolGuiGame`, so every engine call on the player's GUI (`ProtocolMethod`) arrives here.
  - Before each call it sends `{t:"state", state}`: a full snapshot filtered to what that player may see.
  - Then it sends `{t:"call", m, a, rid?}`. Calls with a `rid` block the engine until the browser answers `{t:"reply", rid, v}`; `v` is an index or an array of indices into the offered options.
  - Clicks arrive as `{t:"act", m: "ok"|"cancel"|"card"|"player"|"stack"|"mana"|"undo"|"concede", id}` and run on the seat's own thread. They must not run on the Netty thread: an action can make the engine ask the same player a question, and the answer arrives on the same connection.
  - Answered on the server without asking the player: combat damage assignment (all to the first blocker), dividing damage or counters, sideboarding, `manipulateCardList`. `getAbilityToPlay` with one playable ability uses it without asking. Ordering prompts keep the default order.
  - `-Dtbld.debug=true` logs every prompt, button change and selection to standard output.
- `forge-gui-desktop/src/main/resources/tbld/web/index.html`: the client. One self-contained file of plain HTML and JavaScript. It renders the snapshot as text, the prompt and buttons, dialogs for questions, a "Chosen" panel from the engine's `Selection`, and outlines playable cards (`actionable`). Elements carry `data-*` attributes that the browser test relies on.
- `forge-gui-desktop/src/main/java/forge/view/Main.java`: the command-line mode `tbld [port]` runs the server (default port 8080). `forge-gui-desktop/src/main/config/tbld-server.cmd` is the Windows launcher in the packaged build.

Next steps, in order: a better UI (layout, card images, click-to-target); the chess clock (on the server: a player's time runs while the engine waits for that player's decision); lobby and deck import, merged with the TBLD site's card browsing and deck sharing; hosting.

Known issue: after an action, the previous buttons stay visible for about 250 ms (Forge's `awaitNextInput` delay), so a quick second click can pass the next priority.

## Building and running

Requires JDK 17 or newer and Maven.

- Build the desktop package: `mvn -B -P windows-linux install -DskipTests -T 1C`
- The runnable package is `forge-installer/target/forge-installer-<version>/`. Run the server from that folder: `java -jar forge-gui-desktop-<version>-jar-with-dependencies.jar tbld 8080` (or `tbld-server.cmd`), then open `http://localhost:8080/` in two tabs and choose a seat in each.
- Restart the server after each rebuild.

## Tests

- TBLD tests (`forge-gui-desktop/src/test/java/forge/tbld/`):
  `mvn -B -P windows-linux install -T 1C -Dtest='forge.tbld.*Test' -Dsurefire.failIfNoSpecifiedTests=false`
  - `TbldFlashSupertypeTest`: Flash supertype timing; Searing Touch.
  - `TbldWebServerTest`: serves the page; seat 2 mulligans once; restart.
  - `TbldFullGameTest`: two `TbldBot`s play over the WebSocket protocol until a player loses, and the test checks for lands, creatures, a resolved spell, an attack, a block and a creature dying.
  - `TbldBot` acts on the latest prompt, buttons, selection and state after each burst of messages, and never clicks a card that is already chosen (that would unchoose it).
- Browser test (`tbld-e2e/play.mjs`): with the packaged server running on port 8090, `cd tbld-e2e && npm install && npx playwright install chromium && node play.mjs http://localhost:8090/`. Two Chromium tabs play a full game by reading and clicking the page; screenshots go to `tbld-e2e/out/`. It acts only after the page has been unchanged for 400 ms.
- CI: `.github/workflows/tbld-build.yml` runs all of the above on every push to `master` and uploads the package (`tbld-forge-desktop`) and the screenshots (`tbld-browser-test`). Upstream's full test suite (`test-build.yaml`) runs only when started manually; it has not been run against TBLD changes.
- Brett wants the full-game tests moved behind a manual switch once the web features are known to work.

## Repository conventions

- Checkstyle fails the build on unused or redundant imports.
- Card script reference: the Forge wiki pages "Card scripting API", "Creating a custom Card" and "Creating a custom Set". The `TumbledMTG` edition file is `forge-gui/res/editions/TumbledMTG.txt` (code `TBLD`, type `Custom_Set`).
- Forge is GPL-3.0. Distributing a modified desktop client requires publishing this fork's source; a server-only deployment does not.
