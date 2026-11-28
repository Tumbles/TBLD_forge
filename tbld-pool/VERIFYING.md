# Verifying a batch of scripted cards

One batch = one fresh thread = 40-60 cards of one colour, one test class. Goal: a card counts as implemented only when an
automated in-game test plays every ability and checks the result against the card's text. Never make mockup images; skip
cards without a picture. Brett's rules: commit and push without asking; one thread at a time; report counts + next card.

## Where things are

| What | Where |
|---|---|
| Card scripts | `forge-gui/res/cardsfolder/tbld/tbld_<name>.txt` |
| Brett's card data (imported) | `forge-gui/res/tbld/pool/cards.txt` (`[Name]`, cost=, type=, pt=, text= with `\n`) |
| Pictures | `forge-gui/res/tbld/pics/TBLD <Name>.jpg` (the picture beats the XML) |
| Picture/XML fixes | `tbld-pool/corrections.txt` (`Name \| field=value`; field = cost, type, pt, text, name) |
| Verified names | `tbld-pool/verified.txt` (must equal the tests' `VERIFIED` lists) |
| Test lists | `forge-gui-desktop/src/test/java/forge/tbld/TbldVerified.java` (`LISTS`) |
| Test base | `forge-gui-desktop/src/test/java/forge/tbld/TbldCardTest.java` |
| Examples | `TbldBlueCards2Test.java`, `TbldBlueCards3Test.java` (copy their helpers) |
| Official reference scripts | `tbld-pool/reference/cardsfolder/<letter>/` |
| Known problems | `tbld-pool/blocked.txt`, `approximations.txt` |
| Previous pitfalls | project memory `card-verification` |

Work in the cloud clone of Tumbles/TBLD_forge on master (`add_repo` with push access, one `git clone --depth 1`, then
`register_repo_root`). Maven and the JDK are installed.

## Steps

1. **Pick the batch**: `python3 tbld-pool/next_batch.py U 50` (colour U/G/R/W/C, or M for multicolour). Order so far: black,
   multi, blue; then G, R, W, colourless. Only the next 40-60.
2. **Read each script** (`cat forge-gui/res/cardsfolder/tbld/tbld_<name>.txt`; the `Oracle:` line is Brett's text).
3. **Compare each picture with the XML/script text.** Montage 6 per image:
   `cd forge-gui/res/tbld/pics && montage "TBLD A.jpg" ... -tile 3x2 -geometry 375x523+2+2 /tmp/m.jpg`, then Read the image.
   Zoom a text box: `convert f.jpg -crop 375x230+0+293 -resize 200% z.png`. On a difference (cost, type, text, P/T):
   add a line to `corrections.txt`, edit the `[Name]` entry in `pool/cards.txt` by hand, and edit the script
   (`ManaCost`, `Types`, `Oracle`, abilities) so it matches the picture exactly. Tell Brett in the report.
   Check: `java tbld-pool/TbldPool.java check` (must print 0 problems).
4. **Write `Tbld<Colour>Cards<N>Test`** (see below). One `@Test` per card, playing every ability; assert what Brett's text says.
   Every asserted value should differ if the script were wrong (a test that passes with a broken script is useless).
5. **Register**: add `TbldXxxCards<N>Test.VERIFIED` to `TbldVerified.LISTS`; add the names to `tbld-pool/verified.txt`
   (alphabetical, below the `#` header).
6. **Run**: `mvn -B -P windows-linux install -T 1C -pl forge-gui-desktop -am -Dtest='forge.tbld.TbldBlueCards3Test,forge.tbld.TbldCardPoolTest' -Dsurefire.failIfNoSpecifiedTests=false`
   (about 45 s; CardPoolTest checks the verified list and script/pool match). Rerun only failing classes. A failing test
   means fix the **script** (or the engine, if the mechanic is wrong), not the test.
7. **Sanity check**: temporarily break 5-7 scripts (change a number or cost), rerun, confirm those tests fail, restore.
8. **Recount**: `java tbld-pool/TbldPool.java verify` prints implemented / scripted / placeholder / blocked.
9. **Docs**: update the counts and test-class list in `CLAUDE.md` (Card pool and Tests sections) and the memory file
   `card-verification` (batch done, next card).
10. **Commit and push** (below), then report: counts, next card, picture differences.

## Minimal test class

```java
package forge.tbld;

import forge.game.card.Card;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import java.util.List;

public class TbldBlueCards4Test extends TbldCardTest {
    public static final List<String> VERIFIED = List.of("Jump", "Pestermite");

    @Test
    public void jump() {
        newGame();                                   // me = active player in main 1, opp = the other; libraries filled
        lands("Island", 1, me);                      // mana
        final Card c = creature("Carrion Feeder", me);   // on the battlefield, can attack
        cast(hand("Jump", me), c);                   // cast with targets, resolve everything
        AssertJUnit.assertTrue(current(c).hasKeyword(forge.game.keyword.Keyword.FLYING));
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
    }
}
```

Helpers (all in `TbldCardTest`; copy the private ones you need from `TbldBlueCards3Test`: `oppCasts`, `canCast`,
`countered`, `pt`, `wardCounters`, `ninjutsu`): `newGame()`, `lands`, `creature` (enters, can attack), `enter` (enters,
triggers resolve), `hand`, `grave`, `library(p, names...)` (last named is drawn first), `cast(card, targets...)`,
`activate(card, "text of ability", targets...)`, `put` (cast without resolving), `alternative(card, SpellAbility::isEvoke)`
(also isBestow, isEscape, isMutate, isForetold, isFlashback...), `withOptionalCosts(card)` (jump-start/kicker),
`playUntil(player, PhaseType)`, `attack(me, blocksMap, attackers...)`, `kill(card)` (so dies triggers run), `damage`,
`resolve()`, `current(card)`, `in(card, zone)`, `count(player, zone)`, `tokens(player)`, `p1p1(card)`.
`choices(player)` overrides AI decisions: `confirm`, `modes` ("choose one" text), `targets` (triggers; cards, players or
spells), `prefer` (cards for searches/discards/choices), `payUnless`, `number`, `cardName`, `attackers`, `blocks`.

## Pitfalls (more in memory `card-verification`)

- X costs: `sa.setXManaCostPaid(n)` before cast. Add hand cards after `playUntil` (the AI casts hand cards while turns pass).
- `addCardToZone` to the battlefield fires no ETB: use `enter()`. `hand()` doesn't refresh statics: call `resolve()`.
- Use real combat (`attack`) for "damage dealt this turn" checks; `kill()` not `destroy` for dies triggers.
- Libraries are Woolly Thoctar (6/4 RGW creature). Use `library(...)` to set the top cards.
- Weak-test trap: a result that is the same under a wrong script (e.g. draw 2 put 1 back vs draw 3 put 2 back both leave
  1 card in hand): also assert `me.getNumDrawnThisTurn()`, mana actually paid, counters, zones.
- Opponent AI uses its own abilities; avoid sacrifice outlets on `opp`.
- TBLD rules: Ingest exiles two cards; Clue is an enchantment (`tbld_c_e_clue_scan`); Scan = Dig 2, exile one; transmute
  exiles the card from hand (engine changed); Flash is a supertype. TBLD votes aren't scripted.

## Known flaky or failing (ignore)

`TbldWebTriggersTest` fails in the cloud (passes in CI). The browser-test CI step (`tbld-e2e/play.mjs`) was already failing.
Do not touch them.

## Commit, rebase, push

Other threads push to master at the same time. The clone is shallow.

```
git add -A && git commit -m "Verify blue cards, batch 4: A to B"      # end with the attribution lines from the session
git fetch --depth=50 origin master
git rebase origin/master            # conflicts are usually verified.txt / TbldVerified.java / CLAUDE.md: keep both sides, re-sort verified.txt
git -c push.negotiate=true push origin HEAD:master
```

If the push is rejected, fetch and rebase again. After a rebase rerun `TbldCardPoolTest` if verified.txt or scripts conflicted.
