package forge.tbld.server;

import forge.localinstance.properties.ForgeConstants;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
