package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class TestPreferencesManager {

    @BeforeEach
    void clearPreferences() {
        PreferencesManager.clearAllPreferences();
    }

    @Test
    void Check_URIs_Empty() {
        List<URI> expectedURIs = List.of();

        List<URI> uris = PreferencesManager.getURIs();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
    }

    @Test
    void Check_URIs_Added() {
        URI uri = URI.create("https://github.com/qupath");
        List<URI> expectedURIs = List.of(uri);

        PreferencesManager.addURI(uri);

        List<URI> uris = PreferencesManager.getURIs();
        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
    }

    @Test
    void Check_URIs_Removed() {
        URI uri = URI.create("https://github.com/qupath");
        List<URI> expectedURIs = List.of();

        PreferencesManager.addURI(uri);
        PreferencesManager.removeURI(uri);

        List<URI> uris = PreferencesManager.getURIs();
        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedURIs, uris);
    }

    @Test
    void Check_Last_Server_URI_Empty() {
        String expectedLastURI = "";

        String lastURI = PreferencesManager.getLastServerURI();

        Assertions.assertEquals(expectedLastURI, lastURI);
    }

    @Test
    void Check_Last_Server_URI() {
        URI uri = URI.create("https://github.com/qupath");
        String expectedLastURI = uri.toString();
        PreferencesManager.addURI(uri);

        String lastURI = PreferencesManager.getLastServerURI();

        Assertions.assertEquals(expectedLastURI, lastURI);
    }

    @Test
    void Check_Last_Username_Empty() {
        String expectedLastUsername = "";

        String lastUsername = PreferencesManager.getLastUsername();

        Assertions.assertEquals(expectedLastUsername, lastUsername);
    }

    @Test
    void Check_Last_Username_When_Set() {
        String expectedUsername = "username";

        PreferencesManager.setLastUsername(expectedUsername);

        String lastUsername = PreferencesManager.getLastUsername();
        Assertions.assertEquals(expectedUsername, lastUsername);
    }

    @Test
    void Check_Last_Username_When_Set_Twice() {
        String unexpectedUsername = "unexpected username";
        String expectedUsername = "username";

        PreferencesManager.setLastUsername(unexpectedUsername);
        PreferencesManager.setLastUsername(expectedUsername);

        String lastUsername = PreferencesManager.getLastUsername();
        Assertions.assertEquals(expectedUsername, lastUsername);
    }

    @Test
    void Check_Enable_Unauthenticated_Empty() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Boolean> enableUnauthenticated = PreferencesManager.getEnableUnauthenticated(uri);

        Assertions.assertTrue(enableUnauthenticated.isEmpty());
    }

    @Test
    void Check_Enable_Unauthenticated() {
        boolean expectedEnableUnauthenticated = true;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setEnableUnauthenticated(uri, expectedEnableUnauthenticated);

        Boolean enableUnauthenticated = PreferencesManager.getEnableUnauthenticated(uri).orElse(null);

        Assertions.assertEquals(expectedEnableUnauthenticated, enableUnauthenticated);
    }

    @Test
    void Check_Enable_Unauthenticated_When_Set_Twice() {
        boolean unexpectedEnableUnauthenticated = false;
        boolean expectedEnableUnauthenticated = true;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setEnableUnauthenticated(uri, unexpectedEnableUnauthenticated);
        PreferencesManager.setEnableUnauthenticated(uri, expectedEnableUnauthenticated);

        Boolean enableUnauthenticated = PreferencesManager.getEnableUnauthenticated(uri).orElse(null);

        Assertions.assertEquals(expectedEnableUnauthenticated, enableUnauthenticated);
    }

    @Test
    void Check_Enable_Unauthenticated_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        boolean otherEnableUnauthenticated = false;
        PreferencesManager.setEnableUnauthenticated(otherUri, otherEnableUnauthenticated);
        boolean expectedEnableUnauthenticated = true;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setEnableUnauthenticated(uri, expectedEnableUnauthenticated);

        Boolean enableUnauthenticated = PreferencesManager.getEnableUnauthenticated(uri).orElse(null);

        Assertions.assertEquals(expectedEnableUnauthenticated, enableUnauthenticated);
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_Empty() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Integer> msPixelBufferPort = PreferencesManager.getMsPixelBufferPort(uri);

        Assertions.assertTrue(msPixelBufferPort.isEmpty());
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port() {
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setMsPixelBufferPort(uri, expectedPort);

        int msPixelBufferPort = PreferencesManager.getMsPixelBufferPort(uri).orElse(-1);

        Assertions.assertEquals(expectedPort, msPixelBufferPort);
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_When_Set_Twice() {
        int unexpectedPort = 1000;
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setMsPixelBufferPort(uri, unexpectedPort);
        PreferencesManager.setMsPixelBufferPort(uri, expectedPort);

        int msPixelBufferPort = PreferencesManager.getMsPixelBufferPort(uri).orElse(-1);

        Assertions.assertEquals(expectedPort, msPixelBufferPort);
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        int otherPort = 1000;
        PreferencesManager.setMsPixelBufferPort(otherUri, otherPort);
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setMsPixelBufferPort(uri, expectedPort);

        int msPixelBufferPort = PreferencesManager.getMsPixelBufferPort(uri).orElse(-1);

        Assertions.assertEquals(expectedPort, msPixelBufferPort);
    }

    @Test
    void Check_Web_Jpeg_Quality_Empty() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Float> jpegQuality = PreferencesManager.getWebJpegQuality(uri);

        Assertions.assertTrue(jpegQuality.isEmpty());
    }

    @Test
    void Check_Web_Jpeg_Quality() {
        float expectedJpegQuality = 0.46f;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setWebJpegQuality(uri, expectedJpegQuality);

        float jpegQuality = PreferencesManager.getWebJpegQuality(uri).orElse(-1f);

        Assertions.assertEquals(expectedJpegQuality, jpegQuality);
    }

    @Test
    void Check_Web_Jpeg_Quality_When_Set_Twice() {
        float unexpectedJpegQuality = 0.12f;
        float expectedJpegQuality = 0.46f;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setWebJpegQuality(uri, unexpectedJpegQuality);
        PreferencesManager.setWebJpegQuality(uri, expectedJpegQuality);

        float jpegQuality = PreferencesManager.getWebJpegQuality(uri).orElse(-1f);

        Assertions.assertEquals(expectedJpegQuality, jpegQuality);
    }

    @Test
    void Check_Web_Jpeg_Quality_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        float otherJpegQuality = 0.78f;
        PreferencesManager.setWebJpegQuality(otherUri, otherJpegQuality);
        float expectedJpegQuality = 0.46f;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setWebJpegQuality(uri, expectedJpegQuality);

        float jpegQuality = PreferencesManager.getWebJpegQuality(uri).orElse(-1f);

        Assertions.assertEquals(expectedJpegQuality, jpegQuality);
    }

    @Test
    void Check_Ice_Address_Empty() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<String> iceAddress = PreferencesManager.getIceAddress(uri);

        Assertions.assertTrue(iceAddress.isEmpty());
    }

    @Test
    void Check_Ice_Address() {
        String expectedIceAddress = "https://omero.server.com/";
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIceAddress(uri, expectedIceAddress);

        String iceAddress = PreferencesManager.getIceAddress(uri).orElse("");

        Assertions.assertEquals(expectedIceAddress, iceAddress);
    }

    @Test
    void Check_Ice_Address_When_Set_Twice() {
        String unexpectedIceAddress = "https://unexpected.omero.server.com/";
        String expectedIceAddress = "https://omero.server.com/";
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIceAddress(uri, unexpectedIceAddress);
        PreferencesManager.setIceAddress(uri, expectedIceAddress);

        String iceAddress = PreferencesManager.getIceAddress(uri).orElse("");

        Assertions.assertEquals(expectedIceAddress, iceAddress);
    }

    @Test
    void Check_Ice_Address_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        String otherIceAddress = "https://other.omero.server.com/";
        PreferencesManager.setIceAddress(otherUri, otherIceAddress);
        String expectedIceAddress = "https://omero.server.com/";
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIceAddress(uri, expectedIceAddress);

        String iceAddress = PreferencesManager.getIceAddress(uri).orElse("");

        Assertions.assertEquals(expectedIceAddress, iceAddress);
    }

    @Test
    void Check_Ice_Port_Empty() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Integer> icePort = PreferencesManager.getIcePort(uri);

        Assertions.assertTrue(icePort.isEmpty());
    }

    @Test
    void Check_Ice_Port() {
        int expectedIcePort = 2024;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIcePort(uri, expectedIcePort);

        int icePort = PreferencesManager.getIcePort(uri).orElse(-1);

        Assertions.assertEquals(expectedIcePort, icePort);
    }

    @Test
    void Check_Ice_Port_When_Set_Twice() {
        int unexpectedIcePort = 8080;
        int expectedIcePort = 4646;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIcePort(uri, unexpectedIcePort);
        PreferencesManager.setIcePort(uri, expectedIcePort);

        int icePort = PreferencesManager.getIcePort(uri).orElse(-1);

        Assertions.assertEquals(expectedIcePort, icePort);
    }

    @Test
    void Check_Ice_Port_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        int otherIcePort = 1412;
        PreferencesManager.setIcePort(otherUri, otherIcePort);
        int expectedIcePort = 7878;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIcePort(uri, expectedIcePort);

        int iceAddress = PreferencesManager.getIcePort(uri).orElse(-1);

        Assertions.assertEquals(expectedIcePort, iceAddress);
    }
}
