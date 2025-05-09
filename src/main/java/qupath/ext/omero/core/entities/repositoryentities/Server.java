package qupath.ext.omero.core.entities.repositoryentities;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

/**
 * A server is the top element in the OMERO entity hierarchy.
 * It contains one {@link OrphanedFolder}, and zero or more projects, orphaned datasets, screen, and orphaned plates (described in
 * {@link qupath.ext.omero.core.entities.repositoryentities.serverentities server entities}).
 */
public class Server implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int NUMBER_OF_CHILDREN_TYPES = 5;
    private static final ResourceBundle resources = Utils.getResources();
    private final ObservableList<RepositoryEntity> children = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private final ObservableList<RepositoryEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final List<Owner> owners;
    private final List<Group> groups;
    private final Owner connectedOwner;
    private final Group defaultGroup;
    private int childrenTypesPopulated = 0;

    /**
     * Create a server. This will send a few requests to get basic information on the server, so it can
     * take a few seconds. However, this operation is cancellable. This also starts populating the children
     * (orphaned folder, projects, orphaned datasets...) of the server in the background.
     *
     * @param apisHandler the APIs handler to use when making requests
     * @throws ExecutionException if an error while performing a request to the server
     * @throws InterruptedException if the running thread is interrupted
     * @throws IllegalArgumentException if the server didn't return all the required information
     */
    public Server(ApisHandler apisHandler) throws ExecutionException, InterruptedException {
        long userId = apisHandler.getUserId().get();

        this.groups = apisHandler.isAdmin().orElse(false) ? apisHandler.getGroups().get() : apisHandler.getGroups(userId).get();
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "The server didn't return any group for user with ID %d", userId
            ));
        }
        logger.debug("Got groups {} for server", groups);

        this.owners = this.groups.stream()
                .map(Group::getOwners)
                .flatMap(List::stream)
                .distinct()
                .toList();
        logger.debug("Got owners {} for server", owners);

        this.connectedOwner = this.owners.stream()
                .filter(owner -> owner.id() == userId)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "The provided owner of ID %d was not found in the list returned by the server (%s)",
                        userId,
                        this.owners
                )));
        logger.debug("Got connected owner {} for server", connectedOwner);

        this.defaultGroup = apisHandler.getDefaultGroup()
                .map(group -> {
                    if (this.groups.contains(group)) {
                        return group;
                    } else {
                        logger.warn(
                                "The group {} was not found in the list returned by the server ({}). Using {}",
                                group,
                                this.groups,
                                this.groups.getFirst()
                        );
                        return this.groups.getFirst();
                    }
                })
                .orElse(this.groups.getFirst());
        logger.debug("Got default group {} for server", defaultGroup);

        populate(apisHandler);
    }

    @Override
    public String toString() {
        return String.format(
                "Server containing groups %s, owners %s, connected owner %s and default group %s",
                groups,
                owners,
                connectedOwner,
                defaultGroup
        );
    }

    @Override
    public boolean hasChildren() {
        return true;
    }

    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        return childrenImmutable;
    }

    @Override
    public String getLabel() {
        return resources.getString("Entities.RepositoryEntities.Server.server");
    }

    @Override
    public synchronized boolean isPopulatingChildren() {
        return childrenTypesPopulated != NUMBER_OF_CHILDREN_TYPES;
    }

    /**
     * @return the default group of the connected user
     */
    public Group getDefaultGroup() {
        return defaultGroup;
    }

    /**
     * Get the owner connected to this server. This can be the authenticated user
     * or the public user.
     *
     * @return the owner connected to this server
     */
    public Owner getConnectedOwner() {
        return connectedOwner;
    }

    /**
     * @return an unmodifiable list of groups the connected owner belong to (except for the
     * 'user' and 'system' groups), or all groups of the server if the connected user is an admin
     */
    public List<Group> getGroups() {
        return groups;
    }

    /**
     * @return an unmodifiable list of owners belonging to groups the connected owner belong to,
     * or all owners of the server if the connected user is an admin. Note that non-visible owners
     * (for example owners of private groups) are not included
     */
    public List<Owner> getOwners() {
        return owners;
    }

    private void populate(ApisHandler apisHandler) {
        populateChildren(apisHandler.getProjects(), "projects");
        populateChildren(apisHandler.getScreens(), "screens");
        populateChildren(apisHandler.getOrphanedDatasets(), "orphaned datasets");
        populateChildren(apisHandler.getOrphanedPlates(), "orphaned plates");

        OrphanedFolder.create(apisHandler).whenComplete(((orphanedFolder, error) -> {
            synchronized (this) {
                childrenTypesPopulated++;
            }

            if (orphanedFolder == null) {
                logger.error("Error while creating orphaned folder of server", error);
                return;
            }

            if (orphanedFolder.hasChildren()) {
                logger.debug("Got orphaned folder {}. Adding it to the children of the server", orphanedFolder);
                children.add(orphanedFolder);
            } else {
                logger.debug("Got orphaned folder {} but it doesn't have children, so it is not added to the children of the server", orphanedFolder);
            }
        }));
    }

    private <T extends RepositoryEntity> void populateChildren(CompletableFuture<List<T>> request, String name) {
        request.whenComplete((children, error) -> {
            synchronized (this) {
                childrenTypesPopulated++;
            }

            if (children == null) {
                logger.error("Error while retrieving children {} of server", name, error);
                return;
            }

            logger.debug("Got {} {} as children of server", name, children);
            this.children.addAll(children);
        });
    }
}
