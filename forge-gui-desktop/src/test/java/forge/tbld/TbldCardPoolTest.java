package forge.tbld;

import forge.StaticData;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.ai.AITest;
import forge.gui.card.CardScriptLinter;
import forge.gui.card.CardScriptLinter.Finding;
import forge.gui.card.CardScriptLinter.Severity;
import forge.item.PaperCard;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The TBLD card scripts against Brett's card pool: the pool tool's check passes (every script says
 * what its pool card says, and the TumbledMTG edition lists exactly the scripts), the linter finds no
 * errors, and every script is a TBLD card in the card database.
 */
public class TbldCardPoolTest extends AITest {

    private static Path root() {
        for (Path d = Paths.get("").toAbsolutePath(); d != null; d = d.getParent()) {
            if (Files.isDirectory(d.resolve("tbld-pool"))) {
                return d;
            }
        }
        throw new IllegalStateException("repository root not found");
    }

    private static List<Path> scripts() throws IOException {
        try (Stream<Path> s = Files.list(root().resolve("forge-gui/res/cardsfolder/tbld"))) {
            return s.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
    }

    @Test
    public void poolToolCheckPasses() throws Exception {
        final String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        final Process p = new ProcessBuilder(java, "tbld-pool/TbldPool.java", "check").directory(root().toFile()).redirectErrorStream(true).start();
        final String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        AssertJUnit.assertTrue("the pool check did not finish", p.waitFor(5, TimeUnit.MINUTES));
        System.out.println(out);
        AssertJUnit.assertEquals("TbldPool check failed:\n" + out, 0, p.exitValue());
    }

    /**
     * tbld-pool/verified.txt, the cards "TbldPool verify" counts as implemented, lists exactly the
     * cards whose abilities the card tests play (TbldVerified), and each is a scripted card matching the pool.
     */
    @Test
    public void verifiedCardsAreTheOnesTheTestsPlay() throws Exception {
        final List<String> listed = new ArrayList<>();
        for (final String l : Files.readAllLines(root().resolve("tbld-pool/verified.txt"), StandardCharsets.UTF_8)) {
            if (!l.isBlank() && !l.startsWith("#")) {
                listed.add(l.trim());
            }
        }
        AssertJUnit.assertNull("a card is verified by two tests", TbldVerified.duplicate());
        AssertJUnit.assertEquals("tbld-pool/verified.txt and the card tests' VERIFIED lists (TbldVerified) differ",
                TbldVerified.all(), new java.util.TreeSet<>(listed));
        final String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        final Process p = new ProcessBuilder(java, "tbld-pool/TbldPool.java", "verify").directory(root().toFile()).redirectErrorStream(true).start();
        final String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        AssertJUnit.assertTrue("the pool verify did not finish", p.waitFor(5, TimeUnit.MINUTES));
        System.out.println(out);
        AssertJUnit.assertEquals("TbldPool verify failed:\n" + out, 0, p.exitValue());
    }

    @Test
    public void scriptsHaveNoLintErrors() throws IOException {
        final CardScriptLinter linter = new CardScriptLinter();
        final List<String> errors = new ArrayList<>();
        for (final Path f : scripts()) {
            for (final Finding finding : linter.lint(Files.readString(f, StandardCharsets.UTF_8))) {
                if (finding.severity() == Severity.ERROR) {
                    errors.add(f.getFileName() + ": " + finding.toString().replace("→", "->"));
                }
            }
        }
        AssertJUnit.assertTrue("card-script errors:\n  " + String.join("\n  ", errors), errors.isEmpty());
    }

    @Test
    public void everyScriptIsATbldCard() throws IOException {
        final List<String> missing = new ArrayList<>();
        for (final Path f : scripts()) {
            final List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            String name = lines.stream().filter(l -> l.startsWith("Name:")).findFirst().orElse("Name:").substring(5);
            if (lines.contains("AlternateMode:Split")) {
                name = name + " // " + lines.stream().filter(l -> l.startsWith("Name:")).skip(1).findFirst().orElse("Name:").substring(5);
            }
            final PaperCard pc = StaticData.instance().getCommonCards().getCard(name, "TBLD");
            if (pc == null || !"TBLD".equals(pc.getEdition())) {
                missing.add(f.getFileName() + " (" + name + ")");
            }
        }
        AssertJUnit.assertTrue("not TBLD cards in the card database: " + missing, missing.isEmpty());
    }

    /** Every TBLD card and every TBLD token script ("tbld_" in tokenscripts) can be made into a game object. */
    @Test
    public void everyTbldCardAndTokenCanBeMade() throws IOException {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(0);
        final List<String> failed = new ArrayList<>();
        for (final PaperCard pc : StaticData.instance().getCommonCards().getAllCards()) {
            if (!"TBLD".equals(pc.getEdition())) {
                continue;
            }
            try {
                if (Card.fromPaperCard(pc, p) == null) {
                    failed.add(pc.getName());
                }
            } catch (final RuntimeException e) {
                failed.add(pc.getName() + " (" + e + ")");
            }
        }
        try (Stream<Path> s = Files.list(root().resolve("forge-gui/res/tokenscripts"))) {
            for (final Path f : s.filter(x -> x.getFileName().toString().startsWith("tbld_")).toList()) {
                final String name = f.getFileName().toString().replaceFirst("\\.txt$", "");
                try {
                    if (createToken(name, p) == null) {
                        failed.add("token " + name);
                    }
                } catch (final RuntimeException e) {
                    failed.add("token " + name + " (" + e + ")");
                }
            }
        }
        AssertJUnit.assertTrue("could not be made: " + failed, failed.isEmpty());
    }
}
