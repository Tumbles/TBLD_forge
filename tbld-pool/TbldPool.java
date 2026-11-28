import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * The TBLD card pool tool. Run it from the repository root with the JDK alone (no build needed):
 *
 * <pre>
 *   java tbld-pool/TbldPool.java import            read Brett's Cockatrice files and pictures into the repo
 *   java tbld-pool/TbldPool.java status            counts: scripted, out of date, to do
 *   java tbld-pool/TbldPool.java list [filters]    one line per card (see "usage")
 *   java tbld-pool/TbldPool.java show NAME...      a card's pool entry, script and reference script
 *   java tbld-pool/TbldPool.java draft NAME...     write TBLD scripts from the official reference scripts
 *   java tbld-pool/TbldPool.java check             every TBLD script matches the pool (the tests run this)
 *   java tbld-pool/TbldPool.java edition           rewrite the TumbledMTG edition from the scripts
 * </pre>
 *
 * The pool lives in forge-gui/res/tbld/pool (cards.txt and tokens.txt), made by "import" and never
 * edited by hand. CLAUDE.md ("Card pool") describes the workflow.
 */
public class TbldPool {

    static final Path ROOT = findRoot();
    static final Path RES = ROOT.resolve("forge-gui/res");
    static final Path POOL = RES.resolve("tbld/pool");
    static final Path CARDS_FILE = POOL.resolve("cards.txt");
    static final Path TOKENS_FILE = POOL.resolve("tokens.txt");
    static final Path PICS = RES.resolve("tbld/pics");
    static final Path TOOL = ROOT.resolve("tbld-pool");
    static final Path PIC_MANIFEST = TOOL.resolve("pictures.txt");
    static final Path SOURCES = TOOL.resolve("sources.properties");
    static final Path CORRECTIONS = TOOL.resolve("corrections.txt");
    static final Path CARDSFOLDER = RES.resolve("cardsfolder");
    static final Path SCRIPTS = CARDSFOLDER.resolve("tbld");
    static final Path REFERENCE = TOOL.resolve("reference/cardsfolder");
    static final Path EDITION = RES.resolve("editions/TumbledMTG.txt");
    static final Path BLOCKED = TOOL.resolve("blocked.txt");

    static final Pattern BASIC = Pattern.compile("(Plains|Island|Swamp|Mountain|Forest|Wastes)(\\d*)");
    static final Map<String, String> BASIC_LETTER = Map.of("Plains", "P", "Island", "I", "Swamp", "S", "Mountain", "M", "Forest", "F", "Wastes", "W");
    static final Pattern EMBLEM_PIC = Pattern.compile("!Player (\\w+) Emblem");
    static final List<String> FIELDS = List.of("cost", "type", "pt", "text", "rarity", "color", "tags", "layout", "back", "related", "backface");
    static final Set<String> TYPE_WORDS = Set.of("Legendary", "Basic", "Snow", "World", "Flash", "Token", "Artifact", "Creature", "Enchantment",
        "Land", "Sorcery", "Instant", "Kindred", "Tribal", "Battle", "Planeswalker", "Emblem");

    /** A misspelt card type (a word before the dash that is no card type or supertype), or null. */
    static String typeProblem(final Entry e) {
        for (final String face : e.get("type").split(" // ")) {
            for (final String w : face.split(" - ")[0].trim().split(" ")) {
                if (!w.isEmpty() && !TYPE_WORDS.contains(w)) {
                    return "the type line \"" + e.get("type") + "\" has \"" + w + "\", which is no card type";
                }
            }
        }
        return null;
    }

