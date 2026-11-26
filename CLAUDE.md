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
  - Before each call it sends `{t:"state", state}`: a full snapshot filtered to what that player may see. Each visible card carries its face (name, cost, type, text, power/toughness, colors, set, and `img`, a picture URL when there is one), and cards named in calls carry the same face when the player may see them, so popups can show them. The snapshot also has `phaseId` (the `PhaseType` name) and, for each stack item, `ability`, `trigger`, `controller` and `targets`.
  - Then it sends `{t:"call", m, a, rid?}`. Calls with a `rid` block the engine until the browser answers `{t:"reply", rid, v}`; `v` is an index or an array of indices into the offered options.
  - Clicks arrive as `{t:"act", m: "ok"|"cancel"|"card"|"player"|"stack"|"mana"|"undo"|"concede", id}` and run on the seat's own thread. They must not run on the Netty thread: an action can make the engine ask the same player a question, and the answer arrives on the same connection.
  - Answered on the server without asking the player: combat damage assignment (all to the first blocker), dividing damage or counters, sideboarding, `manipulateCardList`. `getAbilityToPlay` with one playable ability uses it without asking.
  - `order` (Forge's dual list, used for scry, surveil, "choose N cards" and ordering) is answered with indices into the source list followed by the destination list, in the chosen order.
  - The server sets `FPref.UI_SELECT_FROM_CARD_DISPLAYS` off, so cards are picked by clicking the board only on the battlefield and in the player's own hand. Choices from other zones (a library, an opponent's revealed hand, scry) come as dialogs, which the client shows as popups of cards.
  - In the snapshot, a blocker's `blocking` names the attacker it blocks, and an attacker's `blockedBy` lists its blockers. Until blockers are declared, Forge's `CombatView` holds the chosen blockers only as planned blockers (`getPlannedBlockers`), and `isBlocking` is false; the seat shows planned blockers to their controller only.
  - `{t:"pref", autoPass}` turns on Forge's "auto-pass if no actions" (`FPref.YIELD_AUTO_PASS_NO_ACTIONS`) for that seat only, as a `YieldUpdate.SetYieldPref` override on its controller. The client's checkbox is on by default and remembered per seat in `localStorage`; it is resent on connect and on restart. Seats that never send it (the test bots) have it off.
  - `{t:"pref", stops: {own: [...], opponent: [...]}}` names the phases (`PhaseType` names) in which the player stops for priority on their own turn and on an opponent's turn. For every other phase in `BrowserSeat.STOP_PHASES` the seat sends `YieldUpdate.SkipPhase`, so Forge passes priority there while the stack is empty; with something on the stack the player still gets priority. Seats that never send stops (the test bots) stop everywhere.
  - The log in the snapshot is the last 30 entries, oldest first; the client stays scrolled to the newest.
  - `-Dtbld.debug=true` logs every prompt, button change and selection to standard output.
- Declaring blockers (`InputBlock`, changed for TBLD in all clients, as Brett asked): select one of your creatures, then the attacker it blocks; after that nothing is selected. Selecting an attacker with no creature chosen does nothing. Selecting a creature that is blocking takes its block back and does not select it. Selecting the chosen creature again cancels the choice.
- `forge-gui-desktop/src/main/resources/tbld/web/index.html`: the client. One self-contained file of plain HTML and JavaScript, with its own visual design (Endstep was a feature reference only).
  - Board: the opponent's half on top, this player's at the bottom with their hand. Each battlefield has two rows, both centered: creatures in the front row (nearest the middle); in the back row lands, then other permanents after a gap. Two rows rather than three, to keep cards large; the back row has room because identical lands are stacked (same name and state) with a count. Auras and equipment are tucked behind what they are attached to. Rows overlap their cards when crowded.
  - Cards are pictures, turned sideways when tapped. TBLD cards load from this server (`TbldPictures`: `res/tbld/pics/`, served at `/pics/<file>`); official cards from Scryfall by name (`BrowserSeat.imageUrl`). Cards without a picture, tokens, and cards whose picture fails to load are drawn as a card frame.
  - TBLD card pictures are named as in Brett's card files: "TBLD Searing Touch.png". A card with several versions has one file per version, numbered by art index: "TBLD Mountain69.png"; a file without a number serves every version. The server remembers which files exist, so restart it after adding pictures.
  - Basic lands: TBLD has 80 versions of each basic land. The `TumbledMTG` edition lists Mountain 80 times with collector numbers M1 to M80, so art index N is version N (`Mountain|TBLD|69` in a deck). In game every version is just "Mountain", and versions stack together on the battlefield; the version number is only for places where it matters, such as a deck builder. Only Mountain is listed so far, and only version 69 has a picture. The test deck's Mountains are version 69.
  - Summoning-sick creatures are dimmed with a "zZ" badge; attackers move toward the middle with a red tag, blockers have a blue one; power/toughness and damage are badges.
  - The space bar confirms the open popup (a hidden popup is shown first) or presses the decision panel's main button (OK, pass priority, Keep). After a button is pressed, the buttons are disabled until the engine sends new ones (or for one second if it sends none), so a quick second press can't pass the next priority.
  - The active player's half is highlighted. The phase bar between the halves marks the current phase and has two stop toggles per phase: the upper one for the opponent's turn, the lower one for this player's. Defaults: own turn Main 1, Blockers, Main 2; opponent's turn Attackers, Blockers, End step. Stops are remembered per seat in `localStorage` and sent with the auto-pass setting.
  - The side panel: the decision panel (lit up when this player must act, with the prompt, the card it is about, chosen items and buttons), the stack (spells as cards; abilities as dashed purple tiles with a small source card), the log, and settings. The stack area has a set size and scrolls. The ⇅ toggle between stack and log (as Brett asked) switches between a stack that shows one and a half spells with the log taking the rest (the default) and a log of five lines with the stack taking the rest; the choice is remembered in `localStorage`.
  - Hovering any card shows a large preview at once, with its state (damage, counters, combat).
  - Every question from the engine is a popup over the board: cards are shown as cards and picked by clicking; revealed cards that can't be chosen are dimmed; the dual list (scry and the like) has two piles and arrows for order. "Hide" collapses a popup to look at the board. Clicking a graveyard or exile pile shows its cards.
  - Elements carry `data-*` attributes and classes that the browser tests rely on (`data-zone`, `data-row`, `data-id`, `.selectable`, `.chosen`, `.tapped`, `.tags`, `#dialog[data-need]`, `.opt`, `.picked`, `.primary`).
