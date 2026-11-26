// Plays a full game in two browser tabs of the TBLD web client, by reading the page and clicking it
// as a person would. Seat 1 attacks with everything; seat 2 never attacks and blocks.
// Usage: node play.mjs http://localhost:8090/
import { chromium } from "playwright";
import fs from "node:fs";

const base = process.argv[2] || "http://localhost:8090/";
const TIME_LIMIT_MS = 6 * 60 * 1000;
const STUCK_MS = 30 * 1000;

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
fs.mkdirSync("out", { recursive: true });

function makeSeat(seat) {
  return {
    seat, aggressive: seat === 1, page: null, errors: [], actions: [],
    landTurn: -1, attempted: new Set(), blockersClicked: new Set(), blocksConfirmedTurn: -1,
    lastSignature: "", lastChange: Date.now(),
    seen: { lands: 0, creature: false, attack: false, block: false, plannedBlock: false, died: false, spell: false, popups: [],
      emblem: "", emblemUsed: false, colorlessMana: false },
  };
}
const seats = [makeSeat(1), makeSeat(2)];
for (const s of seats) {
  s.page = await context.newPage();
  s.page.on("pageerror", e => s.errors.push("page error: " + e.message));
  s.page.on("console", m => { if (m.type() === "error") s.errors.push("console error: " + m.text()); });
  await s.page.goto(base + "?seat=" + s.seat);
}

// Everything the bot needs from the page, read in one go
async function read(page) {
  return page.evaluate(() => {
    const text = sel => (document.querySelector(sel)?.textContent || "").trim();
    const cards = (panel, zone) => [...(panel?.querySelectorAll(`[data-zone="${zone}"] .card`) || [])].map(c => ({
      id: c.dataset.id, name: c.dataset.name || "", type: c.dataset.type || "",
      selectable: c.classList.contains("selectable"), chosen: c.classList.contains("chosen"),
      tapped: c.classList.contains("tapped"), tags: c.querySelector(".tags")?.textContent || "",
    }));
    const panelInfo = panel => panel && ({
      id: panel.dataset.player, life: +panel.dataset.life, active: panel.classList.contains("active"),
      hand: cards(panel, "Hand"), battlefield: cards(panel, "Battlefield"), graveyard: cards(panel, "Graveyard"),
      command: cards(panel, "Command"), pool: [...(panel?.querySelectorAll(".pool .ms") || [])].map(m => m.textContent),
    });
    const dialog = document.getElementById("dialog");
    const dialogOpen = dialog.style.display === "block";
    const primary = dialog.querySelector(".popup .btn.primary");
    return {
      status: text("#status"), prompt: text("#prompt"), stack: text("#stack"),
      // eslint-disable-next-line no-undef
      selection: typeof selection === "undefined" ? "?" : JSON.stringify(selection),
      buttons: [...document.querySelectorAll("#buttons button")].map(b => ({ text: b.textContent, disabled: b.disabled })),
      // A popup marks its options "opt" ("picked" once chosen), says how many must be picked in
      // data-need, and marks the button that confirms "primary"
      dialog: dialogOpen ? {
        kind: dialog.dataset.kind, title: dialog.querySelector(".p-title")?.textContent || "", need: +(dialog.dataset.need || 0),
        unpicked: dialog.querySelectorAll(".popup .opt:not(.picked)").length, picked: dialog.querySelectorAll(".popup .opt.picked").length,
        primary: !!primary && !primary.disabled,
        buttons: [...dialog.querySelectorAll(".p-actions button")].map(b => b.textContent),
      } : null,
      me: panelInfo(document.querySelector('.player[data-me="1"]')),
      opp: panelInfo(document.querySelector('.player[data-me="0"]')),
    };
  });
}

const isLand = c => c.type.includes("Land");
const isCreature = c => c.type.includes("Creature");

async function click(s, selector, description, turn) {
  s.actions.push(`t${turn} ${description}`);
  await s.page.click(selector, { timeout: 2000 }).catch(e => s.actions.push("  click failed: " + e.message.split("\n")[0]));
}
const button = label => `#buttons button:text-is("${label}")`;

