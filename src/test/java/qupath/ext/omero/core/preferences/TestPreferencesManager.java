package qupath.ext.omero.core.preferences;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.Credentials;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class TestPreferencesManager {

    @BeforeEach
    void clearPreferences() {
        for (ServerPreference serverPreference: PreferencesManager.getServerPreferences()) {
            PreferencesManager.removeServer(serverPreference.webServerUri());
        }
    }

    @Test
    void Check_Preferences_Empty() {
        List<ServerPreference> expectedPreferences = List.of();

        List<ServerPreference> preferences = PreferencesManager.getServerPreferences();

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedPreferences, preferences);
    }

    @Test
    void Check_Preferences_Added() {
        URI webServerUri = URI.create("https://github.com/qupath");
        Credentials credentials = new Credentials();
        ServerPreference serverPreference = new ServerPreference(
                webServerUri,
                credentials,
                null,
                null,
                null,
                null,
                null,
                null
        );
        List<ServerPreference> expectedPreferences = List.of(serverPreference);

        PreferencesManager.addServer(webServerUri, credentials);

        TestUtils.assertCollectionsEqualsWithoutOrder(
                expectedPreferences,
                PreferencesManager.getServerPreferences()
        );
    }

    @Test
    void Check_Preferences_Removed() {
        URI webServerUri = URI.create("https://github.com/qupath");
        Credentials credentials = new Credentials();
        List<ServerPreference> expectedPreferences = List.of();

        PreferencesManager.addServer(webServerUri, credentials);
        PreferencesManager.removeServer(webServerUri);

        TestUtils.assertCollectionsEqualsWithoutOrder(
                expectedPreferences,
                PreferencesManager.getServerPreferences()
        );
    }

    @Test
    void Check_Credentials_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Credentials> credentials = PreferencesManager.getCredentials(uri);

        Assertions.assertTrue(credentials.isEmpty());
    }

    @Test
    void Check_Credentials_When_Preference_Set() {
        Credentials expectedCredentials = new Credentials();
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, expectedCredentials);

        Credentials credentials = PreferencesManager.getCredentials(uri).orElseThrow();

        Assertions.assertEquals(expectedCredentials, credentials);
    }

    @Test
    void Check_Credentials_When_Set_Twice() {
        Credentials unexpectedCredentials = new Credentials("user1", "password1".toCharArray());
        Credentials expectedCredentials = new Credentials("user2", "password2".toCharArray());
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, unexpectedCredentials);
        PreferencesManager.addServer(uri, expectedCredentials);

        Credentials credentials = PreferencesManager.getCredentials(uri).orElseThrow();

        Assertions.assertEquals(expectedCredentials, credentials);
    }

    @Test
    void Check_Credentials_When_Other_URI_Set() {
        Credentials unexpectedCredentials = new Credentials("user1", "password1".toCharArray());
        Credentials expectedCredentials = new Credentials("user2", "password2".toCharArray());
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(otherUri, unexpectedCredentials);
        PreferencesManager.addServer(uri, expectedCredentials);

        Credentials credentials = PreferencesManager.getCredentials(uri).orElseThrow();

        Assertions.assertEquals(expectedCredentials, credentials);
    }

    @Test
    void Check_Max_Body_Size_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Long> maxBodySizeBytes = PreferencesManager.getMaxBodySizeBytes(uri);

        Assertions.assertTrue(maxBodySizeBytes.isEmpty());
    }

    @Test
    void Check_Max_Body_Size_When_Preference_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setMaxBodySizeBytes(uri, 456);

        Optional<Long> maxBodySizeBytes = PreferencesManager.getMaxBodySizeBytes(uri);

        Assertions.assertTrue(maxBodySizeBytes.isEmpty());
    }

    @Test
    void Check_Max_Body_Size_When_Preference_Set() {
        long expectedMaxBodySizeBytes = 5467;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setMaxBodySizeBytes(uri, expectedMaxBodySizeBytes);

        long maxBodySizeBytes = PreferencesManager.getMaxBodySizeBytes(uri).orElseThrow();

        Assertions.assertEquals(expectedMaxBodySizeBytes, maxBodySizeBytes);
    }

    @Test
    void Check_Max_Body_Size_When_Set_Twice() {
        long unexpectedMaxBodySizeBytes = 456;
        long expectedMaxBodySizeBytes = 78;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setMaxBodySizeBytes(uri, unexpectedMaxBodySizeBytes);
        PreferencesManager.setMaxBodySizeBytes(uri, expectedMaxBodySizeBytes);

        long maxBodySizeBytes = PreferencesManager.getMaxBodySizeBytes(uri).orElseThrow();

        Assertions.assertEquals(expectedMaxBodySizeBytes, maxBodySizeBytes);
    }

    @Test
    void Check_Max_Body_Size_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        long otherMaxBodySizeBytes = 4;
        PreferencesManager.addServer(otherUri, new Credentials());
        PreferencesManager.setMaxBodySizeBytes(otherUri, otherMaxBodySizeBytes);
        long expectedMaxBodySizeBytes = 4798;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setMaxBodySizeBytes(uri, expectedMaxBodySizeBytes);

        long maxBodySizeBytes = PreferencesManager.getMaxBodySizeBytes(uri).orElseThrow();

        Assertions.assertEquals(expectedMaxBodySizeBytes, maxBodySizeBytes);
    }

    @Test
    void Check_Web_Jpeg_Quality_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Float> jpegQuality = PreferencesManager.getWebJpegQuality(uri);

        Assertions.assertTrue(jpegQuality.isEmpty());
    }

    @Test
    void Check_Web_Jpeg_Quality_When_Preference_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setWebJpegQuality(uri, 0.5f);

        Optional<Float> jpegQuality = PreferencesManager.getWebJpegQuality(uri);

        Assertions.assertTrue(jpegQuality.isEmpty());
    }

    @Test
    void Check_Web_Jpeg_Quality_When_Preference_Set() {
        float expectedJpegQuality = 0.5f;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setWebJpegQuality(uri, expectedJpegQuality);

        float jpegQuality = PreferencesManager.getWebJpegQuality(uri).orElseThrow();

        Assertions.assertEquals(expectedJpegQuality, jpegQuality);
    }

    @Test
    void Check_Web_Jpeg_Quality_When_Set_Twice() {
        float unexpectedJpegQuality = 0.3f;
        float expectedJpegQuality = 0.7f;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setWebJpegQuality(uri, unexpectedJpegQuality);
        PreferencesManager.setWebJpegQuality(uri, expectedJpegQuality);

        float jpegQuality = PreferencesManager.getWebJpegQuality(uri).orElseThrow();

        Assertions.assertEquals(expectedJpegQuality, jpegQuality);
    }

    @Test
    void Check_Web_Jpeg_Quality_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        float otherJpegQuality = 0.7f;
        PreferencesManager.addServer(otherUri, new Credentials());
        PreferencesManager.setWebJpegQuality(otherUri, otherJpegQuality);
        float expectedJpegQuality = 0.3f;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setWebJpegQuality(uri, expectedJpegQuality);

        float jpegQuality = PreferencesManager.getWebJpegQuality(uri).orElseThrow();

        Assertions.assertEquals(expectedJpegQuality, jpegQuality);
    }

    @Test
    void Check_Ice_Address_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<String> address = PreferencesManager.getIceAddress(uri);

        Assertions.assertTrue(address.isEmpty());
    }

    @Test
    void Check_Ice_Address_When_Preference_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIceAddress(uri, "some_address");

        Optional<String> address = PreferencesManager.getIceAddress(uri);

        Assertions.assertTrue(address.isEmpty());
    }

    @Test
    void Check_Ice_Address_When_Preference_Set() {
        String expectedAddress = "some_address";
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIceAddress(uri, expectedAddress);

        String address = PreferencesManager.getIceAddress(uri).orElseThrow();

        Assertions.assertEquals(expectedAddress, address);
    }

    @Test
    void Check_Ice_Address_When_Set_Twice() {
        String unexpectedAddress = "address1";
        String expectedAddress = "address2";
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIceAddress(uri, unexpectedAddress);
        PreferencesManager.setIceAddress(uri, expectedAddress);

        String address = PreferencesManager.getIceAddress(uri).orElseThrow();

        Assertions.assertEquals(expectedAddress, address);
    }

    @Test
    void Check_Ice_Address_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        String otherAddress = "address1";
        PreferencesManager.addServer(otherUri, new Credentials());
        PreferencesManager.setIceAddress(otherUri, otherAddress);
        String expectedAddress = "address2";
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIceAddress(uri, expectedAddress);

        String address = PreferencesManager.getIceAddress(uri).orElseThrow();

        Assertions.assertEquals(expectedAddress, address);
    }

    @Test
    void Check_Ice_Port_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Integer> port = PreferencesManager.getIcePort(uri);

        Assertions.assertTrue(port.isEmpty());
    }

    @Test
    void Check_Ice_Port_When_Preference_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIcePort(uri, 8080);

        Optional<Integer> port = PreferencesManager.getIcePort(uri);

        Assertions.assertTrue(port.isEmpty());
    }

    @Test
    void Check_Ice_Port_When_Preference_Set() {
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIcePort(uri, expectedPort);

        int port = PreferencesManager.getIcePort(uri).orElseThrow();

        Assertions.assertEquals(expectedPort, port);
    }

    @Test
    void Check_Ice_Port_When_Set_Twice() {
        int unexpectedPort = 1000;
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIcePort(uri, unexpectedPort);
        PreferencesManager.setIcePort(uri, expectedPort);

        int port = PreferencesManager.getIcePort(uri).orElseThrow();

        Assertions.assertEquals(expectedPort, port);
    }

    @Test
    void Check_Ice_Port_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        int otherPort = 1000;
        PreferencesManager.addServer(otherUri, new Credentials());
        PreferencesManager.setIcePort(otherUri, otherPort);
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIcePort(uri, expectedPort);

        int port = PreferencesManager.getIcePort(uri).orElseThrow();

        Assertions.assertEquals(expectedPort, port);
    }

    @Test
    void Check_Ice_Number_Of_Readers_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Integer> numberOfReaders = PreferencesManager.getIceNumberOfReaders(uri);

        Assertions.assertTrue(numberOfReaders.isEmpty());
    }

    @Test
    void Check_Ice_Number_Of_Readers_When_Preference_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setIcePort(uri, 4);

        Optional<Integer> numberOfReaders = PreferencesManager.getIceNumberOfReaders(uri);

        Assertions.assertTrue(numberOfReaders.isEmpty());
    }

    @Test
    void Check_Ice_Number_Of_Readers_When_Preference_Set() {
        int expectedNumberOfReaders = 4;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIceNumberOfReaders(uri, expectedNumberOfReaders);

        int numberOfReaders = PreferencesManager.getIceNumberOfReaders(uri).orElseThrow();

        Assertions.assertEquals(expectedNumberOfReaders, numberOfReaders);
    }

    @Test
    void Check_Ice_Number_Of_Readers_When_Set_Twice() {
        int unexpectedNumberOfReaders = 4;
        int expectedNumberOfReaders = 10;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIceNumberOfReaders(uri, unexpectedNumberOfReaders);
        PreferencesManager.setIceNumberOfReaders(uri, expectedNumberOfReaders);

        int numberOfReaders = PreferencesManager.getIceNumberOfReaders(uri).orElseThrow();

        Assertions.assertEquals(expectedNumberOfReaders, numberOfReaders);
    }

    @Test
    void Check_Ice_Number_Of_Readers_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        int otherNumberOfReaders = 4;
        PreferencesManager.addServer(otherUri, new Credentials());
        PreferencesManager.setIceNumberOfReaders(otherUri, otherNumberOfReaders);
        int expectedNumberOfReaders = 10;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setIceNumberOfReaders(uri, expectedNumberOfReaders);

        int numberOfReaders = PreferencesManager.getIceNumberOfReaders(uri).orElseThrow();

        Assertions.assertEquals(expectedNumberOfReaders, numberOfReaders);
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_Empty_When_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");

        Optional<Integer> port = PreferencesManager.getMsPixelBufferPort(uri);

        Assertions.assertTrue(port.isEmpty());
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_When_Preference_Not_Set() {
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.setMsPixelBufferPort(uri, 8080);

        Optional<Integer> port = PreferencesManager.getMsPixelBufferPort(uri);

        Assertions.assertTrue(port.isEmpty());
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_When_Preference_Set() {
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setMsPixelBufferPort(uri, expectedPort);

        int port = PreferencesManager.getMsPixelBufferPort(uri).orElseThrow();

        Assertions.assertEquals(expectedPort, port);
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_When_Set_Twice() {
        int unexpectedPort = 1000;
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setMsPixelBufferPort(uri, unexpectedPort);
        PreferencesManager.setMsPixelBufferPort(uri, expectedPort);

        int port = PreferencesManager.getMsPixelBufferPort(uri).orElseThrow();

        Assertions.assertEquals(expectedPort, port);
    }

    @Test
    void Check_Ms_Pixel_Buffer_Port_When_Other_URI_Set() {
        URI otherUri = URI.create("https://qupath.readthedocs.io");
        int otherPort = 1000;
        PreferencesManager.addServer(otherUri, new Credentials());
        PreferencesManager.setMsPixelBufferPort(otherUri, otherPort);
        int expectedPort = 8080;
        URI uri = URI.create("https://github.com/qupath");
        PreferencesManager.addServer(uri, new Credentials());
        PreferencesManager.setMsPixelBufferPort(uri, expectedPort);

        int port = PreferencesManager.getMsPixelBufferPort(uri).orElseThrow();

        Assertions.assertEquals(expectedPort, port);
    }
}
