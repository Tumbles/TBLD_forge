package forge.tbld;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.tbld.server.BrowserSeat;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** The object ids Forge puts in its texts ("Mountain (45)") are left out of what players see; the game log keeps them. */
public class TbldObjectIdsTest {

    @Test
    public void idsAreLeftOutOfTexts() {
        AssertJUnit.assertEquals("Mountain", BrowserSeat.withoutIds("Mountain (45)"));
        AssertJUnit.assertEquals("Lightning Bolt", BrowserSeat.withoutIds("Lightning Bolt [45]"));
        AssertJUnit.assertEquals("Face-down card", BrowserSeat.withoutIds("Face-down card (H7)"));
        AssertJUnit.assertEquals("Searing Touch's Effect", BrowserSeat.withoutIds("Searing Touch (76)'s Effect"));
        AssertJUnit.assertEquals("Sacrifice Blood?", BrowserSeat.withoutIds("Sacrifice Blood (12)?"));
        AssertJUnit.assertEquals("Do you want to pay {2} (to keep it)?", BrowserSeat.withoutIds("Do you want to pay {2} (to keep it)?"));
        AssertJUnit.assertEquals("Ward {2}", BrowserSeat.withoutIds("Ward {2}"));
    }

    @Test
    public void idsAreLeftOutOfWholeMessagesExceptTheLog() {
        final JsonObject msg = JsonParser.parseString("""
                {"t":"call","m":"showPromptMessage","a":[null,"Choose Goblin Chaff (9) to block Mountain (45)"],
                 "state":{"stack":[{"text":"Searing Touch (76) - deals 4 damage to Goblin Chaff (9).","yieldKey":"Searing Touch (76): x"}],
                 "alwaysYields":[{"key":"Searing Touch (76): x","name":"Searing Touch (76)"}],
                 "log":["Brett cast Searing Touch (76)."]}}""").getAsJsonObject();
        BrowserSeat.withoutIds(msg);
        final String text = msg.toString();
        AssertJUnit.assertTrue(text, text.contains("\"Choose Goblin Chaff to block Mountain\""));
        AssertJUnit.assertTrue(text, text.contains("\"Searing Touch - deals 4 damage to Goblin Chaff.\""));
        AssertJUnit.assertTrue(text, text.contains("\"name\":\"Searing Touch\""));
        AssertJUnit.assertTrue("the log keeps its ids: " + text, text.contains("\"log\":[\"Brett cast Searing Touch (76).\"]"));
        AssertJUnit.assertTrue("keys that name an ability are left as they are: " + text, text.contains("\"key\":\"Searing Touch (76): x\""));
    }
}