async function step(s) {
  const v = await read(s.page);
  const signature = JSON.stringify(v);
  if (signature !== s.lastSignature) { s.lastSignature = signature; s.lastChange = Date.now(); }
  s.last = v;
  if (!v.me || !v.opp) return;
  const turn = +(v.status.match(/Turn (\d+)/)?.[1] || 0);
  const gameOver = v.status.startsWith("Game over");
  // Record what has happened
  s.seen.lands = Math.max(s.seen.lands, v.me.battlefield.filter(isLand).length);
  if (v.me.battlefield.some(isCreature)) s.seen.creature = true;
  for (const c of [...v.me.battlefield, ...v.opp.battlefield]) {
    if (c.tags.includes("attacking")) s.seen.attack = true;
  }
  // The opponent's blockers are shown only once blocks are declared
  for (const c of v.opp.battlefield) {
    if (c.tags.includes("blocking")) s.seen.block = true;
  }
  for (const c of [...v.me.graveyard, ...v.opp.graveyard]) {
    if (isCreature(c)) s.seen.died = true; else if (/Instant|Sorcery/.test(c.type)) s.seen.spell = true;
  }
  // The starting emblem: which one this seat got, and whether it was used (it leaves the command zone)
  const emblem = v.me.command.find(c => /^Player /.test(c.name));
  if (emblem && !s.seen.emblem) s.seen.emblem = emblem.name;
  if (s.seen.emblem && !emblem) s.seen.emblemUsed = true;
  if (v.me.pool.some(m => m.startsWith("C"))) s.seen.colorlessMana = true;
  // Using an emblem asks no "Exile Player One?" question
  if (/Exile Player .*\?/.test(v.prompt + " " + (v.dialog ? v.dialog.title : ""))) s.seen.exileConfirm = true;
  if (gameOver) return;
  // Act only on a page that has settled, as a person would: messages arrive in bursts, and the
  // buttons from the previous prompt stay up briefly while the next one arrives
  if (Date.now() - s.lastChange < 400) return;

  if (v.dialog) {
    const d = v.dialog;
    if (!s.seen.popups.includes(d.kind)) s.seen.popups.push(d.kind);
    if (d.picked < d.need && d.unpicked > 0) return click(s, "#dialog .popup .opt:not(.picked) >> nth=0", `pick an option in ${d.kind} (${d.title})`, turn);
    if (d.primary) return click(s, "#dialog .popup .btn.primary", `confirm ${d.kind} (${d.title})`, turn);
    if (d.unpicked > 0) return click(s, "#dialog .popup .opt >> nth=0", `answer ${d.kind} with its first option`, turn);
    return click(s, "#dialog .p-actions button >> nth=0", `answer ${d.kind}: ${d.buttons[0]}`, turn);
  }

  const b = Object.fromEntries(v.buttons.map(x => [x.text, !x.disabled]));
  const p = v.prompt.toLowerCase();
  // One mulligan for a hand with fewer than 2 lands, so that a land-starved game doesn't fail the land check
  if (b["Mulligan"] && !s.mulliganed && v.me.hand.filter(isLand).length < 2) { s.mulliganed = true; return click(s, button("Mulligan"), "mulligan", turn); }
  if (b["Keep"]) return click(s, button("Keep"), "keep", turn);
  if (b["Auto"]) return click(s, button("Auto"), "auto-pay", turn);
  // Forge suggests passing until the end of the turn; a player who wants to play declines
  if (b["Decline"]) return click(s, button("Decline"), "decline yield suggestion", turn);
  // A spell that can't be paid for
  if (p.includes("pay mana cost") && b["Cancel"]) return click(s, button("Cancel"), "cancel unpayable spell", turn);

  if (p.includes("select creatures to attack")) {
    if (s.aggressive) {
      const c = v.me.battlefield.find(c => isCreature(c) && c.selectable && !c.tags.includes("attacking") && !s.attempted.has(`attack:${turn}:${c.id}`));
      if (c) { s.attempted.add(`attack:${turn}:${c.id}`); return click(s, `[data-me="1"] .card[data-id="${c.id}"]`, "attack with " + c.name, turn); }
    }
    if (b["OK"]) return click(s, button("OK"), "done attacking", turn);
    return;
  }
  if (p.includes("block")) {
    // A chosen blocker is marked as blocking at once, before OK confirms the blocks
    if (s.blocksConfirmedTurn !== turn && v.me.battlefield.some(c => c.tags.includes("blocking"))) s.seen.plannedBlock = true;
    const attacker = v.opp.battlefield.find(c => c.tags.includes("attacking"));
    if (attacker && !s.aggressive) {
      // Choose a creature, then the attacker it blocks
      const chosen = v.me.battlefield.find(c => c.chosen);
      if (chosen) {
        const key = `assign:${turn}:${chosen.id}`;
        if (!s.attempted.has(key)) {
          s.attempted.add(key);
          return click(s, `[data-me="0"] .card[data-id="${attacker.id}"]`, `block ${attacker.name} with ${chosen.name}`, turn);
        }
      } else {
        const blocker = v.me.battlefield.find(c => isCreature(c) && !c.tapped && !c.tags.includes("blocking") && !s.blockersClicked.has(`${turn}:${c.id}`));
        if (blocker) { s.blockersClicked.add(`${turn}:${blocker.id}`); return click(s, `[data-me="1"] .card[data-id="${blocker.id}"]`, "choose blocker " + blocker.name, turn); }
      }
    }
    if (b["OK"]) { s.blocksConfirmedTurn = turn; return click(s, button("OK"), "done blocking", turn); }
    return;
  }
  if (p.includes("target")) {
    const key = `target:${turn}:${v.prompt}`;
    if (!s.attempted.has(key)) { s.attempted.add(key); return click(s, `[data-player-name="${v.opp.id}"]`, "target opponent", turn); }
    if (b["OK"]) return click(s, button("OK"), "confirm targets", turn);
    if (b["Cancel"]) return click(s, button("Cancel"), "cancel targeting", turn);
    return;
  }
  // A card chosen from the hand, such as the one Player One's emblem exiles: choose one, then OK
  if (p.includes("from your hand") && v.buttons.length) {
    if (b["OK"]) return click(s, button("OK"), "confirm the card chosen from hand", turn);
    if (v.me.hand.length) return click(s, `[data-me="1"] [data-zone="Hand"] .card >> nth=0`, "choose " + v.me.hand[0].name + " from hand", turn);
  }
  if ((p.includes("discard") || p.includes("return")) && v.buttons.length && !b["OK"] && v.me.hand.length) {
    // Chosen cards are not shown in the hand, so any hand card is unchosen
    return click(s, `[data-me="1"] [data-zone="Hand"] .card >> nth=0`, "discard/bottom " + v.me.hand[0].name, turn);
  }
  // Priority in my own main phase with an empty stack: land, then creatures, then other spells
  if (b["OK"] && v.me.active && /main phase/i.test(v.status) && v.stack.includes("(empty)")) {
    const playable = v.me.hand.filter(c => c.selectable);
    const key = `log:${turn}:${v.status}`;
    if (!s.attempted.has(key)) {
      s.attempted.add(key);
      s.actions.push(`t${turn} main phase; hand: ${v.me.hand.map(c => c.id + ":" + c.name + (c.selectable ? "*" : "")).join(", ")}; selection: ${v.selection}; prompt: ${v.prompt}`);
    }
    const land = playable.find(isLand);
    if (land && s.landTurn !== turn) { s.landTurn = turn; return click(s, `[data-me="1"] .card[data-id="${land.id}"]`, "play " + land.name, turn); }
    // The starting emblem, once: Player Two's mana at the first chance, any other when it is highlighted as usable
    // (Player One's needs three untapped lands)
    const emblem = v.me.command.find(c => /^Player /.test(c.name) && (c.selectable || c.name === "Player Two"));
    if (emblem && !s.attempted.has("emblem:" + turn)) {
      s.attempted.add("emblem:" + turn);
      return click(s, `[data-me="1"] [data-zone="Command"] .card[data-id="${emblem.id}"]`, "use emblem " + emblem.name, turn);
    }
    for (const c of [...playable.filter(isCreature), ...playable.filter(c => !isLand(c) && !isCreature(c))]) {
      const key = `cast:${turn}:${c.id}`;
      if (!s.attempted.has(key)) { s.attempted.add(key); return click(s, `[data-me="1"] .card[data-id="${c.id}"]`, "cast " + c.name, turn); }
    }
  }
  if (b["OK"]) return click(s, button("OK"), v.me.active ? "pass (" + v.status + ")" : "pass", turn);
  // Anything else (e.g. Play/Draw after the coin toss): the first button
  if (v.buttons.length && !v.buttons[0].disabled) return click(s, "#buttons button >> nth=0", "press " + v.buttons[0].text, turn);
}