- `forge-gui-desktop/src/main/java/forge/view/Main.java`: the command-line mode `tbld [port]` runs the server (default port 8080). `forge-gui-desktop/src/main/config/tbld-server.cmd` is the Windows launcher in the packaged build.

Next steps, in order: the chess clock (on the server: a player's time runs while the engine waits for that player's decision); lobby and deck import, merged with the TBLD site's card browsing and deck sharing; hosting.

Known issue: after an action, the previous buttons stay visible for about 250 ms (Forge's `awaitNextInput` delay); the client disables them once pressed, so this no longer passes priority twice.

## Building and running

Requires JDK 17 or newer and Maven. On Brett's PC, Maven is at `C:\Users\Tumbles\tools\apache-maven-3.9.16\bin\mvn.cmd` (not on PATH).

- To build and test one module without the installer, add `-pl forge-gui-desktop -am` (without `-am`, Maven cannot resolve the `${revision}` parent version).
- The packaged server locks its jar on Windows: stop it before rebuilding the package.

- Build the desktop package: `mvn -B -P windows-linux install -DskipTests -T 1C`
- The runnable package is `forge-installer/target/forge-installer-<version>/`. Run the server from that folder: `java -jar forge-gui-desktop-<version>-jar-with-dependencies.jar tbld 8080` (or `tbld-server.cmd`), then open `http://localhost:8080/` in two tabs and choose a seat in each.
- Restart the server after each rebuild.

## Tests

- TBLD tests (`forge-gui-desktop/src/test/java/forge/tbld/`):
  `mvn -B -P windows-linux install -T 1C -Dtest='forge.tbld.*Test' -Dsurefire.failIfNoSpecifiedTests=false`
  - `TbldFlashSupertypeTest`: Flash supertype timing; Searing Touch.
  - `TbldWebServerTest`: serves the page; seat 2 mulligans once; seat 1 has auto-pass on and must never get a priority prompt with nothing to play (seat 2, with it off, must); restart.
  - `TbldFullGameTest`: two `TbldBot`s play over the WebSocket protocol until a player loses, and the test checks for lands, creatures, a resolved spell, an attack, a block and a creature dying. The defender blocks by choosing a creature, then the attacker, and must see that creature marked as blocking before it presses OK. After its first block it checks once that selecting the blocked attacker changes nothing and that selecting the blocking creature takes the block back without selecting it.
  - `TbldPicturesTest`: TBLD Mountain has 80 versions and `Mountain|TBLD|69` is collector number M69; pictures are found by version and served at `/pics/`, and nothing outside the pictures folder is. `TbldWebServerTest` also checks that the test deck's Mountains are TBLD version 69 with its picture.
  - `TbldStopsAndPopupsTest`: a seat with stops only in its own main phases gets priority with an empty stack nowhere else (a seat without stops gets it on the other player's turn); with a Mountain and Magma Jet deck, scry 2 arrives as an `order` question whose cards carry their faces, and the card the bot keeps on top is the one it draws next.
  - `TbldBot` acts on the latest prompt, buttons, selection and state after each burst of messages, and never clicks a card that is already chosen (that would unchoose it).
- UI test (`tbld-e2e/ui.mjs`): no server needed. `cd tbld-e2e && node ui.mjs` serves the client from the source file, answers its WebSocket with scripted game states and questions, and checks the board (rows, land stacks, tapped cards, summoning sickness, turn highlight, pictures and drawn frames, hover preview, stack), the phase stops, the decision panel and every popup. Screenshots go to `tbld-e2e/out/ui-*.png`.
- Browser test (`tbld-e2e/play.mjs`): with the packaged server running on port 8090, `cd tbld-e2e && npm install && npx playwright install chromium && node play.mjs http://localhost:8090/`. Two Chromium tabs play a full game by reading and clicking the page; screenshots go to `tbld-e2e/out/`. It acts only after the page has been unchanged for 400 ms. Run it against its own server: browser tabs left open on the same port reconnect and take the seats. A long game can hit its 6-minute limit (seen once on Brett's PC at turn 21); a rerun passed.
- CI: `.github/workflows/tbld-build.yml` runs all of the above on every push to `master` and uploads the package (`tbld-forge-desktop`) and the screenshots (`tbld-browser-test`). Upstream's full test suite (`test-build.yaml`) runs only when started manually; it has not been run against TBLD changes.
- Brett wants the full-game tests moved behind a manual switch once the web features are known to work.

## Repository conventions

- Checkstyle fails the build on unused or redundant imports.
- Card script reference: the Forge wiki pages "Card scripting API", "Creating a custom Card" and "Creating a custom Set". The `TumbledMTG` edition file is `forge-gui/res/editions/TumbledMTG.txt` (code `TBLD`, type `Custom_Set`).
- Forge is GPL-3.0. Distributing a modified desktop client requires publishing this fork's source; a server-only deployment does not.
