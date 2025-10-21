package qupath.ext.omero.core.pixelapis.ice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.pixelapis.PixelApiReader;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.imageserver.OmeroImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class TestIceReader extends OmeroServer {

    abstract static class GenericImage {

        protected static final UserType userType = UserType.AUTHENTICATED;
        protected static BufferedImage expectedImage;
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
        void Check_Image() throws IOException {
            BufferedImage image = reader.readTile(tileRequest);

            TestUtils.assertDoubleBufferedImagesEqual(expectedImage, image);
        }
    }

    @Nested
    class RgbImage extends GenericImage {

        @BeforeAll
        static void createClient() throws Exception {
            expectedImage = OmeroServer.getRgbImage();

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
            expectedImage = OmeroServer.getUint8Image();

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
            expectedImage = OmeroServer.getUint16Image();

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
            expectedImage = OmeroServer.getInt16Image();

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
            expectedImage = OmeroServer.getInt32Image();

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
            expectedImage = OmeroServer.getFloat32Image();

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
            expectedImage = OmeroServer.getFloat64Image();

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
