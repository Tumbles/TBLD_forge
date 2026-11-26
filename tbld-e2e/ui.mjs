// Checks the web client's board and popups without a game server: the page is served from the
// source file, its WebSocket is answered by this script with scripted game states and questions,
// and the test reads the page and the messages the page sends back.
// Usage: node ui.mjs
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(here, "../forge-gui-desktop/src/main/resources/tbld/web/index.html"), "utf8");
// A 1x1 PNG stands in for card pictures
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==", "base64");
fs.mkdirSync(path.join(here, "out"), { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
const page = await context.newPage();
const errors = [];
page.on("pageerror", e => errors.push("page error: " + e.message));
page.on("console", m => { if (m.type() === "error" && !m.text().includes("ERR_FAILED")) errors.push("console error: " + m.text()); });

let server = null;
const received = [];
await page.route("http://tbld.test/**", route => route.fulfill({ contentType: "text/html", body: html }));
await page.route("https://api.scryfall.com/**", route => route.fulfill({ contentType: "image/png", body: png }));
await page.routeWebSocket("ws://tbld.test/ws?seat=1", ws => {
  server = ws;
  ws.onMessage(m => received.push(JSON.parse(m)));
});

const failures = [];
function check(ok, what) { if (!ok) failures.push(what); console.log((ok ? "ok   " : "FAIL ") + what); }
const send = obj => server.send(JSON.stringify(obj));
const call = (m, a, rid) => send(rid === undefined ? { t: "call", m, a } : { t: "call", m, a, rid });
const settle = (ms = 120) => page.waitForTimeout(ms);
async function waitFor(pred, what, ms = 3000) {
  const end = Date.now() + ms;
  while (Date.now() < end) { const v = pred(); if (v) return v; await settle(30); }
  failures.push("timed out waiting for " + what);
  console.log("FAIL timed out waiting for " + what);
  return null;
}
const replyFor = rid => waitFor(() => received.find(m => m.t === "reply" && m.rid === rid), "reply " + rid);
const lastPref = () => [...received].reverse().find(m => m.t === "pref");
const visible = sel => page.locator(sel).first().isVisible().catch(() => false);
const count = sel => page.locator(sel).count();

// ------------------------------------------------------------------ a game state

const img = name => "https://api.scryfall.com/cards/named?format=image&version=normal&exact=" + encodeURIComponent(name);
const card = (id, name, type, extra = {}) => ({ id, name, type, cost: "", text: "", colors: "", set: "M21", token: false,
  tapped: false, sick: false, damage: 0, faceDown: false, img: img(name), ...extra });
const mountain = (id, extra = {}) => card(id, "Mountain", "Basic Land - Mountain", { text: "({T}: Add {R}.)", ...extra });
const creature = (id, name, p, t, extra = {}) => card(id, name, "Creature - Giant", { cost: "{2}{R}", colors: "R", power: p, toughness: t, ...extra });
const ref = c => ({ card: c.id, ...c });
const searing = card(22, "Searing Touch", "Flash Sorcery", { cost: "{R}", colors: "R", set: "TBLD", text: "Searing Touch deals 3 damage to any target.", img: undefined });
const shockCard = card(50, "Shock", "Instant", { cost: "{R}", colors: "R", text: "Shock deals 2 damage to any target." });

function makeState(over = {}) {
  return {
    me: 1, turn: 3, phase: "Main phase, precombat", phaseId: "MAIN1", activePlayer: 1, gameOver: false,
    players: [
      { id: 1, name: "Player 1", life: 20, library: 30, mana: { R: 1 }, exile: [],
        hand: [card(20, "Shock", "Instant", { cost: "{R}", colors: "R" }), searing, card(21, "Lava Axe", "Sorcery", { cost: "{4}{R}", colors: "R" })],
        battlefield: [mountain(1), mountain(2), mountain(3), mountain(4, { tapped: true }), mountain(5, { tapped: true }),
          creature(10, "Raging Goblin", 1, 1, { sick: true }), card(11, "Bone Saw", "Artifact - Equipment", { cost: "{0}" }),
          creature(12, "Gray Ogre", 2, 2, { tapped: true, attacking: "Player 2", blockedBy: ["Hill Giant"] })],
        graveyard: [card(30, "Lava Axe", "Sorcery", { cost: "{4}{R}", colors: "R" })] },
      { id: 2, name: "Player 2", life: 18, library: 31, hand: 5, mana: {}, exile: [], graveyard: [],
        battlefield: [mountain(40), creature(41, "Hill Giant", 3, 3, { blocking: "Gray Ogre", damage: 2 })] },
    ],
    stack: [
      { id: 100, text: "Shock deals 2 damage to target creature.", ability: false, trigger: false, controller: 2, source: ref(shockCard), targets: ["Gray Ogre"] },
      { id: 101, text: "Raging Goblin: when it attacks, it deals 1 damage.", ability: true, trigger: true, controller: 1, source: ref(creature(10, "Raging Goblin", 1, 1)) },
    ],
    log: ["Turn 3 (Player 1)", "Player 1 cast Shock targeting Hill Giant"],
    ...over,
  };
}

await page.goto("http://tbld.test/?seat=1");
await waitFor(() => server, "the page to connect");
send({ t: "hello", seat: 1 });
await waitFor(() => lastPref(), "the page to send its preferences");
send({ t: "state", state: makeState() });
await settle(300);

// ------------------------------------------------------------------ battlefield

check(await count('#half-me [data-row="creatures"] .card[data-id="10"]') === 1, "creatures are in the front row");
check(await count('#half-me [data-row="back"] .card[data-id="11"]') === 1, "a noncreature, nonland permanent is in the back row");
check(await count('#half-me [data-row="back"] .landstack') === 2, "matching lands are stacked: untapped and tapped Mountains in two stacks");
check((await page.locator('#half-me .landstack').first().textContent()).includes("×3"), "the untapped stack shows its count");
const tappedBox = await page.locator('#half-me .card[data-id="4"] .face').boundingBox();
check(tappedBox && tappedBox.width > tappedBox.height, "a tapped card is turned sideways");
const ogre = await page.locator('#half-me .card[data-id="12"] .face').boundingBox();
check(ogre && ogre.width > ogre.height, "a tapped attacker is sideways too");
check(await count('#half-me .card[data-id="10"].sick .badge.sick') === 1, "a summoning-sick creature is marked");
check(await count('#half-opp .card[data-id="41"].sick') === 0, "a creature that isn't sick is not marked");
check((await page.locator('#half-opp .card[data-id="41"] .tags').textContent()).includes("blocking"), "a blocker is tagged as blocking");
check((await page.locator('#half-opp .card[data-id="41"] .badge.pt').textContent()) === "3/1", "a damaged creature shows its remaining toughness");
check(await page.locator("#half-me").evaluate(e => e.classList.contains("active")) && !(await page.locator("#half-opp").evaluate(e => e.classList.contains("active"))),
  "the active player's half is highlighted");
check((await page.locator("#turnwho").textContent()) === "Your turn", "the phase bar says whose turn it is");
check(await count('#half-opp .handbacks .card.back') === 5, "the opponent's hand is shown as card backs");

// pictures
const mountainImg = await page.locator('#half-me .card[data-id="1"] img.art').evaluate(i => i.complete && i.naturalWidth > 0).catch(() => false);
check(mountainImg, "an official card shows its picture");
check(await count('#half-me .hand .card[data-id="22"] img') === 0 && (await page.locator('#half-me .hand .card[data-id="22"] .fr-name').textContent()) === "Searing Touch",
  "a TBLD card without a picture is drawn as a card frame");

// hover preview
await page.hover('#half-me .card[data-id="10"]');
check(await visible("#preview"), "hovering a card shows the large preview at once");
check((await page.locator("#preview .fr-name").textContent()) === "Raging Goblin", "the preview shows the hovered card");
check((await page.locator("#preview .pv-info").textContent()).includes("Summoning sick"), "the preview lists the card's state");
await page.mouse.move(700, 495);
check(!(await visible("#preview")), "the preview hides when the pointer leaves the card");

// stack
check(await count("#stack .stack-item.spell .card") === 1, "a spell on the stack is shown as a card");
check(await count("#stack .stack-item.ability") === 1 && (await page.locator("#stack .stack-item.ability .si-kind").textContent()).startsWith("Triggered ability"),
  "an ability on the stack has its own look");
const spellH = (await page.locator("#stack .stack-item.spell .card").boundingBox()).height;
const abilityH = (await page.locator("#stack .stack-item.ability .card").boundingBox()).height;
check(spellH > abilityH * 1.5, "spells are shown larger than abilities");
check((await page.locator("#stack .stack-item.spell .si-targets").textContent()).includes("Gray Ogre"), "a stack item lists its targets");

// the stack has a set size and scrolls; the toggle between stack and log swaps which one is small
const box = sel => page.locator(sel).evaluate(e => ({ h: e.clientHeight, sh: e.scrollHeight, top: e.getBoundingClientRect().top }));
const spellItemH = (await page.locator("#stack .stack-item.spell").boundingBox()).height;
const smallStack = await box("#stack");
check(Math.abs(smallStack.h - (1.5 * spellItemH + 8)) <= 4, `the stack shows one and a half spells (${smallStack.h}px for ${spellItemH}px spells)`);
check(smallStack.sh > smallStack.h, "a stack taller than its area scrolls");
const bigStack = makeState();
bigStack.stack = [...bigStack.stack, ...bigStack.stack.map(s => ({ ...s, id: s.id + 10 })), ...bigStack.stack.map(s => ({ ...s, id: s.id + 20 }))];
send({ t: "state", state: bigStack });
await settle();
check((await box("#stack")).h === smallStack.h, "the stack area keeps its size as the stack grows");
const longLog = makeState({ log: Array.from({ length: 30 }, (_, i) => "Log line " + (i + 1)) });
send({ t: "state", state: longLog });
await settle();
const logSmallStack = await box("#log");
await page.screenshot({ path: path.join(here, "out/ui-stack-small.png") });
await page.click("#split-toggle");
await settle();
await page.screenshot({ path: path.join(here, "out/ui-log-small.png") });
const lineH = await page.locator("#log div").first().evaluate(e => e.getBoundingClientRect().height);
const smallLog = await box("#log");
check(Math.abs((smallLog.h - 20) / lineH - 5) < 0.5, `the toggle shrinks the log to five lines (${smallLog.h}px, lines of ${lineH}px)`);
check((await box("#stack")).h > smallStack.h + 100 && logSmallStack.h > smallLog.h + 100, "and gives the rest to the stack");
check(await page.locator("#log").evaluate(e => e.scrollTop + e.clientHeight >= e.scrollHeight - 4), "the small log still shows the newest lines");
check(await page.evaluate(() => localStorage.getItem("tbld.sidebarSplit")) === "log-small", "the choice is remembered");
await page.click("#split-toggle");
await settle();
check((await box("#stack")).h === smallStack.h && (await box("#log")).h === logSmallStack.h, "the toggle switches back");
send({ t: "state", state: makeState() });
await settle();

// zone viewer
await page.click('#half-me .pile[data-zone="Graveyard"]');
check(await visible("#viewer") && (await count('#viewer .card[data-name="Lava Axe"]')) === 1, "clicking the graveyard shows its cards");
await page.click('#viewer button:text-is("Close")');
check(!(await visible("#viewer")), "the zone viewer closes");

// targeting a player
selectionMsg({ cards: [], min: 0, max: 0, targets: [{ player: 2, name: "Player 2" }], chosen: [], actionable: [20] });
await settle();
check(await count("#half-opp .who.targetable") === 1, "a player that can be targeted is highlighted");
check(await count('#half-me .hand .card[data-id="20"].selectable') === 1, "a playable card is highlighted");
await page.click('[data-player-name="2"]');
check(!!(await waitFor(() => received.find(m => m.t === "act" && m.m === "player" && m.id === 2), "a player click")), "clicking a player selects them");
selectionMsg({ cards: [], min: 0, max: 0, targets: [], chosen: [], actionable: [] });
function selectionMsg(sel) { call("setSelection", [sel]); }

// many cards in hand overlap instead of overflowing
const bigHand = makeState();
bigHand.players[0].hand = Array.from({ length: 14 }, (_, i) => card(200 + i, "Hill Giant", "Creature - Giant", { power: 3, toughness: 3, colors: "R" }));
send({ t: "state", state: bigHand });
await settle(300);
const overflow = await page.locator("#half-me .hand").evaluate(h => {
  const r = h.getBoundingClientRect();
  return [...h.children].every(c => { const b = c.getBoundingClientRect(); return b.left >= r.left - 1 && b.right <= r.right + 1; });
});
check(overflow, "a large hand overlaps to fit");
send({ t: "state", state: makeState() });
await settle(200);
await page.screenshot({ path: path.join(here, "out/ui-board.png") });

// the other player's turn
send({ t: "state", state: makeState({ activePlayer: 2, phaseId: "COMBAT_DECLARE_ATTACKERS", phase: "Declare Attackers Step" }) });
await settle();
check(await page.locator("#half-opp").evaluate(e => e.classList.contains("active")), "the highlight moves to the other player's half on their turn");
check((await page.locator("#turnwho").textContent()) === "Player 2's turn", "the phase bar names the other player's turn");
check(await count('.ph[data-phase="COMBAT_DECLARE_ATTACKERS"].current.theirs') === 1, "the current phase is marked on the phase bar");

// ------------------------------------------------------------------ priority stops

let pref = lastPref();
check(pref.stops && pref.stops.own.includes("MAIN1") && pref.stops.opponent.includes("END_OF_TURN") && !pref.stops.own.includes("UPKEEP"),
  "default stops are sent on connect");
const before = received.length;
await page.click('.stop.own[data-phase="UPKEEP"]');
await page.click('.stop.opp[data-phase="COMBAT_DAMAGE"]');
pref = await waitFor(() => received.slice(before).filter(m => m.t === "pref").length >= 2 && lastPref(), "stop changes");
check(pref && pref.stops.own.includes("UPKEEP"), "turning on a stop sends it");
check(pref && pref.stops.opponent.includes("COMBAT_DAMAGE") && pref.stops.opponent.includes("COMBAT_FIRST_STRIKE_DAMAGE"), "the damage stop covers first-strike damage");
check(await count('.stop.own.on[data-phase="UPKEEP"]') === 1, "a stop that is on is lit");
await page.click('.stop.own[data-phase="MAIN2"]');
pref = await waitFor(() => !lastPref().stops.own.includes("MAIN2") && lastPref(), "turning off a stop");
check(pref && !pref.stops.own.includes("MAIN2"), "turning off a stop sends it");

// ------------------------------------------------------------------ the decision panel

send({ t: "state", state: makeState() });
call("showPromptMessage", [{ player: 1 }, "Priority: you may cast a spell.", null]);
call("updateButtons", [{ player: 1 }, "OK", "End Turn", true, true, false]);
await settle();
check(await page.locator("#decision").evaluate(e => e.classList.contains("mine")), "the decision panel lights up when it is this player's decision");
check((await page.locator("#dhead-text").textContent()) === "Your decision", "the decision panel says so");
await page.click('#buttons button:text-is("OK")');
check(!!(await waitFor(() => received.find(m => m.t === "act" && m.m === "ok"), "OK")), "the OK button acts");
call("awaitNextInput", []);
await settle(400);
check(await page.locator("#decision").evaluate(e => e.classList.contains("waiting")) && (await page.locator("#prompt").textContent()) === "Waiting for opponent...",
  "the decision panel goes quiet while the opponent decides");
// a prompt about a card shows the card (Forge's scry 1 asks this way)
call("showPromptMessage", [{ player: 1 }, "Put Hill Giant on the top or bottom of your library?", ref(creature(90, "Hill Giant", 3, 3))]);
call("updateButtons", [{ player: 1 }, "Top", "Bottom", true, true, false]);
await settle();
check(await count('#focus .card[data-name="Hill Giant"]') === 1, "the card a prompt is about is shown with it");
call("awaitNextInput", []);
await settle(400);

// at the end of the game the decision panel says who won
send({ t: "state", state: makeState({ gameOver: true, stack: [], log: ["Player 1 has won because all opponents have lost"] }) });
await settle();
check((await page.locator("#prompt").textContent()).startsWith("Player 1 has won") && (await page.locator("#dhead-text").textContent()) === "Game over",
  "the decision panel says who won when the game is over");
send({ t: "state", state: makeState() });
await settle();

// ------------------------------------------------------------------ popups

const opp = makeState().players[1];
const hand3 = [ref(card(60, "Shock", "Instant", { colors: "R" })), ref(card(61, "Lava Axe", "Sorcery", { colors: "R" })), ref(mountain(62)), ref(creature(63, "Gray Ogre", 2, 2))];

// a revealed hand
call("getChoices", ["Player 2 reveals their hand", -1, -1, hand3, null, null], 1);
await settle();
check(await visible("#dialog .popup") && (await count("#dialog .choices .card")) === 4, "revealed cards are shown as cards in a popup");
await page.screenshot({ path: path.join(here, "out/ui-popup-reveal.png") });
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(1))?.v) === "[]", "a reveal is closed with OK");
check(!(await visible("#dialog .popup")), "the popup closes after the answer");

