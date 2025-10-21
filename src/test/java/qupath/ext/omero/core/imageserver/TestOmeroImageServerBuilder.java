package qupath.ext.omero.core.imageserver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Credentials;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

import java.awt.image.BufferedImage;
import java.net.URI;

public class TestOmeroImageServerBuilder extends OmeroServer {

    private static final UserType userType = UserType.UNAUTHENTICATED;

    abstract static class GenericImage {

        protected static long imageId;

        @Test
        void Check_Server_Can_Be_Built() {
            URI imageURI = OmeroServer.getImageUri(imageId);

            try (ImageServer<BufferedImage> server = new OmeroImageServerBuilder().buildServer(
                    imageURI,
                    "--pixelAPI", "Pixel Buffer Microservice",
                    "--usertype", switch (userType) {
                        case UNAUTHENTICATED -> Credentials.UserType.PUBLIC_USER.name();
                        case AUTHENTICATED -> Credentials.UserType.REGULAR_USER.name();
                        case ADMIN -> null;
                    }
            )) {
                Assertions.assertNotNull(server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void Check_Image_Support() {
            URI imageURI = OmeroServer.getImageUri(imageId);

            ImageServerBuilder.UriImageSupport<BufferedImage> imageSupport = new OmeroImageServerBuilder().checkImageSupport(
                    imageURI,
                    "--usertype", switch (userType) {
                        case UNAUTHENTICATED -> Credentials.UserType.PUBLIC_USER.name();
                        case AUTHENTICATED -> Credentials.UserType.REGULAR_USER.name();
                        case ADMIN -> null;
                    }
            );

            Assertions.assertEquals(imageURI, imageSupport.getBuilders().getFirst().getURIs().iterator().next());
        }
    }

    @Nested
    class RgbImage extends GenericImage {

        @BeforeAll
        static void createImage() {
            imageId = OmeroServer.getRgbImage(userType).id();
        }
    }

    @Nested
    class ComplexImage extends GenericImage {

        @BeforeAll
        static void createImage() {
            imageId = OmeroServer.getComplexImage(userType).id();
        }
    }
}
