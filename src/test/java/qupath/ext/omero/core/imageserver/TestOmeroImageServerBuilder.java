package qupath.ext.omero.core.imageserver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.ExecutionException;

public class TestOmeroImageServerBuilder extends OmeroServer {

    private static final UserType userType = UserType.PUBLIC;
    private static final Image image = OmeroServer.getRGBImage(userType);
    private static WebClient client;

    @BeforeAll
    static void createClient() throws ExecutionException, InterruptedException {
        client = OmeroServer.createClient(userType);
    }

    @AfterAll
    static void removeClient() {
        WebClients.removeClient(client);
    }

    @Test
    void Check_Server_Can_Be_Built_With_RGB_image() {
        URI imageURI = OmeroServer.getImageURI(image);

        try (ImageServer<BufferedImage> server = new OmeroImageServerBuilder().buildServer(imageURI, "--pixelAPI", "web")) {
            Assertions.assertNotNull(server);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