// discard-style choice from a revealed hand: only some cards can be chosen
call("chooseSingleEntityForEffect", ["Choose a card to discard", [hand3[0], hand3[1]], { reveal: hand3, message: "" }, false], 2);
await settle();
check(await count("#dialog .choices .card.revealed-only") === 2 && (await count("#dialog .choices .card.opt")) === 2,
  "cards that are only revealed are shown, dimmed, beside the choosable ones");
check(await page.locator("#dialog .primary").isDisabled(), "Confirm waits for a choice");
await page.click('#dialog .card.opt[data-index="1"]');
check(await count("#dialog .card.opt.picked") === 1, "clicking a card picks it");
await page.screenshot({ path: path.join(here, "out/ui-popup-choose.png") });
await page.click("#dialog .primary");
check((await replyFor(2))?.v === 1, "the picked card is answered by its index");

// scry 2: choose the cards to put on the bottom
const top2 = [ref(creature(70, "Hill Giant", 3, 3)), ref(mountain(71))];
call("order", ["Select cards to be put on the bottom of your library", "Cards to put on the bottom", -1, -1, top2, null, null, false, false], 3);
await settle();
check(await count("#dialog .pile-box:not(.dest) .card.opt") === 2, "scry offers both cards");
await page.click('#dialog .pile-box:not(.dest) .card[data-index="1"]');
check(await count('#dialog .pile-box.dest .card[data-index="1"]') === 1, "a clicked card moves to the bottom pile");
await page.screenshot({ path: path.join(here, "out/ui-popup-scry.png") });
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(3))?.v) === "[1]", "scry answers with the cards put on the bottom");

