package forge.tbld;

import forge.StaticData;
import forge.ai.AITest;
import forge.item.PaperCard;
import forge.tbld.server.TbldPictures;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

/** TBLD basic land versions and the card pictures the web server serves. */
public class TbldPicturesTest extends AITest {

    @Test
    public void tbldMountainHasEightyVersions() {
        final PaperCard m69 = StaticData.instance().getCommonCards().getCard("Mountain", "TBLD", 69);
        AssertJUnit.assertNotNull("no TBLD Mountain version 69", m69);
        AssertJUnit.assertEquals("Mountain", m69.getName());
        AssertJUnit.assertEquals(69, m69.getArtIndex());
        AssertJUnit.assertEquals("M69", m69.getCollectorNumber());
        AssertJUnit.assertEquals(80, StaticData.instance().getCommonCards().getArtCount("Mountain", "TBLD"));
    }

    @Test
    public void picturesAreFoundByVersionAndServed() throws Exception {
        // A version with its own picture; a version without one; a card with one picture for every
        // version; a split card (its file is named "Clubs_Spades"); a card with none
        AssertJUnit.assertEquals("/pics/TBLD%20Mountain69.jpg", TbldPictures.urlFor("Mountain", 69));
        AssertJUnit.assertNull(TbldPictures.urlFor("Mountain", 1));
        AssertJUnit.assertEquals("/pics/TBLD%20Searing%20Touch.jpg", TbldPictures.urlFor("Searing Touch", 1));
        AssertJUnit.assertEquals("/pics/TBLD%20Clubs_Spades.jpg", TbldPictures.urlFor("Clubs // Spades", 1));
        AssertJUnit.assertNull(TbldPictures.urlFor("No Such Card", 1));

        final TbldWebServer server = new TbldWebServer(2);
        final int port = server.start(0);
        try {
            final HttpClient http = HttpClient.newHttpClient();
            final HttpResponse<byte[]> ok = get(http, port, "/pics/TBLD%20Mountain69.jpg");
            AssertJUnit.assertEquals(200, ok.statusCode());
            AssertJUnit.assertEquals("image/jpeg", ok.headers().firstValue("content-type").orElse(""));
            AssertJUnit.assertEquals(Files.size(TbldPictures.file("TBLD Mountain69.jpg")), ok.body().length);
            AssertJUnit.assertEquals(404, get(http, port, "/pics/TBLD%20Nothing.png").statusCode());
            // Only files in the pictures folder are served
            AssertJUnit.assertEquals(404, get(http, port, "/pics/..%2F..%2Feditions%2FTumbledMTG.txt").statusCode());
            AssertJUnit.assertEquals(404, get(http, port, "/pics/..%5C..%5Ceditions%5CTumbledMTG.txt").statusCode());
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<byte[]> get(final HttpClient http, final int port, final String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
