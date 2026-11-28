package forge.tbld.server;

import forge.deck.Deck;

import java.util.List;
import java.util.function.Supplier;

/**
 * The decks a seat can choose in the web client. Each holds only implemented cards (abilities played
 * by an in-game test) that have pictures; {@code TbldTestDecksTest} checks this.
 */
public final class TbldTestDecks {
    private TbldTestDecks() {
    }

    /** A deck the client offers: its id (sent by the browser), the label shown and how to build it. */
    public record Entry(String id, String label, Supplier<Deck> deck) {
    }

    public static final List<Entry> ALL = List.of(
            new Entry("red", "Red goblins and burn", TbldTestDecks::red),
            new Entry("green", "Green wolves and hydras", TbldTestDecks::green),
            new Entry("ub-control", "Blue-black counterspells", TbldTestDecks::ubControl),
            new Entry("ur-spells", "Blue-red spells", TbldTestDecks::urSpells),
            new Entry("wu-heroic", "White-blue heroic flyers", TbldTestDecks::wuHeroic),
            new Entry("wb-drain", "White-black life drain", TbldTestDecks::wbDrain),
            new Entry("br-aggro", "Black-red aggro", TbldTestDecks::brAggro),
            new Entry("b-zombies", "Black zombies", TbldTestDecks::bZombies));