// put cards in order
const three = [ref(creature(80, "Hill Giant", 3, 3)), ref(mountain(81)), ref(creature(82, "Gray Ogre", 2, 2))];
call("order", ["Arrange cards to be put on top of your library", "Top of library", 0, 0, three, null, null, false, false], 4);
await settle();
await page.click('#dialog .card[data-index="0"] .order-btns button:text-is("▶")');
check((await page.locator("#dialog .pile-box.dest .card").evaluateAll(n => n.map(x => x.dataset.index).join(","))) === "1,0,2", "arrows reorder the cards");
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(4))?.v) === "[1,0,2]", "the order is answered");

// choose exactly two of four (e.g. discard two)
call("order", ["Choose 2 cards to discard", "Discarded", 2, 2, hand3, null, null, false, false], 5);
await settle();
check((await page.locator("#dialog").getAttribute("data-need")) === "2" && await page.locator("#dialog .primary").isDisabled(), "a choice of exactly two needs two");
await page.click('#dialog .pile-box:not(.dest) .card[data-index="0"]');
await page.click('#dialog .pile-box:not(.dest) .card[data-index="3"]');
check(!(await page.locator("#dialog .primary").isDisabled()), "after two picks it can be confirmed");
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(5))?.v) === "[0,3]", "both picks are answered in order");