const start = Date.now();
let failure = null;
while (true) {
  for (const s of seats) {
    try { await step(s); } catch (e) { s.errors.push("bot error: " + e.message); }
  }
  const over = seats.every(s => s.last && s.last.status.startsWith("Game over"));
  if (over) break;
  if (Date.now() - start > TIME_LIMIT_MS) { failure = "time limit reached"; break; }
  if (seats.every(s => Date.now() - s.lastChange > STUCK_MS)) { failure = "no change on either page for 30 s"; break; }
  if (seats.some(s => s.errors.length)) { failure = "errors on the page"; break; }
  await new Promise(r => setTimeout(r, 150));
}

const report = seats.map(s => [
  `seat ${s.seat}: ${s.last?.status} | prompt: ${s.last?.prompt?.replace(/\s+/g, " ")} | buttons: ${JSON.stringify(s.last?.buttons)} | dialog: ${JSON.stringify(s.last?.dialog)}`,
  `  life ${s.last?.me?.life}, seen ${JSON.stringify(s.seen)}`,
  `  errors: ${JSON.stringify(s.errors)}`,
  `  last actions: ${s.actions.slice(-30).join(" | ")}`,
].join("\n")).join("\n");

if (!failure) {
  const [a, d] = seats;
  const checks = [
    [a.last.me.life <= 0 || d.last.me.life <= 0, "nobody lost on life"],
    [a.seen.lands >= 3 && d.seen.lands >= 3, "a player played fewer than 3 lands"],
    [a.seen.creature && d.seen.creature, "a player cast no creature"],
    [a.seen.attack, "no attack"],
    [a.seen.block, "seat 1 never saw a block"],
    [d.seen.plannedBlock, "seat 2 never saw its chosen blocker marked as blocking before confirming"],
    [a.seen.died || d.seen.died, "no creature died"],
    [a.seen.spell || d.seen.spell, "no instant or sorcery resolved"],
    [[a.seen.emblem, d.seen.emblem].sort().join() === "Player One,Player Two", "the seats did not get Player One and Player Two"],
    [a.seen.emblemUsed && d.seen.emblemUsed, "a player could not use their emblem"],
    [[a, d].some(s => s.seen.emblem === "Player Two" && s.seen.colorlessMana), "Player Two's emblem added no {C}"],
    [!a.seen.exileConfirm && !d.seen.exileConfirm, "using an emblem asked to confirm exiling it"],
  ];
  const failed = checks.filter(([ok]) => !ok).map(([, m]) => m);
  if (failed.length) failure = failed.join("; ");
}
for (const s of seats) await s.page.screenshot({ path: `out/seat${s.seat}.png`, fullPage: true }).catch(() => {});
await browser.close();
console.log(report);
if (failure) { console.log("BROWSER TEST FAILED: " + failure); process.exit(1); }
console.log("BROWSER TEST PASSED");
