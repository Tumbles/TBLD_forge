package forge.tbld;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every card whose abilities an automated in-game test plays: the VERIFIED lists of the card tests.
 * {@code tbld-pool/verified.txt} must list exactly these ({@code TbldCardPoolTest} checks it).
 */
public final class TbldVerified {
    private TbldVerified() {
    }

    /** The VERIFIED list of each test that plays cards; add a new card test here. */
    static final List<List<String>> LISTS = List.of(
            TbldTestDecksTest.VERIFIED,
            TbldBlackCards1Test.VERIFIED,
            TbldBlackCards2Test.VERIFIED,
            TbldBlackCards3Test.VERIFIED,
            TbldBlackCards4Test.VERIFIED,
            TbldMultiCards1Test.VERIFIED,
            TbldMultiCards2Test.VERIFIED,
            TbldBlueCards1Test.VERIFIED,
            TbldBlueCards2Test.VERIFIED,
            TbldBlueCards3Test.VERIFIED,
            TbldBlueCards4Test.VERIFIED,
            TbldGreenCards1Test.VERIFIED,
            TbldGreenCards2Test.VERIFIED);

    public static Set<String> all() {
        final Set<String> out = new TreeSet<>();
        LISTS.stream().flatMap(List::stream).forEach(out::add);
        return out;
    }

    /** A card listed by two tests, or null. */
    static String duplicate() {
        final Set<String> seen = new TreeSet<>();
        for (final List<String> l : LISTS) {
            for (final String n : l) {
                if (!seen.add(n)) {
                    return n;
                }
            }
        }
        return null;
    }
}
