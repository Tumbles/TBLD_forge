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
// TBLD pictures come from the repository's picture folder, as the server serves them
await page.route("http://tbld.test/pics/**", route => {
  const file = path.join(here, "../forge-gui/res/tbld/pics", decodeURIComponent(new URL(route.request().url()).pathname.slice("/pics/".length)));
  return fs.existsSync(file) ? route.fulfill({ contentType: file.endsWith(".png") ? "image/png" : "image/jpeg", body: fs.readFileSync(file) }) : route.fulfill({ status: 404, body: "" });
});
await page.routeWebSocket(/^ws:\/\/tbld\.test\/ws\?seat=1(&|$)/, ws => {
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
const emblem = (id, name, sub, text) => card(id, name, "Emblem — " + sub, { set: "TBLD", text, img: "/pics/" + encodeURIComponent("TBLD " + name + ".jpg") });

function makeState(over = {}) {
  return {
    me: 1, turn: 3, phase: "Main phase, precombat", phaseId: "MAIN1", activePlayer: 1, gameOver: false,
    players: [
      { id: 1, name: "Player 1", life: 20, library: 30, mana: { R: 1 }, exile: [], counters: { Energy: 3 },
        hand: [card(20, "Shock", "Instant", { cost: "{R}", colors: "R" }), searing, card(21, "Lava Axe", "Sorcery", { cost: "{4}{R}", colors: "R" })],
        battlefield: [mountain(1), mountain(2), mountain(3), mountain(4, { tapped: true }), mountain(5, { tapped: true }),
          creature(10, "Raging Goblin", 1, 1, { sick: true }), card(11, "Bone Saw", "Artifact - Equipment", { cost: "{0}" }),
          creature(12, "Gray Ogre", 2, 2, { tapped: true, attacking: "Player 2", blockedBy: ["Hill Giant"] })],
        graveyard: [card(30, "Lava Axe", "Sorcery", { cost: "{4}{R}", colors: "R" })],
        command: [emblem(60, "Player One", "Book of Lore", "You skip your first draw step.")] },
      { id: 2, name: "Player 2", life: 18, library: 31, hand: 5, mana: {}, exile: [], graveyard: [],
        command: [emblem(61, "Player Two", "Shard of Mana", "Exile this: Add {C}.")],
        battlefield: [mountain(40), creature(41, "Hill Giant", 3, 3, { blocking: "Gray Ogre", blockingIds: [12], damage: 2 })] },
    ],
    stack: [
      { id: 100, text: "Shock deals 2 damage to target creature.", ability: false, trigger: false, controller: 2, source: ref(shockCard), targets: ["Gray Ogre"], targetCards: [12], targetPlayers: [] },
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
check(await count('#half-me [data-row="back"] .landstack') === 1, "matching lands are stacked, tapped and untapped together");
const fiveBox = await page.locator('#half-me .landstack').first().boundingBox();
const countBox = await page.locator('#half-me .landstack .stackcount').boundingBox();
check((await page.locator('#half-me .landstack .stackcount').textContent()) === "×5" && countBox.y > fiveBox.y + fiveBox.height - 6
  && Math.abs((countBox.x + countBox.width / 2) - (fiveBox.x + fiveBox.width / 2)) < 2, "a stack of 5 lands shows its count under the stack");
await page.screenshot({ path: path.join(here, "out/ui-land-count.png") });
const fourLands = makeState();
fourLands.players[0].battlefield = fourLands.players[0].battlefield.filter(c => c.id !== 5);
send({ t: "state", state: fourLands });
await settle(300);
check((await page.locator('#half-me .landstack .stackcount').textContent()) === "×4", "a stack of 4 lands shows its count");
const threeLands = makeState();
threeLands.players[0].battlefield = threeLands.players[0].battlefield.filter(c => c.id !== 4 && c.id !== 5);
send({ t: "state", state: threeLands });
await settle(300);
check(await count('#half-me .landstack .stackcount') === 0, "a stack of fewer than 4 lands shows no count");
send({ t: "state", state: makeState() });
await settle(300);
const stackIds = await page.locator('#half-me .landstack .card').evaluateAll(xs => xs.map(x => x.dataset.id));
check(JSON.stringify(stackIds) === '["4","5","1","2","3"]', "tapped lands are at the back of the stack");
// two clicks on the same spot tap two lands: after the first, the next untapped land is under the pointer
const topBox = await page.locator('#half-me .card[data-id="3"] .face').boundingBox();
const spot = { x: topBox.x + topBox.width / 2, y: topBox.y + topBox.height / 2 };
const oneMoreTapped = makeState();
oneMoreTapped.players[0].battlefield.find(c => c.id === 3).tapped = true;
send({ t: "state", state: oneMoreTapped });
await settle(300);
check(await count('#half-me [data-row="back"] .landstack') === 1, "tapping a land keeps it in its stack");
const under = await page.evaluate(({ x, y }) => { const c = document.elementFromPoint(x, y); return c && c.closest(".card") ? c.closest(".card").dataset.id : null; }, spot);
check(under === "2", "after the top land is tapped, the next untapped one is in the same spot (" + under + ")");
send({ t: "state", state: makeState() });
await settle(300);
const tappedBox = await page.locator('#half-me .card[data-id="4"] .face').boundingBox();
check(tappedBox && tappedBox.width > tappedBox.height, "a tapped card is turned sideways");
// the front tapped land (5) and the top untapped one (3) share their left and bottom edges: an L
const frontTapped = await page.locator('#half-me .card[data-id="5"] .face').boundingBox();
const topUntapped = await page.locator('#half-me .card[data-id="3"] .face').boundingBox();
check(frontTapped && topUntapped && Math.abs((frontTapped.y + frontTapped.height) - (topUntapped.y + topUntapped.height)) < 1.5
  && Math.abs(frontTapped.x - topUntapped.x) < 1.5 && frontTapped.y > topUntapped.y + 10,
  "tapped lands lie along the bottom of their stack from the top untapped land, forming an L with it");
// a lone tapped land, a tapped stack and a tapped artifact stand on one baseline (the bottom of the back row)
const baseline = makeState();
baseline.players[0].battlefield.push(card(90, "Forest", "Basic Land - Forest", { tapped: true }), card(91, "Bone Saw 2", "Artifact - Equipment", { tapped: true }));
send({ t: "state", state: baseline });
await settle(300);
const bottoms = await Promise.all(["5", "90", "91"].map(async id => { const b = await page.locator('#half-me .card[data-id="' + id + '"] .face').boundingBox(); return b.y + b.height; }));
check(Math.max(...bottoms) - Math.min(...bottoms) < 1.5, "tapped cards in the back row share a baseline: a stacked land, a lone land and an artifact (" + bottoms.map(b => Math.round(b)).join(", ") + ")");
const loneUntapped = await page.locator('#half-me .card[data-id="11"] .face').boundingBox();
check(Math.abs((loneUntapped.y + loneUntapped.height) - Math.max(...bottoms)) < 1.5, "an untapped card in the back row ends on the same baseline");
send({ t: "state", state: makeState() });
await settle(300);
const ogre = await page.locator('#half-me .card[data-id="12"] .face').boundingBox();
check(ogre && ogre.width > ogre.height, "a tapped attacker is sideways too");
check(await count('#half-me .card[data-id="10"].sick .badge.sick') === 1, "a summoning-sick creature is marked");
check(await count('#half-opp .card[data-id="41"].sick') === 0, "a creature that isn't sick is not marked");
check((await page.locator('#half-opp .card[data-id="41"] .tags').textContent()).includes("blocking"), "a blocker is tagged as blocking");
check((await page.locator('#half-opp .card[data-id="41"] .badge.pt').textContent()) === "3/3", "a damaged creature shows its toughness unchanged");
check((await page.locator('#half-opp .card[data-id="41"] .badge.dmg').textContent()) === "2 dmg", "a damaged creature shows its damage");
check(await page.locator("#half-me").evaluate(e => e.classList.contains("active")) && !(await page.locator("#half-opp").evaluate(e => e.classList.contains("active"))),
  "the active player's half is highlighted");
check((await page.locator("#turnwho").textContent()) === "Your turn", "the phase bar says whose turn it is");
check(!(await page.locator("#table").evaluate(e => e.classList.contains("combat"))) && !(await page.locator("#combattag").isVisible()),
  "outside combat the table is not marked as in combat");
send({ t: "state", state: makeState({ phase: "Combat, declare attackers", phaseId: "COMBAT_DECLARE_ATTACKERS" }) });
await settle(300);
check(await page.locator("#table").evaluate(e => e.classList.contains("combat")) && await page.locator("#combattag").isVisible()
  && await count("#phases .ph.combat-step") === 5, "in a combat step the table is marked as in combat, with a Combat tag");
await page.screenshot({ path: path.join(here, "out/ui-combat.png") });
send({ t: "state", state: makeState() });
await settle(300);
check(await count('#half-opp .handbacks .card.back') === 5, "the opponent's hand is shown as card backs");
check((await page.locator('#half-me .pbar .pcounter.energy[data-counter="Energy"]').textContent()).includes("3")
  && await count('#half-opp .pcounter') === 0, "a player's energy is shown in their bar, and a player without counters shows none");
const centered = sel => page.locator(sel).evaluate(row => {
  const r = row.getBoundingClientRect();
  const boxes = [...row.children].map(k => k.getBoundingClientRect());
  const left = Math.min(...boxes.map(b => b.left)), right = Math.max(...boxes.map(b => b.right));
  return Math.abs((left - r.left) - (r.right - right)) <= 2;
});
check(await centered('#half-me [data-row="back"]'), "lands and other permanents are centered in their row");
check(await centered('#half-opp [data-row="back"]'), "a lone land is centered too");

// pictures
const mountainImg = await page.locator('#half-me .card[data-id="1"] img.art').evaluate(i => i.complete && i.naturalWidth > 0).catch(() => false);
check(mountainImg, "an official card shows its picture");
check(await count('#half-me .hand .card[data-id="22"] img') === 0 && (await page.locator('#half-me .hand .card[data-id="22"] .np-name').textContent()) === "Searing Touch"
  && await count('#half-me .hand .card[data-id="22"] .fr-text, #half-me .hand .card[data-id="22"] .frame') === 0,
  "a TBLD card without a picture is a plain placeholder with its name, not a drawn card");

// hover preview
await page.hover('#half-me .card[data-id="10"]');
check(await visible("#preview"), "hovering a card shows the large preview at once");
check((await page.locator("#preview .np-name").textContent()) === "Raging Goblin", "the preview shows the hovered card");
check((await page.locator("#preview .pv-info").textContent()).includes("Summoning sick"), "the preview lists the card's state");
await page.mouse.move(700, 495);
check(!(await visible("#preview")), "the preview hides when the pointer leaves the card");
// a card cast from the hand goes away under the pointer: its preview goes with it
const handCard = page.locator('#half-me .hand .card').first();
const castId = Number(await handCard.getAttribute("data-id"));
await handCard.hover();
check(await visible("#preview"), "hovering a card in the hand shows the preview");
const cast = makeState();
cast.players[0].hand = cast.players[0].hand.filter(c => c.id !== castId);
send({ t: "state", state: cast });
await settle(300);
check(!(await visible("#preview")), "the preview of a card that leaves the hand under the pointer goes away");
// a popup opening over a hovered card hides its preview
send({ t: "state", state: makeState() });
await settle(300);
await page.hover('#half-me .card[data-id="10"]');
call("message", ["A popup over the hovered card", "Note"]);
await settle(300);
check(!(await visible("#preview")), "a popup opening over a hovered card hides its preview");
await page.mouse.move(5, 5);
await page.locator("#dialog .popup .btn.primary").click();
await settle(300);

// an effect is shown as the art of the card that made it, and hovering it shows what it does
const withEffect = makeState();
withEffect.players[1].command = [...withEffect.players[1].command, card(65, "Searing Touch's Effect", "", { set: "TBLD", effect: true,
  img: "/pics/" + encodeURIComponent("TBLD Searing Touch.jpg"), text: "A player dealt damage this way can't gain life this turn." })];
send({ t: "state", state: withEffect });
await settle(400);
const fx = page.locator('#half-opp .command[data-zone="Effects"] .effect[data-name="Searing Touch\'s Effect"]');
check(await fx.count() === 1 && (await count('#half-opp .command[data-zone="Effects"] .card')) === 0, "an effect is shown as art, not as a card");
check((await fx.evaluate(e => getComputedStyle(e).backgroundImage)).includes("Searing%20Touch"), "the art comes from the card that made it");
const fxBox = await fx.boundingBox();
check(Math.abs(fxBox.width / fxBox.height - 631 / 462) < 0.05, "the effect has the art box's shape");
await fx.hover();
check(await visible("#preview .pv-art") && (await page.locator("#preview .pv-info").textContent()).includes("can't gain life"), "hovering an effect shows its art and what it does");
await page.screenshot({ path: path.join(here, "out/ui-effect.png") });
await page.mouse.move(700, 495);
send({ t: "state", state: makeState() });
await settle();

// stack
check(await count("#stack .stack-item.spell .card") === 1, "a spell on the stack is shown as a card");
check(await count("#stack .stack-item.ability") === 1 && (await page.locator("#stack .stack-item.ability .si-kind").textContent()).startsWith("Triggered ability"),
  "an ability on the stack has its own look");
const spellH = (await page.locator("#stack .stack-item.spell .card").boundingBox()).height;
const abilityH = (await page.locator("#stack .stack-item.ability .card").boundingBox()).height;
check(spellH > abilityH * 1.5, "spells are shown larger than abilities");
check((await page.locator("#stack .stack-item.spell .si-targets").textContent()).includes("Gray Ogre"), "a stack item lists its targets");

// "Always yield" on a triggered ability: shown on the trigger only, sent with its key, shown at once, listed in the settings
const goblinKey = "Battlefield: Raging Goblin (10): When it attacks, it deals 1 damage.";
const withKey = (on, yields = []) => { const st = makeState({ alwaysYields: yields }); Object.assign(st.stack[1], { yieldKey: goblinKey, alwaysYield: on }); return st; };
send({ t: "state", state: withKey(false) });
await settle();
check(await count("#stack .stack-item .ayield") === 1 && (await count('#stack .stack-item[data-stack="101"] .ayield')) === 1, "only the triggered ability offers Always yield");
check(!(await visible("#yields")), "the settings list no abilities while none is yielded to");
let ny = received.length;
await page.click('#stack .stack-item[data-stack="101"] .ayield');
check(!!(await waitFor(() => received.slice(ny).find(m => m.t === "act" && m.m === "alwaysYield" && m.key === goblinKey && m.on === true), "always yield on")),
  "Always yield tells the game, with the ability's key");
check(!received.slice(ny).some(m => m.t === "act" && m.m === "stack"), "clicking Always yield does not select the stack item");
check(await count('#stack .stack-item[data-stack="101"] .ayield.on') === 1, "Always yield shows as on at once");
check(await visible("#yields") && (await page.locator("#yields .ychip").textContent()).includes("Raging Goblin"), "the settings list the ability at once");
await page.screenshot({ path: path.join(here, "out/ui-always-yield.png") });
send({ t: "state", state: withKey(true, [{ key: goblinKey, name: "Raging Goblin", text: "When it attacks, it deals 1 damage." }]) });
await settle();
ny = received.length;
await page.click("#yields .ychip");
check(!!(await waitFor(() => received.slice(ny).find(m => m.t === "act" && m.m === "alwaysYield" && m.key === goblinKey && m.on === false), "always yield off")),
  "the list switches Always yield off");
check(!(await visible("#yields")) && (await count('#stack .stack-item[data-stack="101"] .ayield.on')) === 0, "switched off, it leaves the list and the stack shows it off");
send({ t: "state", state: makeState() });
await settle();

// the stack takes the sidebar below the decision panel and scrolls; the log is a popup
const box = sel => page.locator(sel).evaluate(e => ({ h: e.clientHeight, sh: e.scrollHeight, top: e.getBoundingClientRect().top }));
const stackArea = await box("#stack");
check(await count("#side #log") === 0, "the log is not in the sidebar");
const bigStack = makeState();
bigStack.stack = Array.from({ length: 6 }, (_, k) => bigStack.stack.map(s => ({ ...s, id: s.id + 10 * k }))).flat();
send({ t: "state", state: bigStack });
await settle();
const bigArea = await box("#stack");
check(bigArea.h === stackArea.h && bigArea.sh > bigArea.h, "a growing stack keeps its area and scrolls");
const longLog = makeState({ log: Array.from({ length: 30 }, (_, i) => "Log line " + (i + 1)) });
send({ t: "state", state: longLog });
await settle();
await page.click("#log-open");
await settle();
check(await visible("#viewer #log") && (await page.locator("#viewer .p-title").textContent()) === "Game log", "the Game log button opens the log as a popup");
check(await count("#viewer #log div") === 30, "the popup shows the log");
check(await page.locator("#log").evaluate(e => e.scrollTop + e.clientHeight >= e.scrollHeight - 4), "the log opens at the newest line");
send({ t: "state", state: makeState({ log: [...longLog.log, "Log line 31"] }) });
await settle();
check((await page.locator("#log div").last().textContent()) === "Log line 31", "the open log follows the game");
await page.screenshot({ path: path.join(here, "out/ui-log.png") });
await page.click('#viewer button:text-is("Close")');
check(!(await visible("#viewer")), "the log popup closes");
send({ t: "state", state: makeState() });
await settle();

// lines from stack items to their targets, numbered at both ends
const ringAt = end => page.locator(`#arrows circle[data-end="${end}"]`).first().evaluate(c => ({ x: +c.getAttribute("cx"), y: +c.getAttribute("cy") }));
check(await count("#arrows path:not(.block)") === 1, "a stack item with a target has a line to it (and one without has none)");
const ogreBox = await page.locator('#half-me .card[data-id="12"]').boundingBox();
const toCard = await ringAt("target");
check(toCard.x >= ogreBox.x && toCard.x <= ogreBox.x + ogreBox.width && Math.abs(toCard.y - ogreBox.y) < 20, "the line ends at the targeted card");
const shockItem = await page.locator('#stack .stack-item[data-stack="100"]').boundingBox();
const fromItem = await ringAt("stack");
check(Math.abs(fromItem.x - shockItem.x) < 12 && fromItem.y >= shockItem.y && fromItem.y <= shockItem.y + shockItem.height, "the line starts at the stack item");
check((await page.locator("#arrows text").allTextContents()).every(t => t === "1") && (await count("#arrows text")) === 2, "both ends carry the item's number");
await page.screenshot({ path: path.join(here, "out/ui-target-lines.png") });
const atPlayer = makeState();
atPlayer.stack = [{ ...atPlayer.stack[1] }, { ...atPlayer.stack[0], targets: ["Player 2"], targetCards: [], targetPlayers: [2] }];
send({ t: "state", state: atPlayer });
await settle();
const who = await page.locator('.who[data-player-name="2"]').boundingBox();
const toPlayer = await ringAt("target");
check(toPlayer.x >= who.x && toPlayer.x <= who.x + who.width && toPlayer.y >= who.y && toPlayer.y <= who.y + who.height, "a line to a player ends at their name");
check((await page.locator("#arrows text").first().textContent()) === "2", "the number is the item's place on the stack");
send({ t: "state", state: makeState({ stack: [] }) });
await settle();
check(await count("#arrows path:not(.block)") === 0, "the lines go when the stack is empty");
// a line joins each blocker to the attacker it blocks, from facing edge to facing edge
check(await count('#arrows path.block[data-blocker="41"][data-attacker="12"]') === 1 && (await count("#arrows path.block")) === 1, "a line joins the blocker to its attacker");
const blockEnds = await page.locator("#arrows circle.block").evaluateAll(cs => cs.map(c => ({ x: +c.getAttribute("cx"), y: +c.getAttribute("cy") })));
const giantBox = await page.locator('#half-opp .card[data-id="41"]').boundingBox();
const ogreBox2 = await page.locator('#half-me .card[data-id="12"]').boundingBox();
check(blockEnds.length === 2 && Math.abs(blockEnds[0].y - (giantBox.y + giantBox.height)) < 2 && Math.abs(blockEnds[1].y - ogreBox2.y) < 2,
  "the block line runs from the blocker's bottom edge to the attacker's top edge");
await page.screenshot({ path: path.join(here, "out/ui-block-lines.png") });
const noCombat = makeState({ stack: [] });
delete noCombat.players[1].battlefield[1].blockingIds;
send({ t: "state", state: noCombat });
await settle();
check(await count("#arrows path.block") === 0, "no block line without a block");
send({ t: "state", state: makeState() });
await settle();

// zone viewer
await page.click('#half-me .pile[data-zone="Graveyard"]');
check(await visible("#viewer") && (await count('#viewer .card[data-name="Lava Axe"]')) === 1, "clicking the graveyard shows its cards");
await page.click('#viewer button:text-is("Close")');
check(!(await visible("#viewer")), "the zone viewer closes");

// emblems: each player's starting emblem is shown at the end of the battlefield's back row, with its
// picture; this player's can be used
check(await count('#half-me [data-row="back"] [data-zone="Command"] .card[data-name="Player One"]') === 1, "this player's emblem is in the back row");
check(await count('#half-opp [data-row="back"] [data-zone="Command"] .card[data-name="Player Two"]') === 1, "the opponent's emblem is in their back row");
check(await page.locator('#half-me [data-row="back"] > *').last().evaluate(e => e.dataset.zone === "Command"), "the emblem comes after the other permanents");
const emblemH = (await page.locator('#half-me [data-zone="Command"] .card').boundingBox()).height;
const saw = (await page.locator('#half-me .card[data-id="11"]').boundingBox()).height;
check(Math.abs(emblemH - saw) <= 1, "the emblem is as large as a permanent");
check(await count('#half-me .pbar [data-zone="Command"]') === 0, "the emblem is not also in the player bar");
const emblemImg = await page.locator('#half-me [data-zone="Command"] .card img.art').evaluate(i => i.complete && i.naturalWidth > 100).catch(() => false);
check(emblemImg, "an emblem shows its picture");
await page.hover('#half-me [data-zone="Command"] .card[data-id="60"]');
check(await visible("#preview") && (await count("#preview img.art")) === 1, "hovering an emblem shows it large");
await page.mouse.move(700, 495);
selectionMsg({ cards: [], min: 0, max: 0, targets: [], chosen: [], actionable: [60] });
await settle();
check(await count('#half-me [data-zone="Command"] .card[data-id="60"].selectable') === 1, "a usable emblem is highlighted");
await page.click('#half-me [data-zone="Command"] .card[data-id="60"]');
check(!!(await waitFor(() => received.find(m => m.t === "act" && m.m === "card" && m.id === 60), "an emblem click")), "clicking this player's emblem uses it");
await page.click('#half-opp [data-zone="Command"] .card[data-id="61"]');
await settle();
check(!received.find(m => m.t === "act" && m.m === "card" && m.id === 61), "clicking the opponent's emblem does nothing");
selectionMsg({ cards: [], min: 0, max: 0, targets: [], chosen: [], actionable: [] });
await page.screenshot({ path: path.join(here, "out/ui-emblems.png") });

// targeting a player
selectionMsg({ cards: [], min: 0, max: 0, targets: [{ player: 2, name: "Player 2" }], chosen: [], actionable: [20] });
await settle();
check(await count("#half-opp .who.targetable") === 1, "a player that can be targeted is highlighted");
check(await count('#half-me .hand .card[data-id="20"].selectable') === 1, "a playable card is highlighted");
await page.click('[data-player-name="2"]');
check(!!(await waitFor(() => received.find(m => m.t === "act" && m.m === "player" && m.id === 2), "a player click")), "clicking a player selects them");
selectionMsg({ cards: [], min: 0, max: 0, targets: [], chosen: [], actionable: [] });
await settle();
check(!(await page.locator("#half-me .hand").evaluate(e => e.classList.contains("choosing"))), "the hand is not lit up while only casting");

// choosing cards from the hand (discard, exile): the whole hand is lit up like the decision panel
selectionMsg({ cards: [20, 21], min: 1, max: 1, targets: [], chosen: [], actionable: [] });
await settle();
const handBg = await page.locator("#half-me .hand").evaluate(e => e.classList.contains("choosing") ? getComputedStyle(e, "::before").backgroundImage : "");
const handTop = await page.locator("#half-me .hand").evaluate(e => e.getBoundingClientRect().top + parseFloat(getComputedStyle(e, "::before").top));
const labelBottom = Math.max(...await page.locator("#half-me .pile-label").evaluateAll(ls => ls.map(l => l.getBoundingClientRect().bottom)));
check(handTop >= labelBottom - 0.5, `the lit-up hand starts below the pile labels (${handTop} vs ${labelBottom})`);
const panelBg = await page.locator("#decision").evaluate(e => {
  const had = e.classList.contains("mine");
  e.classList.add("mine");
  const bg = getComputedStyle(e).backgroundImage;
  if (!had) e.classList.remove("mine");
  return bg;
});
check(handBg.includes("gradient") && handBg === panelBg, "a choice from the hand lights the hand up with the decision panel's gradient");
await page.screenshot({ path: path.join(here, "out/ui-hand-choice.png") });
selectionMsg({ cards: [41], min: 1, max: 1, targets: [], chosen: [], actionable: [] });
await settle();
check(!(await page.locator("#half-me .hand").evaluate(e => e.classList.contains("choosing"))), "a choice of a card on the battlefield leaves the hand as it is");
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

// ------------------------------------------------------------------ settings: auto-pass, yield all, hold priority

check(pref && pref.autoPass === "manaOnly" && pref.holdPriority === false, "auto-pass defaults to 'if only mana abilities' and hold priority to off");
await page.selectOption("#autopass", "noActions");
pref = await waitFor(() => lastPref().autoPass === "noActions" && lastPref(), "auto-pass change");
check(!!pref, "choosing an auto-pass setting sends it");
await page.click("#hold-label");
pref = await waitFor(() => lastPref().holdPriority === true && lastPref(), "hold priority change");
check(!!pref && pref.autoPass === "noActions", "turning hold priority on sends it with the other settings");
// the deck picker: the server's decks and the one in use; choosing another asks first, then tells the server
send({ t: "hello", seat: 1, deck: "red", decks: [{ id: "red", label: "Red goblins and burn" }, { id: "ub-control", label: "Blue-black counterspells" }] });
await settle();
check(await page.locator("#deck").inputValue() === "red" && (await count("#deck option")) === 2, "the deck picker lists the server's decks and shows the one in use");
page.once("dialog", d => d.dismiss());
await page.selectOption("#deck", "ub-control");
await settle();
check(await page.locator("#deck").inputValue() === "red" && !received.find(m => m.t === "deck"), "declining the new game keeps the deck");
page.once("dialog", d => d.accept());
await page.selectOption("#deck", "ub-control");
check(!!(await waitFor(() => received.find(m => m.t === "deck" && m.id === "ub-control"), "a deck choice")), "choosing a deck tells the server");
check(await page.evaluate(() => localStorage.getItem("tbld.deck.seat1")) === "ub-control", "the chosen deck is remembered for the next connection");
send({ t: "state", state: makeState() });
await settle();
const apBox = await page.locator("#autopass-label").boundingBox();
const yBox = await page.locator("#yield-label").boundingBox();
check(apBox && yBox && Math.abs(apBox.y - yBox.y) < 12 && yBox.x > apBox.x, "Yield all sits beside Auto-pass");
check(await page.locator("#settings").evaluate(e => e.scrollWidth <= e.clientWidth + 1), "the settings fit the side panel");
send({ t: "state", state: makeState({ yieldAll: true }) });
await settle();
check(await page.locator("#yieldall").isChecked(), "Yield all shows that the game is yielding (End Turn turns it on)");
let n = received.length;
await page.click("#yield-label");
check(!!(await waitFor(() => received.slice(n).find(m => m.t === "act" && m.m === "yieldAll" && m.id === 0), "yield all off")), "switching Yield all off tells the game");
send({ t: "state", state: makeState({ yieldAll: false }) });
await settle();
check(!(await page.locator("#yieldall").isChecked()), "Yield all shows when the game stops yielding");
n = received.length;
await page.click("#yield-label");
check(!!(await waitFor(() => received.slice(n).find(m => m.t === "act" && m.m === "yieldAll" && m.id === 1), "yield all on")), "switching Yield all on tells the game");
await page.locator("#settings").screenshot({ path: path.join(here, "out/ui-settings.png") });
check(await count("#settings #deck") === 0 && (await count("#settings #restart")) === 0 && (await count("#playtest #deck")) === 1 && (await count("#playtest #restart")) === 1,
  "the deck picker and Restart are in their own playtest panel, apart from the settings");
check(await page.locator("#playtest").evaluate(e => e.scrollWidth <= e.clientWidth + 1), "the playtest panel fits the side panel");
send({ t: "state", state: makeState() });
await settle();

// ------------------------------------------------------------------ the decision panel

send({ t: "state", state: makeState() });
call("showPromptMessage", [{ player: 1 }, "Priority: you may cast a spell.", null]);
call("updateButtons", [{ player: 1 }, "OK", "End Turn", true, true, false]);
await settle();
check(await page.locator("#decision").evaluate(e => e.classList.contains("mine")), "the decision panel lights up when it is this player's decision");
check((await page.locator("#dhead-text").textContent()) === "Your decision", "the decision panel says so");
await page.keyboard.press("Space");
check(!!(await waitFor(() => received.find(m => m.t === "act" && m.m === "ok"), "OK")), "the space bar presses OK");
await page.keyboard.press("Space");
await settle(200);
check(received.filter(m => m.t === "act" && m.m === "ok").length === 1, "a second press before the engine answers does nothing");
check(await page.locator('#buttons button:text-is("OK")').isDisabled(), "pressed buttons are spent until new ones arrive");
call("updateButtons", [{ player: 1 }, "OK", "End Turn", true, true, false]);
await settle();
await page.click('#buttons button:text-is("OK")');
check(!!(await waitFor(() => received.filter(m => m.t === "act" && m.m === "ok").length === 2, "a second OK")), "new buttons can be pressed again");
// while an action can be undone, End Turn keeps its place and Undo comes after it
call("updateButtons", [{ player: 1 }, "OK", "Undo (1)", true, true, false]);
await settle();
check(JSON.stringify(await page.locator("#buttons button").allTextContents()) === '["OK","End Turn","Undo (1)"]', "End Turn stays beside OK while Undo is offered");
n = received.length;
await page.click('#buttons button:text-is("End Turn")');
check(!!(await waitFor(() => received.slice(n).find(m => m.t === "act" && m.m === "yieldAll" && m.id === 1), "End Turn")), "End Turn turns on Yield all while Undo is offered");
call("updateButtons", [{ player: 1 }, "OK", "Undo (1)", true, true, false]);
await settle();
n = received.length;
await page.click('#buttons button:text-is("Undo (1)")');
check(!!(await waitFor(() => received.slice(n).find(m => m.t === "act" && m.m === "cancel"), "Undo")), "Undo still undoes");
// paying a cost: Auto sits in the player's bar, between the hand and the lands; Cancel stays in the panel
call("showPromptMessage", [{ player: 1 }, "Pay Mana Cost: {R}", null]);
call("updateButtons", [{ player: 1 }, "Auto", "Cancel", true, true, true]);
await settle();
const autoBox = await page.locator('#half-me .pbar #paybtn button:text-is("Auto")').boundingBox();
const handBox = await page.locator('#half-me .hand').boundingBox();
const landsBox = await page.locator('#half-me .landstack').boundingBox();
check(autoBox && autoBox.y > landsBox.y + landsBox.height - 1 && autoBox.y + autoBox.height < handBox.y + handBox.height
  && JSON.stringify(await page.locator("#buttons button").allTextContents()) === '["Cancel"]',
  "while paying, Auto sits between the hand and the lands, and Cancel stays in the decision panel");
n = received.length;
await page.keyboard.press(" ");
check(!!(await waitFor(() => received.slice(n).find(m => m.t === "act" && m.m === "ok"), "Auto")), "the space bar presses Auto");
call("awaitNextInput", []);
await settle(400);
check(await count("#paybtn button") === 0, "the Auto button goes away when the payment is done");
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
await page.keyboard.press("Space");
check(JSON.stringify((await replyFor(1))?.v) === "[]", "the space bar confirms a popup");
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

// ordering simultaneous abilities: one row, left to right in the order they resolve, dragged into place
const sas = [
  { sa: 301, text: "Crackling Cyclops - Whenever you cast a noncreature spell, Crackling Cyclops gets +3/+0 until end of turn." },
  { sa: 302, text: "Jungleborn Pioneer - When Jungleborn Pioneer enters the battlefield, create a 1/1 Merfolk token." },
  { sa: 303, text: "Servant of the Conduit - When Servant of the Conduit enters the battlefield, you get EE." }];
const tileOrder = () => page.locator("#dialog .sa-row .sa-tile").evaluateAll(n => n.map(x => x.dataset.index).join(","));
const tileBoxes = () => page.locator("#dialog .sa-row .sa-tile").evaluateAll(n => n.map(x => { const r = x.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height }; }));
call("order", ["Select order for simultaneous abilities", "Resolve first", 0, 0, sas, [], null, false, true], 11);
await settle();
let tiles = await tileBoxes();
check(tiles.length === 3 && tiles.every((t, i) => Math.abs(t.y - tiles[0].y) < 2 && (i === 0 || t.x > tiles[i - 1].x + tiles[i - 1].w - 1)),
  "simultaneous abilities are in one row, left to right");
const rankTexts = () => page.locator("#dialog .sa-tile .sa-rank").allTextContents();
check(JSON.stringify(await rankTexts()) === JSON.stringify(["Resolves first", "Resolves 2nd", "Resolves last"]), "each ability says when it resolves");
check((await page.locator("#dialog .sa-tile").first().textContent()).includes("goes on the stack last, on top")
  && (await page.locator("#dialog .sa-tile").last().textContent()).includes("goes on the stack first"), "the ends say which goes on the stack first and last");
check((await page.locator("#dialog .hint").textContent()).includes("leftmost goes on the stack last"), "a hint explains the order");
await page.screenshot({ path: path.join(here, "out/ui-popup-order-abilities.png") });
// a click changes nothing
await page.click('#dialog .sa-tile[data-index="1"] .sa-text');
check(await tileOrder() === "0,1,2", "clicking an ability changes nothing");
// drag the first ability past the last one
{
  const b = tiles[0], last = tiles[2];
  await page.mouse.move(b.x + b.w / 2, b.y + 14);
  await page.mouse.down();
  await page.mouse.move(last.x + last.w / 2 + 30, b.y + 20, { steps: 10 });
  check(await count("#dialog .sa-tile.dragging") === 1 && await count("#dialog .drop-mark") === 1, "dragging lifts the ability and shows where it will land");
  const dragged = await page.locator("#dialog .sa-tile.dragging").boundingBox();
  check(Math.abs(dragged.x - (b.x + (last.x + last.w / 2 + 30 - (b.x + b.w / 2)))) < 3, "the dragged ability follows the pointer");
  await page.mouse.up();
  await settle();
}
check(await tileOrder() === "1,2,0" && await count("#dialog .drop-mark") === 0, "dropping it after the last ability moves it to the end");
check(JSON.stringify(await rankTexts()) === JSON.stringify(["Resolves first", "Resolves 2nd", "Resolves last"])
  && (await page.locator("#dialog .sa-tile").first().getAttribute("data-index")) === "1", "the labels follow the new order");
// and back to the front, from the right
tiles = await tileBoxes();
{
  const b = tiles[2], first = tiles[0];
  await page.mouse.move(b.x + b.w / 2, b.y + 14);
  await page.mouse.down();
  await page.mouse.move(first.x + 20, b.y + 14, { steps: 10 });
  await page.mouse.up();
  await settle();
}
check(await tileOrder() === "0,1,2", "dragging the last ability to the front moves it first");
await page.click('#dialog .sa-tile[data-index="0"] .order-btns button:text-is("▶")');
check(await tileOrder() === "1,0,2", "the arrows still work");
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(11))?.v) === "[1,0,2]", "the order is answered, first to resolve first");

// the same question when the order was set before: everything arrives in the chosen pile
call("order", ["Reorder simultaneous abilities", "Resolve first", 0, 0, [], sas, null, false, true], 12);
await settle();
tiles = await tileBoxes();
check(tiles.length === 3 && tiles.every(t => Math.abs(t.y - tiles[0].y) < 2) && await count("#dialog .pile-box:not(.dest)") === 0,
  "a reorder question is the same row, with nothing to choose");
{
  const b = tiles[0], last = tiles[2];
  await page.mouse.move(b.x + b.w / 2, b.y + 14);
  await page.mouse.down();
  await page.mouse.move(last.x + last.w, b.y + 14, { steps: 10 });
  await page.mouse.up();
  await settle();
}
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(12))?.v) === "[1,2,0]", "a reordered row is answered");

