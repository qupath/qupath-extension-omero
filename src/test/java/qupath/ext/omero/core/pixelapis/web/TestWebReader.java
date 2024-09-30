package qupath.ext.omero.core.pixelapis.web;

import org.junit.jupiter.api.*;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.pixelapis.PixelAPIReader;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.imageserver.OmeroImageServerBuilder;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class TestWebReader extends OmeroServer {

    abstract static class GenericImage {

        protected static final UserType userType = UserType.PUBLIC;
        protected static Image image;
        protected static WebClient client;
        protected static TileRequest tileRequest;
        protected static PixelAPIReader reader;

        @AfterAll
        static void removeClient() throws Exception {
            reader.close();
            WebClients.removeClient(client);
        }

        @Test
        void Check_Image_Can_Be_Read() throws IOException {
            BufferedImage image = reader.readTile(tileRequest);

            Assertions.assertNotNull(image);
        }

        @Test
        void Check_Image_Histogram() throws IOException {
            double expectedMean = OmeroServer.getImageRedChannelMean(image);
            double expectedStdDev = OmeroServer.getImageRedChannelStdDev(image);

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
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getRGBImage(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI", "Web",
                    "--jpegQuality", "1.0")
            ) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            reader = client.getPixelAPI(WebAPI.class).createReader(
                    image.getId(),
                    metadata
            );
        }
    }

    @Nested
    class UInt8Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getUint8Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI", "Web",
                    "--jpegQuality", "1.0")
            ) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            reader = client.getPixelAPI(WebAPI.class).createReader(
                    image.getId(),
                    metadata
            );
        }
    }
}
