package qupath.ext.omero.core.pixelapis.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.imageserver.OmeroImageServerBuilder;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TestWebReader extends OmeroServer {

    abstract static class GenericImage {

        protected static final UserType userType = UserType.UNAUTHENTICATED;
        protected static double expectedMean;
        protected static double expectedStdDev;
        protected static Client client;
        protected static TileRequest tileRequest;
        protected static PixelApiReader reader;

        @AfterAll
        static void removeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Test
        void Check_Image_Can_Be_Read() throws IOException {
            BufferedImage image = reader.readTile(tileRequest);

            Assertions.assertNotNull(image);
        }

        @Test
        void Check_Image_Histogram() throws IOException {
            BufferedImage image = reader.readTile(tileRequest);

            Histogram histogram = new Histogram(
                    Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                            .map(ColorTools::red)
                            .toArray(),
                    256,
                    Double.NaN,
                    Double.NaN
            );

            Assertions.assertEquals(expectedMean, histogram.getMeanValue(), 0.1);
            Assertions.assertEquals(expectedStdDev, histogram.getStdDev(), 0.1);
        }
    }

    @Nested
    class RgbImage extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getRgbImageRedChannelMean();
            expectedStdDev = OmeroServer.getRgbImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI", "Web",
                    "--jpegQuality", "1.0",
                    "--usertype",
                    userType.name()
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            }

            if (client.getPixelAPI(WebApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(WebApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: Web API not available");
            }
        }
    }

    @Nested
    class UInt8Image extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getUint8ImageRedChannelMean();
            expectedStdDev = OmeroServer.getUint8ImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI", "Web",
                    "--jpegQuality", "1.0",
                    "--usertype",
                    userType.name()
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            }

            if (client.getPixelAPI(WebApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(WebApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: Web API not available");
            }
        }
    }
}
