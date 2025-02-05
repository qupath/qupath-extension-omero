package qupath.ext.omero.core.entities.repositoryentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.Credentials;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TestOrphanedFolder extends OmeroServer {

    private static final Credentials.UserType userType = Credentials.UserType.PUBLIC_USER;
    private static Client client;
    private static OrphanedFolder orphanedFolder;

    @BeforeAll
    static void createClient() throws ExecutionException, InterruptedException {
        client = OmeroServer.createClient(userType);
        orphanedFolder = OrphanedFolder.create(client.getApisHandler()).get();
    }

    @AfterAll
    static void removeClient() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void Check_Has_Children() {
        boolean expectedChildren = true;

        boolean hasChildren = orphanedFolder.hasChildren();

        Assertions.assertEquals(expectedChildren, hasChildren);
    }

    @Test
    void Check_Children() throws InterruptedException {
        List<? extends RepositoryEntity> expectedChildren = OmeroServer.getOrphanedImages(userType);

        List<? extends RepositoryEntity> children = orphanedFolder.getChildren();
        while (orphanedFolder.isPopulatingChildren()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
    }
}