    /** The entry with this id, or null. */
    public static Entry byId(final String id) {
        for (final Entry e : ALL) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** The entry whose deck has this name, or null. */
    public static Entry byDeckName(final String name) {
        for (final Entry e : ALL) {
            if (e.deck().get().getName().equals(name)) {
                return e;
            }
        }
        return null;
    }

    /** The red test deck (40 cards). Its Mountains are TBLD Mountain version 69. */
    public static Deck red() {
        final Deck d = new Deck("TBLD red test deck");
        d.getMain().add("Mountain|TBLD|69", 17);
        d.getMain().add("Rigging Runner", 2);
        d.getMain().add("Goblin Wardriver", 3);
        d.getMain().add("Voldaren Epicure", 2);
        d.getMain().add("Harsh Mentor", 2);
        d.getMain().add("Crackling Cyclops", 2);
        d.getMain().add("Hound of Griselbrand", 2);
        d.getMain().add("Volley Veteran", 1);
        d.getMain().add("Searing Touch", 3);
        d.getMain().add("Galvanic Blast", 2);
        d.getMain().add("Firebolt", 2);
        d.getMain().add("Searing Blood", 2);
        return d;
    }

    /** The green test deck (40 cards). Its Forests are TBLD Forest version 11. */
    public static Deck green() {
        final Deck d = new Deck("TBLD green test deck");
        d.getMain().add("Forest|TBLD|11", 17);
        d.getMain().add("Young Wolf", 3);
        d.getMain().add("Deeproot Champion", 2);
        d.getMain().add("Servant of the Conduit", 2);
        d.getMain().add("Sacred Wolf", 3);
        d.getMain().add("Jungleborn Pioneer", 2);
        d.getMain().add("Greenweaver Druid", 2);
        d.getMain().add("Ironscale Hydra", 2);
        d.getMain().add("Stingerfling Spider", 1);
        d.getMain().add("Blanchwood Armor", 2);
        d.getMain().add("Hunger of the Howlpack", 2);
        d.getMain().add("Stand Together", 1);
        d.getMain().add("Feed the Clan", 1);
        return d;
    }

    /** Blue-black control: counterspells, removal and a few evasive creatures. */
    public static Deck ubControl() {
        final Deck d = new Deck("TBLD blue-black counterspells test deck");
        d.getMain().add("Island|TBLD|23", 9);
        d.getMain().add("Swamp|TBLD|41", 8);
        d.getMain().add("Fathom Feeder", 2);
        d.getMain().add("Vampire Nighthawk", 2);
        d.getMain().add("Augury Raven", 2);
        d.getMain().add("Citywatch Sphinx", 2);
        d.getMain().add("Lazav Wearer of Faces", 1);
        d.getMain().add("Essence Scatter", 2);
        d.getMain().add("Exclude", 1);
        d.getMain().add("Condescend", 1);
        d.getMain().add("Disdainful Stroke", 1);
        d.getMain().add("Devious Cover-Up", 1);
        d.getMain().add("Confirm Suspicions", 1);
        d.getMain().add("Grasp of Darkness", 2);
        d.getMain().add("Tyrants Scorn", 1);
        d.getMain().add("Agony Warp", 1);
        d.getMain().add("Poison the Cup", 1);
        d.getMain().add("Sign in Blood", 1);
        d.getMain().add("Concentrate", 1);
        return d;
    }

    /** Blue-red spells: pingers and creatures that reward casting sorceries, card draw, bounce and counters. */
    public static Deck urSpells() {
        final Deck d = new Deck("TBLD blue-red spells test deck");
        d.getMain().add("Island|TBLD|57", 9);
        d.getMain().add("Mountain|TBLD|12", 8);
        d.getMain().add("Razorfin Hunter", 2);
        d.getMain().add("Gelectrode", 2);
        d.getMain().add("Ecstatic Electromancer", 2);
        d.getMain().add("Cyclops Superconductor", 2);
        d.getMain().add("Baral Chief of Compliance", 1);
        d.getMain().add("Augur of Bolas", 1);
        d.getMain().add("Hypersonic Dragon", 2);
        d.getMain().add("Electrosiphon", 2);
        d.getMain().add("Confound", 1);
        d.getMain().add("Circular Logic", 1);
        d.getMain().add("Ideas Unbound", 1);
        d.getMain().add("Chart a Course", 2);
        d.getMain().add("Capsize", 1);
        d.getMain().add("Cyclonic Rift", 1);
        d.getMain().add("Stitch in Time", 1);
        d.getMain().add("Fireminds Research", 1);
        return d;
    }

    /** White-blue heroic: creatures that reward being targeted, auras and flyers. */
    public static Deck wuHeroic() {
        final Deck d = new Deck("TBLD white-blue heroic test deck");
        d.getMain().add("Plains|TBLD|7", 8);
        d.getMain().add("Island|TBLD|64", 9);
        d.getMain().add("Battlewise Hoplite", 2);
        d.getMain().add("Battlefield Thaumaturge", 2);
        d.getMain().add("Riddle Gate Gargoyle", 2);
        d.getMain().add("Hussar Patrol", 2);
        d.getMain().add("Elite Guardmage", 2);
        d.getMain().add("Benthic Infiltrator", 1);
        d.getMain().add("Ascended Lawmage", 1);
        d.getMain().add("Azorius Skyguard", 1);
        d.getMain().add("Staggering Insight", 2);
        d.getMain().add("Arcane Flight", 2);
        d.getMain().add("Eel Umbra", 1);
        d.getMain().add("Aqueous Form", 1);
        d.getMain().add("Double Cleave", 1);
        d.getMain().add("Dramatic Rescue", 1);
        d.getMain().add("Faithful Mending", 1);
        d.getMain().add("Essence Capture", 1);
        return d;
    }

    /** White-black drain: life gain that makes the opponent lose life, and removal. */
    public static Deck wbDrain() {
        final Deck d = new Deck("TBLD white-black drain test deck");
        d.getMain().add("Plains|TBLD|33", 7);
        d.getMain().add("Swamp|TBLD|66", 10);
        d.getMain().add("Vicious Conquistador", 2);
        d.getMain().add("Orzhov Enforcer", 2);
        d.getMain().add("Cruel Celebrant", 2);
        d.getMain().add("Elas il-Kor Sadistic Pilgrim", 1);
        d.getMain().add("Vito Thorn of the Dusk Rose", 1);
        d.getMain().add("Marauding Blight-Priest", 2);
        d.getMain().add("Treasury Thrull", 1);
        d.getMain().add("Basilica Bell-Haunt", 1);
        d.getMain().add("Sanctum Seeker", 1);
        d.getMain().add("Vona Butcher of Magan", 1);
        d.getMain().add("Mortify", 2);
        d.getMain().add("Unmake", 1);
        d.getMain().add("Despark", 1);
        d.getMain().add("Gift of Orzhova", 1);
        d.getMain().add("Vicious Rumors", 1);
        d.getMain().add("Foul-Tongue Shriek", 1);
        d.getMain().add("Defeat", 1);
        d.getMain().add("Sudden Edict", 1);
        return d;
    }

    /** Black-red aggro: hasty and menacing creatures, discard and removal. */
    public static Deck brAggro() {
        final Deck d = new Deck("TBLD black-red aggro test deck");
        d.getMain().add("Mountain|TBLD|38", 7);
        d.getMain().add("Swamp|TBLD|15", 9);
        d.getMain().add("Spike Jester", 3);
        d.getMain().add("Goblin Deathraiders", 2);
        d.getMain().add("Bloodtithe Harvester", 2);
        d.getMain().add("Rakdos Headliner", 2);
        d.getMain().add("Beetleback Chief", 2);
        d.getMain().add("Cryptborn Horror", 1);
        d.getMain().add("Boltwing Marauder", 1);
        d.getMain().add("Pulse Tracker", 2);
        d.getMain().add("Mardu Shadowspear", 1);
        d.getMain().add("Auger Spree", 1);
        d.getMain().add("Blightning", 1);
        d.getMain().add("Terminal Agony", 2);
        d.getMain().add("Infernal Tribute", 1);
        d.getMain().add("Disfigure", 2);
        d.getMain().add("Skulduggery", 1);
        return d;
    }

    /** Black zombies: zombie lords, recursion, sacrifice and devotion. */
    public static Deck bZombies() {
        final Deck d = new Deck("TBLD black zombies test deck");
        d.getMain().add("Swamp|TBLD|72", 17);
        d.getMain().add("Carrion Feeder", 2);
        d.getMain().add("Sightless Ghoul", 2);
        d.getMain().add("Putrid Goblin", 2);
        d.getMain().add("Ghoulraiser", 2);
        d.getMain().add("Lord of the Accursed", 2);
        d.getMain().add("Undead Butler", 1);
        d.getMain().add("Fell Stinger", 2);
        d.getMain().add("Tomb Tyrant", 2);
        d.getMain().add("Warren Soultrader", 1);
        d.getMain().add("Gray Merchant of Asphodel", 2);
        d.getMain().add("Lilianas Mastery", 1);
        d.getMain().add("Victim of Night", 2);
        d.getMain().add("Macabre Waltz", 1);
        d.getMain().add("Endless Ranks of the Dead", 1);
        return d;
    }
}