    public static void main(final String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            usage();
            return;
        }
        final List<String> rest = Arrays.asList(args).subList(1, args.length);
        switch (args[0]) {
            case "import" -> doImport(rest);
            case "status" -> status();
            case "list" -> list(rest);
            case "show" -> show(rest);
            case "draft" -> draft(rest);
            case "check" -> System.exit(check() ? 0 : 1);
            case "verify" -> System.exit(verify(rest) ? 0 : 1);
            case "edition" -> writeEdition(true);
            default -> usage();
        }
    }

    static void usage() {
        System.out.println("""
            java tbld-pool/TbldPool.java <command>
              import [--no-pics]   read the card XML, token XML and pictures named in tbld-pool/sources.properties
              status               counts of scripted, out-of-date and unscripted cards
              list [filters]       filters: --todo --done --outdated --blocked --auto (todo, reference has the same text)
                                   --ref (todo with a reference script) --noref --tag T --color C --type T
                                   --text REGEX --name REGEX --pics (has a picture) --single (one face)
              show NAME...         pool entry, script, reference script and their texts side by side
              draft NAME...        write scripts from reference scripts (--auto: every --auto card; --limit N)
              check                every TBLD script matches its pool entry; exit code 1 if not
              verify [--list KIND] counts implemented, scripted-but-untested, placeholder and blocked cards
                                   (KIND: implemented, scripted, placeholder, blocked, or a sub-kind it prints)
              edition              rewrite forge-gui/res/editions/TumbledMTG.txt from the scripts""");
    }

    // ------------------------------------------------------------------------------------------------
    // Pool entries

    /** One card (or token) of the pool: its name without "TBLD " and its fields. */
    static final class Entry {
        final String name;
        final Map<String, String> f = new LinkedHashMap<>();

        Entry(final String name) {
            this.name = name;
        }

        String get(final String k) {
            return f.getOrDefault(k, "");
        }

        boolean isBasic() {
            return "Basic".equals(get("rarity")) && BASIC.matcher(name).matches();
        }

        /** Split, adventure and double-faced cards; their faces' texts are separated by "---" lines (as are a saga's chapters). */
        boolean multiFace() {
            return get("type").contains(" // ") || !get("layout").isEmpty();
        }
    }

    static Map<String, Entry> readPool(final Path file) throws IOException {
        final Map<String, Entry> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (!Files.exists(file)) {
            return out;
        }
        Entry cur = null;
        for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                cur = new Entry(line.substring(1, line.length() - 1));
                out.put(cur.name, cur);
            } else if (cur != null) {
                final int eq = line.indexOf('=');
                cur.f.put(line.substring(0, eq), unescape(line.substring(eq + 1)));
            }
        }
        return out;
    }

    static void writePool(final Path file, final String what, final Map<String, Entry> pool) throws IOException {
        final StringBuilder b = new StringBuilder();
        b.append("# TBLD ").append(what).append(", written by \"java tbld-pool/TbldPool.java import\" from Brett's Cockatrice card files.\n");
        b.append("# Do not edit: change the cards in Brett's files and import again.\n");
        for (final Entry e : pool.values()) {
            b.append('\n').append('[').append(e.name).append("]\n");
            for (final String k : FIELDS) {
                final String v = e.get(k);
                if (!v.isEmpty()) {
                    b.append(k).append('=').append(escape(v)).append('\n');
                }
            }
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
    }

    static String escape(final String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    static String unescape(final String s) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                final char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------------
    // import

    static Properties sources() throws IOException {
        final Properties p = new Properties();
        if (!Files.exists(SOURCES)) {
            throw new IllegalStateException(SOURCES + " is missing. Create it with three lines:\n"
                + "cards=<path of tumbled-mtg-cards.xml>\ntokens=<path of tokens.xml>\npictures=<folder of the card pictures>\n"
                + "(use forward slashes; the file is not committed)");
        }
        try (var r = Files.newBufferedReader(SOURCES, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    static void doImport(final List<String> args) throws Exception {
        final Properties src = sources();
        final List<String> warnings = new ArrayList<>();
        final Map<String, Entry> cards = parseXml(Paths.get(src.getProperty("cards")), false, warnings);
        final Map<String, Entry> tokens = parseXml(Paths.get(src.getProperty("tokens")), true, warnings);
        applyCorrections(cards, warnings);
        final Map<String, Entry> oldCards = readPool(CARDS_FILE);
        final Map<String, Entry> oldTokens = readPool(TOKENS_FILE);
        writePool(CARDS_FILE, "card pool", cards);
        writePool(TOKENS_FILE, "tokens, emblems and back faces", tokens);
        System.out.printf("Cards: %d (%d basic land versions). Tokens, emblems and back faces: %d.%n",
            cards.size(), cards.values().stream().filter(Entry::isBasic).count(), tokens.size());
        reportChanges("card", oldCards, cards);
        reportChanges("token", oldTokens, tokens);
        for (final Entry e : cards.values()) {
            if (typeProblem(e) != null) {
                warnings.add(e.name + ": " + typeProblem(e));
            }
        }
        warnings.forEach(w -> System.out.println("Warning: " + w));
        if (!args.contains("--no-pics")) {
            final List<Path> extra = Arrays.stream(src.getProperty("pictures.extra", "").split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Paths::get).toList();
            importPictures(Paths.get(src.getProperty("pictures")), extra, cards, tokens);
        }
        final Map<String, Script> scripts = readScripts();
        final List<String> outdated = new ArrayList<>();
        for (final Script s : scripts.values()) {
            final List<String> problems = compare(s, cards, tokens);
            if (!problems.isEmpty()) {
                outdated.add(s.poolName() + ": " + String.join("; ", problems));
            }
        }
        if (!outdated.isEmpty()) {
            System.out.println("Scripts that no longer match the pool (" + outdated.size() + "):");
            outdated.forEach(o -> System.out.println("  " + o));
        }
        writeEdition(false);
    }

    static void reportChanges(final String what, final Map<String, Entry> before, final Map<String, Entry> after) {
        if (before.isEmpty()) {
            return;
        }
        final List<String> added = new ArrayList<>(), removed = new ArrayList<>(), changed = new ArrayList<>();
        for (final Entry e : after.values()) {
            final Entry o = before.get(e.name);
            if (o == null) {
                added.add(e.name);
            } else {
                final List<String> fields = FIELDS.stream().filter(k -> !o.get(k).equals(e.get(k))).toList();
                if (!fields.isEmpty()) {
                    changed.add(e.name + " (" + String.join(", ", fields) + ")");
                }
            }
        }
        for (final String n : before.keySet()) {
            if (!after.containsKey(n)) {
                removed.add(n);
            }
        }
        System.out.printf("%s changes: %d added, %d removed, %d changed.%n", what, added.size(), removed.size(), changed.size());
        added.forEach(n -> System.out.println("  added " + n));
        removed.forEach(n -> System.out.println("  removed " + n));
        changed.forEach(n -> System.out.println("  changed " + n));
    }

    static final Pattern CARD = Pattern.compile("<card>(.*?)</card>", Pattern.DOTALL);

    static Map<String, Entry> parseXml(final Path file, final boolean tokens, final List<String> warnings) throws IOException {
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        xml = xml.replaceAll("(?s)<!--.*?-->", "");
        final Map<String, Entry> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Matcher m = CARD.matcher(xml);
        while (m.find()) {
            final String body = m.group(1);
            String name = field(body, "name").trim();
            final Matcher em = EMBLEM_PIC.matcher(name);
            if (em.matches()) {
                name = "Player " + em.group(1);
            } else if (name.startsWith("TBLD ")) {
                name = name.substring(5).trim();
            } else {
                warnings.add(file.getFileName() + ": skipped \"" + name + "\" (its name doesn't start with \"TBLD \")");
                continue;
            }
            final Entry e = new Entry(name);
            e.f.put("cost", field(body, "manacost").trim());
            e.f.put("type", normType(field(body, "type")));
            e.f.put("pt", field(body, "pt").trim());
            e.f.put("text", normText(field(body, "text")));
            e.f.put("rarity", field(body, "rarity").trim());
            e.f.put("color", field(body, "color").trim());
            e.f.put("tags", field(body, "tags").trim().replaceAll("\\s+", " "));
            e.f.put("layout", field(body, "layout").trim());
            final List<String> related = new ArrayList<>();
            final Matcher r = Pattern.compile("<(reverse-related|related)(\\s[^>]*)?>(.*?)</\\1>", Pattern.DOTALL).matcher(body);
            while (r.find()) {
                final String n = stripTbld(decode(r.group(3)).trim());
                if ("related".equals(r.group(1)) && r.group(2) != null && r.group(2).contains("attach") && !e.get("layout").isEmpty()) {
                    e.f.put("back", n);
                } else if (!related.contains(n)) {
                    related.add(n);
                }
            }
            e.f.put("related", String.join("|", related));
            if (tokens && body.contains("<dfc")) {
                e.f.put("backface", "1");
            }
            if (out.containsKey(name)) {
                final Entry first = out.get(name);
                final boolean same = FIELDS.stream().filter(k -> !"tags".equals(k)).allMatch(k -> first.get(k).equals(e.get(k)));
                warnings.add(file.getFileName() + ": \"" + name + "\" is in the file twice" + (same ? " (same card)" : " with different text")
                    + "; the first one is kept");
                continue;
            }
            out.put(name, e);
        }
        return out;
    }

    /**
     * Applies tbld-pool/corrections.txt: "Name | field=value" lines for cards whose picture and XML
     * disagree (the picture is right). The field is name (renames the card) or one of FIELDS.
     * A correction the XML no longer needs is reported, so it can be deleted.
     */
    static void applyCorrections(final Map<String, Entry> cards, final List<String> warnings) throws IOException {
        if (!Files.exists(CORRECTIONS)) {
            return;
        }
        for (final String line : Files.readAllLines(CORRECTIONS, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            final String[] p = line.split("\\|", 2);
            final String name = p[0].trim();
            final String[] kv = p.length < 2 ? new String[0] : p[1].trim().split("=", 2);
            if (kv.length < 2 || !("name".equals(kv[0].trim()) || FIELDS.contains(kv[0].trim()))) {
                warnings.add("corrections.txt: can't read \"" + line + "\"");
                continue;
            }
            final String field = kv[0].trim(), value = kv[1].trim();
            final Entry e = cards.get(name);
            if (e == null) {
                warnings.add("corrections.txt: \"" + name + "\" is not in the card XML (already fixed there?)");
            } else if ("name".equals(field)) {
                final Entry renamed = new Entry(value);
                renamed.f.putAll(e.f);
                cards.remove(name);
                cards.put(value, renamed);
            } else if (value.equals(e.get(field))) {
                warnings.add("corrections.txt: " + name + "'s " + field + " is already " + value + " in the card XML");
            } else {
                e.f.put(field, value);
            }
        }
    }

    static String stripTbld(final String n) {
        return n.startsWith("TBLD ") ? n.substring(5) : n;
    }

    static String field(final String body, final String tag) {
        final Matcher m = Pattern.compile("<" + tag + "(\\s[^>]*)?>(.*?)</" + tag + ">", Pattern.DOTALL).matcher(body);
        return m.find() ? decode(m.group(2)) : "";
    }

    static String decode(final String s) {
        final Matcher m = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);").matcher(s);
        final StringBuilder b = new StringBuilder();
        while (m.find()) {
            final String g = m.group(1);
            final String rep = switch (g) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> new String(Character.toChars(g.startsWith("#x") ? Integer.parseInt(g.substring(2), 16) : Integer.parseInt(g.substring(1))));
            };
            m.appendReplacement(b, Matcher.quoteReplacement(rep));
        }
        m.appendTail(b);
        return b.toString();
    }

    static String normType(final String t) {
        return t.replace('\t', ' ').replaceAll("\\s+[-—–]\\s+", " - ").replaceAll("\\s+", " ").trim();
    }

    static String normText(final String t) {
        final String[] lines = t.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        final StringBuilder b = new StringBuilder();
        for (final String l : lines) {
            b.append(l.stripTrailing()).append('\n');
        }
        return b.toString().strip();
    }

    // ------------------------------------------------------------------------------------------------
    // Pictures

    /** Recorded with each picture's source hash: changing how pictures are made converts them all again. */
    static final String PICTURE_FORMAT = "/jpeg90-375";

    /**
     * Pictures are stored as JPEG files (quality 0.9, at most 375 x 523) to keep the repository small.
     * The folders in "pictures.extra" only fill gaps: a card or back face with no picture in the main
     * folder. There, a picture wider than tall of a double-faced card shows both faces side by side; its
     * left half is the card's picture and its right half its back face's.
     */
    static void importPictures(final Path dir, final List<Path> extraDirs, final Map<String, Entry> cards, final Map<String, Entry> tokens)
            throws Exception {
        final Map<String, String> manifest = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (Files.exists(PIC_MANIFEST)) {
            for (final String l : Files.readAllLines(PIC_MANIFEST, StandardCharsets.UTF_8)) {
                final String[] p = l.split("\t");
                if (p.length == 3) {
                    manifest.put(p[0], p[2]);
                }
            }
        }
        final Map<String, String> wanted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // target file -> source file name
        final Map<String, Path> sourceOf = new ConcurrentHashMap<>();
        final Map<String, Integer> halfOf = new ConcurrentHashMap<>(); // target -> 1 left half, 2 right half of its source
        final List<String> unmatched = new ArrayList<>();
        final Map<String, String> byKey = new TreeMap<>();
        Stream.concat(cards.keySet().stream(), tokens.keySet().stream()).forEach(n -> byKey.putIfAbsent(key(n), n));
        for (final Path f : pictureFiles(dir)) {
            final String name = pictureName(f, cards, tokens, byKey);
            if (name == null) {
                unmatched.add(f.getFileName().toString());
                continue;
            }
            final String target = "TBLD " + name + ".jpg";
            if (wanted.containsKey(target)) {
                unmatched.add(f.getFileName().toString() + " (another file has the same name)");
                continue;
            }
            wanted.put(target, f.getFileName().toString());
            sourceOf.put(target, f);
        }
        int filled = 0;
        for (final Path d : extraDirs) {
            for (final Path f : pictureFiles(d)) {
                final String name = pictureName(f, cards, tokens, byKey);
                if (name == null) {
                    continue;
                }
                final String source = d.getFileName() + "/" + f.getFileName();
                final String target = "TBLD " + name + ".jpg";
                final String back = cards.containsKey(name) ? cards.get(name).get("back") : "";
                final String backTarget = "TBLD " + back + ".jpg";
                final boolean bothFaces = !back.isEmpty() && tokens.containsKey(back)
                    && !(wanted.containsKey(target) && wanted.containsKey(backTarget)) && isWide(f);
                if (bothFaces) {
                    for (final String t : List.of(target, backTarget)) {
                        if (!wanted.containsKey(t)) {
                            wanted.put(t, source);
                            sourceOf.put(t, f);
                            halfOf.put(t, t.equals(target) ? 1 : 2);
                            filled++;
                        }
                    }
                } else if (!wanted.containsKey(target)) {
                    wanted.put(target, source);
                    sourceOf.put(target, f);
                    filled++;
                }
            }
        }
        Files.createDirectories(PICS);
        final Map<String, String> newManifest = new ConcurrentHashMap<>();
        final AtomicInteger converted = new AtomicInteger();
        wanted.keySet().parallelStream().forEach(target -> {
            try {
                final Path src = sourceOf.get(target);
                final int half = halfOf.getOrDefault(target, 0);
                final String hash = sha1(src) + PICTURE_FORMAT + (half == 1 ? "/left" : half == 2 ? "/right" : "");
                final Path out = PICS.resolve(target);
                if (!hash.equals(manifest.get(target)) || !Files.exists(out)) {
                    toJpeg(src, out, half);
                    converted.incrementAndGet();
                }
                newManifest.put(target, hash);
                Files.deleteIfExists(PICS.resolve(target.replaceFirst("\\.jpg$", ".png")));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        int removed = 0;
        for (final String old : manifest.keySet()) {
            if (!newManifest.containsKey(old)) {
                Files.deleteIfExists(PICS.resolve(old));
                removed++;
            }
        }
        final StringBuilder b = new StringBuilder();
        for (final String t : new TreeSet<>(newManifest.keySet())) {
            b.append(t).append('\t').append(wanted.get(t)).append('\t').append(newManifest.get(t)).append('\n');
        }
        Files.writeString(PIC_MANIFEST, b.toString(), StandardCharsets.UTF_8);
        System.out.printf("Pictures: %d in the repo (%d from pictures.extra), %d converted now, %d removed. %d files in the picture folder match no card:%n",
            newManifest.size(), filled, converted.get(), removed, unmatched.size());
        System.out.println("  " + String.join(", ", unmatched));
        final List<String> missing = cards.values().stream().filter(e -> !e.isBasic() && !newManifest.containsKey("TBLD " + e.name + ".jpg")).map(e -> e.name).toList();
        System.out.printf("%d cards (not counting basic lands) have no picture.%n", missing.size());
    }

    static List<Path> pictureFiles(final Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g)$")).sorted().toList();
        }
    }

    /** The card or token a picture file shows, or null. */
    static String pictureName(final Path f, final Map<String, Entry> cards, final Map<String, Entry> tokens, final Map<String, String> byKey) {
        final String base = f.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        final Matcher em = EMBLEM_PIC.matcher(base);
        String name = em.matches() ? "Player " + em.group(1) : base.startsWith("TBLD ") ? base.substring(5) : null;
        if (name != null && !(cards.containsKey(name) || tokens.containsKey(name))) {
            name = byKey.get(key(name)); // "TBLD Séance.png" is the picture of Seance
        }
        return name != null && (cards.containsKey(name) || tokens.containsKey(name)) ? name : null;
    }

    static boolean isWide(final Path f) throws IOException {
        final BufferedImage in = ImageIO.read(f.toFile());
        return in != null && in.getWidth() > in.getHeight();
    }

    static String sha1(final Path p) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(p))).substring(0, 16);
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** half: 0 the whole picture, 1 its left half, 2 its right half. */
    static void toJpeg(final Path src, final Path out, final int half) throws IOException {
        BufferedImage in = ImageIO.read(src.toFile());
        if (in == null) {
            throw new IOException("not a picture: " + src);
        }
        if (half != 0) {
            in = in.getSubimage(half == 2 ? in.getWidth() / 2 : 0, 0, in.getWidth() / 2, in.getHeight());
        }
        // Large pictures are scaled down to the usual 375 x 523 (the client shows cards at most 300 pixels wide)
        final double scale = Math.min(1.0, Math.min(375.0 / Math.min(in.getWidth(), in.getHeight()), 523.0 / Math.max(in.getWidth(), in.getHeight())));
        final int w0 = (int) Math.round(in.getWidth() * scale), h0 = (int) Math.round(in.getHeight() * scale);
        final BufferedImage rgb = new BufferedImage(w0, h0, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w0, h0);
        g.drawImage(in, 0, 0, w0, h0, null);
        g.dispose();
        final ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        final ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.9f);
        final Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(rgb, null, null), p);
        } finally {
            w.dispose();
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------------------------------------
    // Scripts

    /** A card script: its faces' key/value lines, in order. */
    record Script(Path file, List<Map<String, String>> faces, List<List<String>> lines) {
        String front(final String k) {
            return faces.get(0).getOrDefault(k, null);
        }

        boolean split() {
            return "Split".equals(front("AlternateMode"));
        }

        /** The pool name this script is for: a split card's is "Fire_Ice". */
        String poolName() {
            if (split() && faces.size() > 1) {
                return front("Name") + "_" + faces.get(1).get("Name");
            }
            return front("Name");
        }
    }

    static Script readScript(final Path f) throws IOException {
        final List<Map<String, String>> faces = new ArrayList<>();
        final List<List<String>> lines = new ArrayList<>();
        Map<String, String> cur = new LinkedHashMap<>();
        List<String> curLines = new ArrayList<>();
        for (final String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (l.trim().equals("ALTERNATE")) {
                faces.add(cur);
                lines.add(curLines);
                cur = new LinkedHashMap<>();
                curLines = new ArrayList<>();
                continue;
            }
            curLines.add(l);
            final int c = l.indexOf(':');
            if (c > 0) {
                final String k = l.substring(0, c);
                if (Set.of("Name", "ManaCost", "Types", "PT", "Oracle", "AlternateMode", "Loyalty", "Defense").contains(k)) {
                    cur.putIfAbsent(k, l.substring(c + 1));
                }
            }
        }
        faces.add(cur);
        lines.add(curLines);
        return new Script(f, faces, lines);
    }

    /** TBLD scripts by pool name ("Fire_Ice" for a split card; "Mountain" for the basic land). */
    static Map<String, Script> readScripts() throws IOException {
        final Map<String, Script> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (!Files.isDirectory(SCRIPTS)) {
            return out;
        }
        try (Stream<Path> s = Files.list(SCRIPTS)) {
            for (final Path f : s.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
                final Script sc = readScript(f);
                if (sc.front("Name") != null) {
                    out.put(sc.poolName(), sc);
                }
            }
        }
        return out;
    }

    static String scriptFileName(final String poolName) {
        return "tbld_" + poolName.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "") + ".txt";
    }

    // ------------------------------------------------------------------------------------------------
    // Pool entry -> what its script must say

    static final Pattern COST_TOKEN = Pattern.compile("([WUBRGC2H])/([WUBRGP])|(\\d+)|([WUBRGCXYZSH])");

    /** Brett's mana cost ("1W/B", "2/W2/W", "H/U") as a script's ManaCost ("1 WB", "2W 2W", "UP"); null if Forge has no such cost. */
    static String forgeCost(final String cost) {
        final String c = cost.replace(" ", "");
        if (c.isEmpty()) {
            return "no cost";
        }
        final Matcher m = COST_TOKEN.matcher(c);
        final List<String> out = new ArrayList<>();
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) {
                return null;
            }
            pos = m.end();
            if (m.group(1) != null) {
                out.add(m.group(1).equals("H") ? m.group(2) + "P" : m.group(1) + m.group(2));
            } else if (m.group(3) != null) {
                out.add(m.group(3));
            } else if (m.group(4).equals("H")) {
                return null; // generic Phyrexian mana: no such shard in Forge
            } else {
                out.add(m.group(4));
            }
        }
        return pos == c.length() ? String.join(" ", out) : null;
    }

    static String forgeType(final String type) {
        return type.replace(" - ", " ").trim();
    }

    /** What one face of a script must say; a null value isn't checked. */
    record Face(String name, String cost, String types, String pt, String oracle) {
    }

    static List<Face> expectedFaces(final Entry e, final Map<String, Entry> tokens) {
        final String[] types = e.get("type").split(" // ");
        final String[] costs = e.get("cost").isEmpty() ? new String[] {""} : e.get("cost").split("\\s*//\\s*");
        final String[] texts = e.multiFace() ? e.get("text").split("\n---\n|\n---$", -1) : new String[] {e.get("text")};
        final Entry back = e.get("back").isEmpty() ? null : tokens.get(e.get("back"));
        int n = Math.max(types.length, texts.length);
        if (back != null) {
            n = Math.max(n, 2);
        }
        final String[] names = new String[n];
        names[0] = e.name;
        if (n == 2 && e.name.contains("_")) {
            final String[] p = e.name.split("_", 2);
            names[0] = p[0];
            names[1] = p[1];
        } else if (n == 2 && back != null) {
            names[1] = back.name;
        }
        final List<Face> faces = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String type = i < types.length ? types[i] : null;
            String pt = i == 0 ? e.get("pt") : "";
            if (i == 1 && back != null && types.length < 2) {
                type = back.get("type");
            }
            if (i == 1 && back != null) {
                pt = back.get("pt");
            }
            if (i > 0 && types.length > 1 && i < types.length && e.get("pt").contains(" // ")) {
                pt = e.get("pt").split(" // ")[i];
            }
            final String cost = i < costs.length ? forgeCost(costs[i]) : null;
            final String text = i < texts.length ? texts[i].strip() : null;
            faces.add(new Face(names[i], cost, type == null ? null : forgeType(type), pt, text == null ? null : escape(text)));
        }
        return faces;
    }

    /** How a script differs from its pool entry; empty if it matches. */
    static List<String> compare(final Script s, final Map<String, Entry> cards, final Map<String, Entry> tokens) {
        final List<String> out = new ArrayList<>();
        final String pn = s.poolName();
        final Matcher bm = BASIC.matcher(pn);
        if (bm.matches() && bm.group(2).isEmpty()) {
            final List<Entry> versions = basicVersions(cards, pn);
            if (versions.isEmpty()) {
                out.add("no TBLD " + pn + " in the pool");
                return out;
            }
            final Entry e = versions.get(0);
            diff(out, "Types", forgeType(e.get("type")), s.front("Types"));
            diff(out, "Oracle", escape(e.get("text")), s.front("Oracle"));
            return out;
        }
        final Entry e = cards.get(pn);
        if (e == null) {
            out.add("not in the pool");
            return out;
        }
        final List<Face> faces = expectedFaces(e, tokens);
        if (faces.size() != s.faces().size()) {
            out.add("the pool card has " + faces.size() + " face(s), the script " + s.faces().size());
            return out;
        }
        for (int i = 0; i < faces.size(); i++) {
            final Face f = faces.get(i);
            final Map<String, String> sf = s.faces().get(i);
            final String p = faces.size() > 1 ? "face " + (i + 1) + " " : "";
            if (f.name() != null) {
                diff(out, p + "Name", f.name(), sf.get("Name"));
            }
            if (f.cost() != null) {
                diff(out, p + "ManaCost", f.cost(), sf.get("ManaCost"));
            }
            if (f.types() != null) {
                diff(out, p + "Types", f.types(), sf.get("Types"));
            }
            if (f.pt() != null && !(f.pt().isEmpty() && sf.get("PT") == null)) {
                diff(out, p + "PT", f.pt(), sf.get("PT"));
            }
            if (f.oracle() != null) {
                diff(out, p + "Oracle", f.oracle(), sf.get("Oracle"));
            }
        }
        return out;
    }

    static void diff(final List<String> out, final String what, final String want, final String have) {
        final String h = have == null ? "" : have.strip();
        if (!want.strip().equals(h)) {
            out.add(what + " should be \"" + want + "\" (is \"" + h + "\")");
        }
    }

    static List<Entry> basicVersions(final Map<String, Entry> cards, final String basic) {
        final List<Entry> out = new ArrayList<>();
        for (final Entry e : cards.values()) {
            final Matcher m = BASIC.matcher(e.name);
            if (e.isBasic() && m.matches() && m.group(1).equals(basic)) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Integer.compare(version(a.name), version(b.name)));
        return out;
    }

    static int version(final String basicName) {
        final Matcher m = BASIC.matcher(basicName);
        return m.matches() && !m.group(2).isEmpty() ? Integer.parseInt(m.group(2)) : 1;
    }

    // ------------------------------------------------------------------------------------------------
    // Reference scripts (official Forge cards)

    record Ref(String name, Path file, String oracle) {
    }

    static Map<String, Ref> refIndex;

    /** Official scripts by a key made of the name's letters and digits (Brett's names drop apostrophes and commas). */
    static Map<String, Ref> refs() throws IOException {
        if (refIndex != null) {
            return refIndex;
        }
        refIndex = new ConcurrentHashMap<>();
        for (final Path root : List.of(REFERENCE, CARDSFOLDER)) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(root)) {
                s.parallel().filter(p -> p.toString().endsWith(".txt") && !p.startsWith(SCRIPTS)).forEach(p -> {
                    try {
                        String name = null, oracle = null;
                        for (final String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                            if (l.trim().equals("ALTERNATE")) {
                                break;
                            }
                            if (name == null && l.startsWith("Name:")) {
                                name = l.substring(5);
                            } else if (oracle == null && l.startsWith("Oracle:")) {
                                oracle = l.substring(7);
                            }
                        }
                        if (name != null) {
                            refIndex.putIfAbsent(key(name), new Ref(name, p, oracle == null ? "" : oracle));
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        return refIndex;
    }

    static String key(final String name) {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static Ref ref(final Entry e) throws IOException {
        if (e.multiFace()) {
            final String n = e.name.replace("_", " // ");
            return refs().get(key(e.name.contains("_") ? e.name.split("_")[0] : n));
        }
        return refs().get(key(e.name));
    }

    /** Official Oracle text in Brett's templating, as far as it can be done mechanically; for spotting reprints. */
    static String brettify(final String oracle, final String name) {
        String t = oracle.replace("\\n", "\n");
        final String shortName = name.split(",")[0];
        t = t.replace(name, "this");
        if (shortName.length() > 3) {
            t = t.replace(shortName, "this");
        }
        t = t.replaceAll("\\{([0-9WUBRGCXSE]+)\\}", "$1").replaceAll("\\{([WUBRG2])/([WUBRGP])\\}", "$1/$2").replace("{T}", "T").replace("{Q}", "Q");
        t = t.replace("onto the battlefield", "into play").replace("enters the battlefield", "enters").replace("on the battlefield", "in play")
            .replace("the battlefield", "play");
        t = t.replaceAll("\\byou gain\\b", "gain").replaceAll("\\bYou gain\\b", "Gain");
        t = t.replace("instant or sorcery", "sorcery").replace("instant and sorcery", "sorcery").replace("instant and/or sorcery", "sorcery")
            .replace("Instant", "Flash sorcery").replace("instant", "flash sorcery");
        // Brett's templating: "this turn", "at your upkeep", "at the next end step", no planeswalkers
        t = t.replace("until end of turn", "this turn").replace("Until end of turn", "This turn");
        t = t.replaceAll("(?i)at the beginning of combat on your turn", "at your beginning of combat step");
        t = t.replaceAll("(?i)at the beginning of (your|each opponent's|each player's|each|the next|the|that player's|enchanted player's) ", "at $1 ");
        t = t.replace(" or planeswalker", "").replace(", or planeswalker", "").replace(" and/or planeswalker", "").replace(" or battle", "");
        t = t.replace("Activate only as a sorcery.", "Can't be activated at flash speed.");
        return normCompare(t);
    }

    static String normCompare(final String t) {
        return t.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("[’']", "'").replaceAll("[“”]", "\"").replaceAll("[—–]", "-")
            .replaceAll("(?i)\\bthis (card|creature|spell|land|artifact|enchantment|permanent|aura|equipment)\\b", "this")
            .replaceAll("\\ban (sorcery|flash)", "a $1").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT).replaceAll("[.,]", "")
            .replace("owner's", "owners").replaceAll("(\\d+/\\d+ [a-z ]+?) (creature )?tokens?\\b", "$1 token")
            .replace("at at ", "at ");
    }

    static boolean sameText(final Entry e, final Ref r) {
        return brettify(r.oracle(), r.name()).equals(normCompare(e.get("text")));
    }

    // ------------------------------------------------------------------------------------------------
    // status, list, show

    static final class Row {
        Entry e;
        String state; // done, outdated, todo
        Ref ref;
        boolean auto;
        List<String> problems = List.of();
    }

    static Map<String, String> blockedCards;

    /**
     * Cards that can't be scripted yet, with the reason (a TBLD mechanic the engine lacks, or a problem in
     * Brett's card data): tbld-pool/blocked.txt, one "Name | reason" per line.
     */
    static Map<String, String> blocked() throws IOException {
        if (blockedCards == null) {
            blockedCards = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            if (Files.exists(BLOCKED)) {
                for (final String l : Files.readAllLines(BLOCKED, StandardCharsets.UTF_8)) {
                    final int bar = l.indexOf('|');
                    if (!l.startsWith("#") && bar > 0) {
                        blockedCards.put(l.substring(0, bar).trim(), l.substring(bar + 1).trim());
                    }
                }
            }
        }
        return blockedCards;
    }

    static List<Row> rows(final Map<String, Entry> cards, final Map<String, Entry> tokens, final boolean withRefs) throws IOException {
        final Map<String, Script> scripts = readScripts();
        final List<Row> out = new ArrayList<>();
        final Set<String> basicsDone = new TreeSet<>();
        for (final Entry e : cards.values()) {
            final Row r = new Row();
            r.e = e;
            Script s = scripts.get(e.name);
            if (e.isBasic()) {
                final String b = BASIC.matcher(e.name).replaceFirst("$1");
                s = scripts.get(b);
                if (!basicsDone.add(b + version(e.name))) {
                    continue;
                }
            }
            if (s == null && blocked().containsKey(e.name)) {
                r.state = "blocked";
                r.problems = List.of(blocked().get(e.name));
            } else if (s == null) {
                r.state = "todo";
                if (withRefs) {
                    r.ref = ref(e);
                    r.auto = r.ref != null && !e.multiFace() && typeProblem(e) == null && sameText(e, r.ref);
                }
            } else {
                r.problems = compare(s, cards, tokens);
                r.state = r.problems.isEmpty() ? "done" : "outdated";
            }
            out.add(r);
        }
        return out;
    }

    static void status() throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE), tokens = readPool(TOKENS_FILE);
        final List<Row> rows = rows(cards, tokens, true);
        final List<Row> nonBasic = rows.stream().filter(r -> !r.e.isBasic()).toList();
        final long done = nonBasic.stream().filter(r -> r.state.equals("done")).count();
        final long out = nonBasic.stream().filter(r -> r.state.equals("outdated")).count();
        final long todo = nonBasic.stream().filter(r -> r.state.equals("todo")).count();
        final long auto = nonBasic.stream().filter(r -> r.auto).count();
        final long ref = nonBasic.stream().filter(r -> r.state.equals("todo") && r.ref != null).count();
        System.out.printf("Cards in the pool (not counting basic land versions): %d%n", nonBasic.size());
        final long blockedCount = nonBasic.stream().filter(r -> r.state.equals("blocked")).count();
        System.out.printf("  scripted and matching the pool: %d%n  scripted but out of date: %d%n  not scripted: %d"
            + " (%d with an official reference script, %d of them with the same text)%n  blocked (tbld-pool/blocked.txt): %d%n",
            done, out, todo, ref, auto, blockedCount);
        final Map<String, String> basics = new TreeMap<>();
        for (final Row r : rows) {
            if (r.e.isBasic()) {
                basics.merge(BASIC.matcher(r.e.name).replaceFirst("$1"), r.state, (a, b) -> a);
            }
        }
        System.out.println("  basic lands: " + basics);
        nonBasic.stream().filter(r -> r.state.equals("outdated")).forEach(r -> System.out.println("  out of date: " + r.e.name + ": " + String.join("; ", r.problems)));
    }

    static void list(final List<String> args) throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE), tokens = readPool(TOKENS_FILE);
        final Set<String> pics = picFiles();
        final List<Row> rows = rows(cards, tokens, true);
        int n = 0;
        for (final Row r : rows) {
            if (r.e.isBasic() || !matches(r, args, pics)) {
                continue;
            }
            n++;
            System.out.printf("%-34s %-12s %-38s %-8s %s%n", r.e.name, r.e.get("cost"), r.e.get("type") + (r.e.get("pt").isEmpty() ? "" : " " + r.e.get("pt")),
                r.state, r.state.equals("blocked") ? r.problems.get(0) : r.ref == null ? "" : (r.auto ? "auto " : "ref ") + r.ref.name());
        }
        System.out.println(n + " card(s)");
    }

    static Set<String> picFiles() throws IOException {
        if (!Files.isDirectory(PICS)) {
            return Set.of();
        }
        try (Stream<Path> s = Files.list(PICS)) {
            return s.map(p -> p.getFileName().toString().replaceFirst("\\.[^.]+$", "")).collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        }
    }

    static boolean matches(final Row r, final List<String> args, final Set<String> pics) {
        for (int i = 0; i < args.size(); i++) {
            final String a = args.get(i);
            final String v = i + 1 < args.size() ? args.get(i + 1) : "";
            final boolean ok = switch (a) {
                case "--todo" -> r.state.equals("todo");
                case "--blocked" -> r.state.equals("blocked");
                case "--done" -> r.state.equals("done");
                case "--outdated" -> r.state.equals("outdated");
                case "--auto" -> r.auto;
                case "--ref" -> r.state.equals("todo") && r.ref != null;
                case "--noref" -> r.state.equals("todo") && r.ref == null;
                case "--single" -> !r.e.multiFace();
                case "--pics" -> pics.contains("TBLD " + r.e.name);
                case "--tag" -> (" " + r.e.get("tags") + " ").contains(" " + v.toUpperCase(Locale.ROOT) + " ");
                case "--color" -> colorsOf(r.e).equals(v.toUpperCase(Locale.ROOT));
                case "--type" -> r.e.get("type").toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT));
                case "--text" -> Pattern.compile(v, Pattern.CASE_INSENSITIVE).matcher(r.e.get("text")).find();
                case "--name" -> Pattern.compile(v, Pattern.CASE_INSENSITIVE).matcher(r.e.name).find();
                default -> true;
            };
            if (!ok) {
                return false;
            }
            if (Set.of("--tag", "--color", "--type", "--text", "--name").contains(a)) {
                i++;
            }
        }
        return true;
    }

    /** The colors of a card's mana cost, in WUBRG order ("C" for none). */
    static String colorsOf(final Entry e) {
        final StringBuilder b = new StringBuilder();
        for (final char c : "WUBRG".toCharArray()) {
            if (e.get("cost").indexOf(c) >= 0) {
                b.append(c);
            }
        }
        return b.length() == 0 ? "C" : b.toString();
    }

    static void show(final List<String> names) throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE), tokens = readPool(TOKENS_FILE);
        final Map<String, Script> scripts = readScripts();
        for (final String n : names) {
            final Entry e = cards.containsKey(n) ? cards.get(n) : tokens.get(n);
            if (e == null) {
                System.out.println(n + ": not in the pool");
                continue;
            }
            System.out.println("== " + e.name);
            for (final String k : FIELDS) {
                if (!e.get(k).isEmpty()) {
                    System.out.println(k + ": " + e.get(k).replace("\n", "\n      "));
                }
            }
            final Script s = scripts.get(e.name);
            if (s != null) {
                System.out.println("-- script " + ROOT.relativize(s.file()));
                final List<String> p = compare(s, cards, tokens);
                System.out.println(p.isEmpty() ? "   matches the pool" : "   differs: " + String.join("; ", p));
            }
            final Ref r = ref(e);
            if (r != null) {
                System.out.println("-- reference " + ROOT.relativize(r.file()) + (sameText(e, r) ? " (same text)" : ""));
                System.out.println("   official: " + r.oracle().replace("\\n", "\n             "));
                System.out.println("   as Brett: " + brettify(r.oracle(), r.name()));
                System.out.println("   pool:     " + normCompare(e.get("text")));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // draft

    static void draft(final List<String> args) throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE), tokens = readPool(TOKENS_FILE);
        final boolean force = args.contains("--force");
        final List<String> names = new ArrayList<>();
        int limit = Integer.MAX_VALUE;
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals("--limit")) {
                limit = Integer.parseInt(args.get(++i));
            } else if (args.get(i).equals("--auto")) {
                rows(cards, tokens, true).stream().filter(r -> r.auto).forEach(r -> names.add(r.e.name));
            } else if (!args.get(i).startsWith("--")) {
                names.add(args.get(i));
            }
        }
        int written = 0;
        for (final String n : names) {
            if (written >= limit) {
                break;
            }
            final Entry e = cards.get(n);
            if (e == null) {
                System.out.println(n + ": not in the pool");
                continue;
            }
            final Path out = SCRIPTS.resolve(scriptFileName(e.name));
            if (Files.exists(out) && !force) {
                System.out.println(n + ": " + ROOT.relativize(out) + " exists (--force overwrites it)");
                continue;
            }
            if (e.multiFace()) {
                System.out.println(n + ": has several faces; write it by hand");
                continue;
            }
            if (typeProblem(e) != null) {
                System.out.println(n + ": " + typeProblem(e) + "; Brett must fix the card first");
                continue;
            }
            final Ref r = ref(e);
            if (r == null) {
                System.out.println(n + ": no reference script; write it by hand");
                continue;
            }
            Files.createDirectories(SCRIPTS);
            Files.writeString(out, String.join("\n", draftLines(e, r)) + "\n", StandardCharsets.UTF_8);
            written++;
            final boolean same = sameText(e, r);
            System.out.println("wrote " + ROOT.relativize(out) + " from " + ROOT.relativize(r.file()) + (same ? " (same text)" : " (TEXT DIFFERS: edit it)"));
            if (!same) {
                System.out.println("   official: " + brettify(r.oracle(), r.name()));
                System.out.println("   pool:     " + normCompare(e.get("text")));
            }
            if (r.name().equals(e.name) && r.file().startsWith(CARDSFOLDER)) {
                final Path moved = REFERENCE.resolve(CARDSFOLDER.relativize(r.file()));
                Files.createDirectories(moved.getParent());
                Files.move(r.file(), moved);
                System.out.println("   moved the official script to " + ROOT.relativize(moved) + " (same name)");
                refIndex = null;
            }
        }
        writeEdition(false);
    }

    static List<String> draftLines(final Entry e, final Ref r) throws IOException {
        final List<String> in = Files.readAllLines(r.file(), StandardCharsets.UTF_8);
        final List<String> out = new ArrayList<>();
        final Face f = expectedFaces(e, Map.of()).get(0);
        final boolean flash = (" " + f.types() + " ").contains(" Flash ");
        boolean ptDone = false;
        for (final String l : in) {
            if (l.startsWith("Name:")) {
                out.add("Name:" + e.name);
            } else if (l.startsWith("ManaCost:")) {
                out.add("ManaCost:" + (f.cost() == null ? "no cost" : f.cost()));
            } else if (l.startsWith("Types:")) {
                out.add("Types:" + f.types());
                if (!e.get("pt").isEmpty()) {
                    out.add("PT:" + e.get("pt"));
                    ptDone = true;
                }
            } else if (l.startsWith("PT:")) {
                if (!ptDone && !e.get("pt").isEmpty()) {
                    out.add("PT:" + e.get("pt"));
                }
            } else if (l.startsWith("Oracle:")) {
                out.add("Oracle:" + f.oracle());
            } else if (flash && l.trim().equals("K:Flash")) {
                continue;
            } else if (l.startsWith("SVar:Picture")) {
                continue;
            } else {
                out.add(r.name().equals(e.name) ? l : l.replace(r.name(), e.name));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // verify: implemented, scripted but untested, or placeholder

    static final Path VERIFIED = TOOL.resolve("verified.txt");

    /** Cards whose abilities an automated in-game test plays (tbld-pool/verified.txt, one name per line). */
    static Set<String> verified() throws IOException {
        final Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (Files.exists(VERIFIED)) {
            for (final String l : Files.readAllLines(VERIFIED, StandardCharsets.UTF_8)) {
                if (!l.isBlank() && !l.startsWith("#")) {
                    out.add(l.trim());
                }
            }
        }
        return out;
    }

    /** The paragraphs of a card's text that need an ability: reminder text, mode bullets and blank lines left out. */
    static int abilityParagraphs(final String text) {
        int n = 0;
        for (final String p : text.split("\n")) {
            final String t = p.replaceAll("\\([^)]*\\)", "").trim();
            if (!t.isEmpty() && !t.startsWith("•") && !t.startsWith("- ")) {
                n++;
            }
        }
        return n;
    }

    /** The script's ability lines (A:, T:, S:, R: and K:) on all its faces. */
    static int abilityLines(final Script s) {
        int n = 0;
        for (final List<String> face : s.lines()) {
            for (final String l : face) {
                if (l.matches("^[ATSRK]:.*")) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Sorts every non-basic pool card into one of these, and prints the counts (and with --list KIND
     * the names):
     * implemented - its abilities are played by an automated in-game test (verified.txt), or it has no
     *               abilities at all (a vanilla card: name, cost, type and stats, which check compares);
     * scripted    - a script matching the pool, with ability lines for its text, that no test plays yet
     *               ("same-text": copied from the official card with the same text; "adapted": edited by
     *               hand; "fewer-lines": fewer ability lines than paragraphs of text, worth a look);
     * placeholder - no working abilities: no script, an out-of-date script, or a script with no ability lines;
     * blocked     - waits for a TBLD mechanic or a fix in Brett's data (blocked.txt).
     */
    static boolean verify(final List<String> args) throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE), tokens = readPool(TOKENS_FILE);
        final Map<String, Script> scripts = readScripts();
        final Set<String> verified = verified();
        final Map<String, List<String>> kinds = new LinkedHashMap<>();
        for (final String k : List.of("implemented-tested", "implemented-vanilla", "scripted-same-text", "scripted-adapted",
                "scripted-fewer-lines", "placeholder-no-script", "placeholder-outdated", "placeholder-no-abilities", "blocked")) {
            kinds.put(k, new ArrayList<>());
        }
        boolean ok = true;
        for (final Row r : rows(cards, tokens, false)) {
            if (r.e.isBasic()) {
                continue;
            }
            final String name = r.e.name;
            final String kind;
            if (r.state.equals("blocked")) {
                kind = "blocked";
            } else if (r.state.equals("todo")) {
                kind = "placeholder-no-script";
            } else if (r.state.equals("outdated")) {
                kind = "placeholder-outdated";
            } else {
                final Script s = scripts.get(name);
                // An instant or sorcery is usually one spell ability whose sub-abilities do each sentence
                final boolean spell = r.e.get("type").matches("(?i).*\\b(instant|sorcery)\\b.*");
                final int paragraphs = spell ? Math.min(1, abilityParagraphs(r.e.get("text"))) : abilityParagraphs(r.e.get("text"));
                final int lines = abilityLines(s);
                if (paragraphs > 0 && lines == 0) {
                    kind = "placeholder-no-abilities";
                } else if (verified.contains(name)) {
                    kind = "implemented-tested";
                } else if (paragraphs == 0) {
                    kind = "implemented-vanilla";
                } else if (lines < paragraphs) {
                    kind = "scripted-fewer-lines";
                } else {
                    final Ref ref = ref(r.e);
                    kind = ref != null && sameText(r.e, ref) ? "scripted-same-text" : "scripted-adapted";
                }
            }
            kinds.get(kind).add(name);
        }
        for (final String v : verified) {
            if (!kinds.get("implemented-tested").contains(v)) {
                System.out.println("verified.txt names " + v + ", which is not a scripted card matching the pool");
                ok = false;
            }
        }
        final int total = kinds.values().stream().mapToInt(List::size).sum();
        final int implemented = kinds.get("implemented-tested").size() + kinds.get("implemented-vanilla").size();
        final int scripted = kinds.get("scripted-same-text").size() + kinds.get("scripted-adapted").size() + kinds.get("scripted-fewer-lines").size();
        final int placeholder = kinds.get("placeholder-no-script").size() + kinds.get("placeholder-outdated").size()
                + kinds.get("placeholder-no-abilities").size();
        System.out.printf("Cards in the pool (not counting basic land versions): %d%n", total);
        System.out.printf("  implemented: %d (%d with abilities played by an in-game test, %d with no abilities)%n",
                implemented, kinds.get("implemented-tested").size(), kinds.get("implemented-vanilla").size());
        System.out.printf("  scripted, not yet tested in game: %d (%d copied from the official card with the same text, %d adapted by hand,"
                + " %d possibly incomplete: fewer ability lines than paragraphs of text)%n", scripted, kinds.get("scripted-same-text").size(),
                kinds.get("scripted-adapted").size(), kinds.get("scripted-fewer-lines").size());
        System.out.printf("  placeholder: %d (%d with no script, %d out of date, %d with a script but no abilities)%n", placeholder,
                kinds.get("placeholder-no-script").size(), kinds.get("placeholder-outdated").size(), kinds.get("placeholder-no-abilities").size());
        System.out.printf("  blocked: %d%n", kinds.get("blocked").size());
        final int at = args.indexOf("--list");
        if (at >= 0 && at + 1 < args.size()) {
            final String want = args.get(at + 1);
            kinds.forEach((k, names) -> {
                if (k.startsWith(want)) {
                    names.forEach(n -> System.out.println(k + "\t" + n));
                }
            });
        }
        return ok;
    }

    // ------------------------------------------------------------------------------------------------
    // check and edition

    static boolean check() throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE), tokens = readPool(TOKENS_FILE);
        if (cards.isEmpty()) {
            System.out.println("The pool is empty: run \"java tbld-pool/TbldPool.java import\" first.");
            return false;
        }
        final List<String> problems = new ArrayList<>();
        final Map<String, Script> scripts = readScripts();
        final Set<String> officialNames = officialNames();
        for (final Script s : scripts.values()) {
            final String file = SCRIPTS.relativize(s.file()).toString();
            compare(s, cards, tokens).forEach(p -> problems.add(file + ": " + p));
            if (!file.equals(scriptFileName(s.poolName()))) {
                problems.add(file + ": should be named " + scriptFileName(s.poolName()));
            }
            for (final Map<String, String> face : s.faces()) {
                if (officialNames.contains(face.get("Name"))) {
                    problems.add(file + ": an official script is also named \"" + face.get("Name") + "\"; move it to tbld-pool/reference");
                }
            }
        }
        final String edition = Files.readString(EDITION, StandardCharsets.UTF_8);
        if (!edition.equals(editionText(scripts, cards))) {
            problems.add("forge-gui/res/editions/TumbledMTG.txt doesn't list exactly the TBLD scripts: run \"java tbld-pool/TbldPool.java edition\"");
        }
        problems.forEach(System.out::println);
        System.out.println(scripts.size() + " TBLD script(s), " + problems.size() + " problem(s)");
        return problems.isEmpty();
    }

    static Set<String> officialNames() throws IOException {
        final Set<String> out = ConcurrentHashMap.newKeySet();
        try (Stream<Path> s = Files.walk(CARDSFOLDER)) {
            s.parallel().filter(p -> p.toString().endsWith(".txt") && !p.startsWith(SCRIPTS)).forEach(p -> {
                try {
                    for (final String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        if (l.startsWith("Name:")) {
                            out.add(l.substring(5).trim());
                        }
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return out;
    }

    static void writeEdition(final boolean verbose) throws IOException {
        final Map<String, Entry> cards = readPool(CARDS_FILE);
        if (cards.isEmpty()) {
            return;
        }
        final String text = editionText(readScripts(), cards);
        if (!text.equals(Files.exists(EDITION) ? Files.readString(EDITION, StandardCharsets.UTF_8) : "")) {
            Files.writeString(EDITION, text, StandardCharsets.UTF_8);
            System.out.println("Rewrote " + ROOT.relativize(EDITION));
        } else if (verbose) {
            System.out.println(ROOT.relativize(EDITION) + " is up to date");
        }
    }

    /** The edition: every scripted card, keeping the collector numbers it already has; basic lands as P1-P80 and so on. */
    static String editionText(final Map<String, Script> scripts, final Map<String, Entry> cards) throws IOException {
        final List<String> head = new ArrayList<>();
        final Map<String, String> numbers = new LinkedHashMap<>(); // card name -> collector number
        int max = 0;
        if (Files.exists(EDITION)) {
            boolean inCards = false;
            for (final String l : Files.readAllLines(EDITION, StandardCharsets.UTF_8)) {
                if (l.trim().equals("[cards]")) {
                    inCards = true;
                    continue;
                }
                if (!inCards) {
                    head.add(l);
                    continue;
                }
                final String[] p = l.trim().split(" ", 3);
                if (p.length == 3 && p[0].matches("\\d+")) {
                    numbers.putIfAbsent(p[2], p[0]);
                    max = Math.max(max, Integer.parseInt(p[0]));
                }
            }
        }
        if (head.isEmpty()) {
            head.addAll(List.of("[metadata]", "Code=TBLD", "Date=2026-11-24", "Name=TumbledMTG", "Type=Custom_Set", ""));
        }
        while (!head.isEmpty() && head.get(head.size() - 1).isBlank()) {
            head.remove(head.size() - 1);
        }
        final List<String[]> lines = new ArrayList<>(); // number, rarity, name
        final List<String> basics = new ArrayList<>();
        for (final Script s : scripts.values()) {
            final String pn = s.poolName();
            final Matcher bm = BASIC.matcher(pn);
            if (bm.matches() && bm.group(2).isEmpty()) {
                basics.add(pn);
                continue;
            }
            final Entry e = cards.get(pn);
            final String name = s.split() ? s.front("Name") + " // " + s.faces().get(1).get("Name") : s.front("Name");
            String num = numbers.get(name);
            if (num == null) {
                num = String.valueOf(++max);
                numbers.put(name, num);
            }
            lines.add(new String[] {num, rarity(e == null ? "" : e.get("rarity")), name});
        }
        lines.sort((a, b) -> Integer.compare(Integer.parseInt(a[0]), Integer.parseInt(b[0])));
        final StringBuilder b = new StringBuilder(String.join("\n", head)).append("\n\n[cards]\n");
        for (final String[] l : lines) {
            b.append(l[0]).append(' ').append(l[1]).append(' ').append(l[2]).append('\n');
        }
        Collections.sort(basics);
        for (final String basic : basics) {
            for (final Entry v : basicVersions(cards, basic)) {
                b.append(BASIC_LETTER.get(basic)).append(version(v.name)).append(" L ").append(basic).append('\n');
            }
        }
        return b.toString();
    }

    static String rarity(final String r) {
        if (r.equals("Basic")) {
            return "L";
        }
        if (r.startsWith("M")) {
            return "M";
        }
        if (r.startsWith("R")) {
            return "R";
        }
        if (r.startsWith("U")) {
            return "U";
        }
        if (r.startsWith("C")) {
            return "C";
        }
        return "S";
    }

    static Path findRoot() {
        for (Path d = Paths.get("").toAbsolutePath(); d != null; d = d.getParent()) {
            if (Files.isDirectory(d.resolve("forge-gui/res/cardsfolder"))) {
                return d;
            }
        }
        throw new IllegalStateException("Run this from the TBLD_forge repository");
    }
}