// cards in an order question can be dragged too
call("order", ["Arrange cards to be put on top of your library", "Top of library", 0, 0, three, null, null, false, false], 13);
await settle();
{
  const cards = await page.locator("#dialog .pile-box.dest .card").evaluateAll(n => n.map(x => { const r = x.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height }; }));
  const b = cards[2];
  await page.mouse.move(b.x + b.w / 2, b.y + b.h / 2);
  await page.mouse.down();
  await page.mouse.move(cards[0].x + 10, b.y + b.h / 2, { steps: 10 });
  await page.mouse.up();
  await settle();
}
check((await page.locator("#dialog .pile-box.dest .card").evaluateAll(n => n.map(x => x.dataset.index).join(","))) === "2,0,1", "a card dragged to the front moves first");
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(13))?.v) === "[2,0,1]", "the dragged card order is answered");

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

// buyback: one choice from a list that includes not paying it, and only a Confirm button
call("getChoices", ["Choose optional costs: Searing Touch", 1, 1, ["Buyback – {4}{R}", "No buyback"], null, null], 10);
await settle();
check(await count("#dialog .opt") === 2 && (await count('#dialog button:text-is("Choose none")')) === 0,
  "the buyback question lists 'No buyback' as an option, with no Choose none button");
await page.screenshot({ path: path.join(here, "out/ui-popup-buyback.png") });
await page.click('#dialog .opt:text-is("No buyback")');
await page.click("#dialog .primary");
check(JSON.stringify((await replyFor(10))?.v) === "[1]", "choosing No buyback answers it");

// typing in a text field: the space bar types a space and confirms nothing
call("showInputDialog", ["Name a card", "Choose", null, "", null, false], 9);
await settle();
await page.locator("#dialog input").pressSequentially("Hill Giant");
await settle();
check(!received.find(m => m.t === "reply" && m.rid === 9), "typing a space in a text field doesn't confirm");
await page.click('#dialog .p-actions button:text-is("OK")');
check((await replyFor(9))?.v === "Hill Giant", "the typed text is answered");

check(!errors.length, "no errors on the page " + JSON.stringify(errors));
await browser.close();
if (failures.length) { console.log("UI TEST FAILED:\n  " + failures.join("\n  ")); process.exit(1); }
console.log("UI TEST PASSED");
