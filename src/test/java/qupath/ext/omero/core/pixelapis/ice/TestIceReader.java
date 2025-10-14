package qupath.ext.omero.core.pixelapis.ice;

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
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.List;

public class TestIceReader extends OmeroServer {

    abstract static class GenericImage {

        protected static final UserType userType = UserType.AUTHENTICATED;
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

            Raster raster = image.getData();
            double[] redValues = new double[image.getWidth()*image.getHeight()];
            for (int y=0; y<image.getHeight(); y++) {
                for (int x=0; x<image.getWidth(); x++) {
                    redValues[x + image.getWidth()*y] = raster.getSampleDouble(x, y, 0);
                }
            }
            Histogram histogram = new Histogram(
                    redValues,
                    256,
                    Double.NaN,
                    Double.NaN
            );

            Assertions.assertEquals(expectedMean, histogram.getMeanValue(), 0.001);
            Assertions.assertEquals(expectedStdDev, histogram.getStdDev(), 0.001);
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
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
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
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class UInt16Image extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getUint16ImageRedChannelMean();
            expectedStdDev = OmeroServer.getUint16ImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Int16Image extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getInt16ImageRedChannelMean();
            expectedStdDev = OmeroServer.getInt16ImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Int32Image extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getInt32ImageRedChannelMean();
            expectedStdDev = OmeroServer.getInt32ImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Float32Image extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getFloat32ImageRedChannelMean();
            expectedStdDev = OmeroServer.getFloat32ImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Float64Image extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedMean = OmeroServer.getFloat64ImageRedChannelMean();
            expectedStdDev = OmeroServer.getFloat64ImageRedChannelStdDev();

            client = OmeroServer.createClient(userType);

            long imageId = OmeroServer.getRgbImage(userType).id();
            ImageServerMetadata metadata;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageUri(imageId),
                    "--pixelAPI",
                    "Ice",
                    "--username",
                    OmeroServer.getUsername(userType)
            )) {
                tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);
                metadata = imageServer.getMetadata();
            } catch (Exception e) {
                Assumptions.abort("Aborting tests: ICE API not available");
                return;
            }

            if (client.getPixelAPI(IceApi.class).isAvailable().get()) {
                reader = client.getPixelAPI(IceApi.class).createReader(
                        imageId,
                        metadata,
                        List.of()
                );
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }
}
