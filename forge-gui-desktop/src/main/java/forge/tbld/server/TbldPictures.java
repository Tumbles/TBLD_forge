package forge.tbld.server;

import forge.localinstance.properties.ForgeConstants;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pictures of TBLD cards, which the web server serves at {@code /pics/<file name>} from
 * {@code res/tbld/pics}, where the pool tool ({@code tbld-pool/TbldPool.java import}) puts them as
 * JPEG files. Files are named as in Brett's card files: "TBLD Searing Touch.jpg", and a split card
 * "Fire // Ice" is "TBLD Fire_Ice.jpg". A card with several versions in the TBLD set (each basic land
 * has 80) has one file per version, numbered by its art index: "TBLD Mountain69.jpg". The server
 * looks a file up once and remembers the result, so it must be restarted after pictures are added.
 */
public final class TbldPictures {

    public static final String URL_PREFIX = "/pics/";
    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".webp");
    private static final Map<String, Boolean> EXISTS = new ConcurrentHashMap<>();

    private TbldPictures() {
    }

    private static Path dir() {
        return Paths.get(ForgeConstants.RES_DIR, "tbld", "pics");
    }

    /**
     * The URL of the picture of a TBLD card's version {@code artIndex}: its own file if it has one,
     * otherwise the card's file without a number. Null if there is neither.
     */
    public static String urlFor(final String name, final int artIndex) {
        final String base0 = "TBLD " + name.replace(" // ", "_");
        final List<String> bases = artIndex > 0 ? List.of(base0 + artIndex, base0) : List.of(base0);
        for (final String base : bases) {
            for (final String ext : EXTENSIONS) {
                final String fileName = base + ext;
                if (file(fileName) != null) {
                    return URL_PREFIX + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                }
            }
        }
        return null;
    }

    /** The picture file with this name, or null if there is none or the name is not a plain picture file name. */
    public static Path file(final String fileName) {
        final String lower = fileName.toLowerCase(Locale.ROOT);
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")
                || EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            return null;
        }
        final Path p;
        try {
            p = dir().resolve(fileName);
        } catch (final InvalidPathException e) {
            return null;
        }
        return EXISTS.computeIfAbsent(fileName, f -> Files.isRegularFile(p)) ? p : null;
    }

    /** One of Brett's tokens from {@code res/tbld/pool/tokens.txt}: "Merfolk4" is version 4 of "Merfolk". */
    private record PoolToken(String name, String base, int number, String pt, String colors) {
    }

    private static volatile List<PoolToken> poolTokens;

    private static List<PoolToken> poolTokens() {
        List<PoolToken> result = poolTokens;
        if (result != null) {
            return result;
        }
        result = new ArrayList<>();
        final Path file = Paths.get(ForgeConstants.RES_DIR, "tbld", "pool", "tokens.txt");
        try {
            String name = null;
            final Map<String, String> fields = new HashMap<>();
            final List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            lines.add("[]");
            for (final String line : lines) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    if (name != null && fields.getOrDefault("type", "").startsWith("Token")) {
                        final Matcher m = VERSION.matcher(name);
                        m.matches();
                        result.add(new PoolToken(name, m.group(1), m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2)),
                                fields.getOrDefault("pt", ""), sortedColors(fields.getOrDefault("color", ""))));
                    }
                    name = line.substring(1, line.length() - 1);
                    fields.clear();
                } else if (line.contains("=")) {
                    fields.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
                }
            }
        } catch (final Exception e) {
            // no pool here (a test without resources): tokens have no pictures
        }
        result.sort(Comparator.comparingInt(PoolToken::number));
        poolTokens = result;
        return result;
    }

    private static final Pattern VERSION = Pattern.compile("(.*?)(\\d*)");

    private static String sortedColors(final String colors) {
        return colors.toUpperCase(Locale.ROOT).chars().filter(ch -> "WUBRG".indexOf(ch) >= 0).sorted()
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    /**
     * The URL of the picture of one of Brett's tokens for a token in the game, or null. The game's
     * token is named like "Merfolk Token"; Brett's tokens are "Merfolk", "Merfolk2" and so on (the
     * same token with other art). A token of the same name and colors is chosen, preferring one with
     * the same power and toughness ({@code pt} such as "1/1", null if not a creature), then the lowest
     * version.
     */
    public static String tokenUrlFor(final String name, final String pt, final String colors) {
        final String base = name.endsWith(" Token") ? name.substring(0, name.length() - " Token".length()) : name;
        final String wanted = sortedColors(colors == null ? "" : colors);
        PoolToken best = null;
        for (final PoolToken t : poolTokens()) {
            if (!t.base().equalsIgnoreCase(base) || !t.colors().equals(wanted) || urlFor(t.name(), 0) == null) {
                continue;
            }
            if (best == null || t.pt().equals(pt == null ? "" : pt) && !best.pt().equals(pt == null ? "" : pt)) {
                best = t;
            }
        }
        return best == null ? null : urlFor(best.name(), 0);
    }

    /** The content type of a picture file. */
    public static String contentType(final String fileName) {
        final String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
