package qupath.ext.omero.core.pixelapis.ice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.imageserver.OmeroImageServerBuilder;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class TestIceReader extends OmeroServer {

    abstract static class GenericImage {

        protected static final UserType userType = UserType.USER;
        protected static Image image;
        protected static WebClient client;
        protected static TileRequest tileRequest;
        protected static PixelApiReader reader;

        @AfterAll
        static void removeClient() throws Exception {
            if (reader != null) {
                reader.close();
            }
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
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getRGBImage(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class UInt8Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getUint8Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class UInt16Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getUint16Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Int16Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getInt16Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Int32Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getInt32Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Float32Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getFloat32Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }

    @Nested
    class Float64Image extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            image = OmeroServer.getFloat64Image(userType);
            client = OmeroServer.createClient(userType);

            ImageServerMetadata metadata = null;
            try (OmeroImageServer imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                    OmeroServer.getImageURI(image),
                    "--pixelAPI",
                    "Ice"
            )) {
                if (imageServer != null) {
                    tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

                    metadata = imageServer.getMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (metadata != null && client.getPixelAPI(IceAPI.class).isAvailable().get()) {
                try {
                    reader = client.getPixelAPI(IceAPI.class).createReader(
                            image.getId(),
                            metadata
                    );
                } catch (IOException e) {
                    Assumptions.abort("Aborting test: ICE API not available");
                }
            } else {
                Assumptions.abort("Aborting tests: ICE API not available");
            }
        }
    }
}
