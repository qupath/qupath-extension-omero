package qupath.ext.omero.core.imageserver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.net.URI;

public class TestOmeroImageServerBuilder extends OmeroServer {

    private static final Credentials.UserType userType = Credentials.UserType.PUBLIC_USER;

    abstract static class GenericImage {

        protected static Image image;

        @Test
        void Check_Server_Can_Be_Built() {
            URI imageURI = OmeroServer.getImageURI(image);

            try (ImageServer<BufferedImage> server = new OmeroImageServerBuilder().buildServer(
                    imageURI,
                    "--pixelAPI", "Pixel Buffer Microservice",
                    "--usertype", userType.name()
            )) {
                Assertions.assertNotNull(server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void Check_Image_Support() {
            URI imageURI = OmeroServer.getImageURI(image);

            ImageServerBuilder.UriImageSupport<BufferedImage> imageSupport = new OmeroImageServerBuilder().checkImageSupport(
                    imageURI,
                    "--usertype", userType.name()
            );

            Assertions.assertEquals(imageURI, imageSupport.getBuilders().getFirst().getURIs().iterator().next());
        }
    }

    @Nested
    class RgbImage extends GenericImage {

        @BeforeAll
        static void createImage() {
            image = OmeroServer.getRGBImage(userType);
        }
    }

    @Nested
    class ComplexImage extends GenericImage {

        @BeforeAll
        static void createImage() {
            image = OmeroServer.getComplexImage(userType);
        }
    }
}
