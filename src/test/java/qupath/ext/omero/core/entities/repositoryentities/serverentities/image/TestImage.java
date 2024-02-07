package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import org.junit.jupiter.api.*;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TestImage extends OmeroServer {

    abstract static class GenericImage {

        protected static WebClient client;
        protected static Image image;

        @AfterAll
        static void removeClient() {
            WebClients.removeClient(client);
        }

        @Test
        void Check_Has_Children() {
            boolean expectedChildren = false;

            boolean hasChildren = image.hasChildren();

            Assertions.assertEquals(expectedChildren, hasChildren);
        }

        @Test
        void Check_Children() throws InterruptedException {
            List<? extends RepositoryEntity> expectedChildren = List.of();

            List<? extends RepositoryEntity> children = image.getChildren();
            while (image.isPopulatingChildren()) {
                TimeUnit.MILLISECONDS.sleep(50);
            }

            TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
        }

        @Test
        abstract void Check_Attributes();
    }

    @Nested
    class RGBImage extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            client = OmeroServer.createUnauthenticatedClient();

            image = client.getApisHandler().getImage(OmeroServer.getRGBImage().getId()).get().orElse(null);
        }

        @Test
        @Override
        void Check_Attributes() {
            int numberOfValues = image.getNumberOfAttributes();
            String[] expectedAttributeValues = new String[numberOfValues];
            for (int i=0; i<numberOfValues; ++i) {
                expectedAttributeValues[i] = OmeroServer.getRGBImageAttributeValue(i);
            }

            String[] attributesValues = new String[numberOfValues];
            for (int i=0; i<numberOfValues; ++i) {
                attributesValues[i] = image.getAttributeValue(i);
            }

            Assertions.assertArrayEquals(expectedAttributeValues, attributesValues);
        }
    }

    @Nested
    class ComplexImage extends GenericImage {

        @BeforeAll
        static void createClient() throws ExecutionException, InterruptedException {
            client = OmeroServer.createUnauthenticatedClient();

            image = client.getApisHandler().getImage(OmeroServer.getComplexImage().getId()).get().orElse(null);
        }

        @Test
        @Override
        void Check_Attributes() {
            int numberOfValues = image.getNumberOfAttributes();
            String[] expectedAttributeValues = new String[numberOfValues];
            for (int i=0; i<numberOfValues; ++i) {
                expectedAttributeValues[i] = OmeroServer.getComplexImageAttributeValue(i);
            }

            String[] attributesValues = new String[numberOfValues];
            for (int i=0; i<numberOfValues; ++i) {
                attributesValues[i] = image.getAttributeValue(i);
            }

            Assertions.assertArrayEquals(expectedAttributeValues, attributesValues);
        }
    }
}