// a yes/no question about a card, and hiding a popup to look at the board
call("confirm", [ref(creature(85, "Hill Giant", 3, 3)), "Put Hill Giant on top of your library?", true, ["Top", "Bottom"]], 6);
await settle();
check(await count("#dialog .p-ref .card") === 1, "a question about a card shows the card");
await page.click("#dialog .p-hide");
check(!(await visible("#dialog .popup")) && await visible("#dialog .reopen"), "a popup can be hidden to look at the board");
await page.click("#dialog .reopen");
check(await visible("#dialog .popup"), "and brought back");
await page.click('#dialog button:text-is("Bottom")');
check((await replyFor(6))?.v === false, "the second option answers no");

// several questions arrive at once: they are asked one after another
call("showOptionDialog", ["Choose a mode", "Mode", null, ["Deal 2 damage", "Draw a card"], 0], 7);
call("getChoices", ["Choose a number", 1, 1, [0, 1, 2, 3], null, null], 8);
await settle();
await page.click('#dialog .opt:text-is("Draw a card")');
check((await replyFor(7))?.v === 1, "an option button answers its index");
await settle();
await page.click('#dialog .opt:text-is("2")');
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(8))?.v) === "[2]", "the next question follows");

check(!errors.length, "no errors on the page " + JSON.stringify(errors));
await browser.close();
if (failures.length) { console.log("UI TEST FAILED:\n  " + failures.join("\n  ")); process.exit(1); }
console.log("UI TEST PASSED");
