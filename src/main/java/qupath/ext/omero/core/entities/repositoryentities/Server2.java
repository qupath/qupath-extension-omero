package qupath.ext.omero.core.entities.repositoryentities;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler2;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.gui.UiUtilities;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

public class Server2 implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(Server2.class);
    private static final int NUMBER_OF_CHILDREN_TYPES = 5;
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final ObservableList<RepositoryEntity> children = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private final ObservableList<RepositoryEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final List<Owner> owners;
    private final List<Group> groups;
    private final Owner connectedOwner;
    private final Group defaultGroup;
    private int childrenTypesPopulated = 0;

    public Server2(ApisHandler2 apisHandler) throws ExecutionException, InterruptedException {
        long userId = apisHandler.getUserId().get();
        if (userId == 0) {
            throw new IllegalArgumentException(
                    "It is forbidden to use the root account to log in, as no images should be uploaded with this user"
            );
        }

        this.groups = apisHandler.getGroups(userId).get();
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "The server didn't return any group for user with ID %d", userId
            ));
        }

        this.owners = this.groups.stream()
                .map(Group::getOwners)
                .flatMap(List::stream)
                .distinct()
                .toList();

        this.connectedOwner = this.owners.stream()
                .filter(owner -> owner.id() == userId)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "The provided owner of ID %d was not found in the list returned by the server (%s)",
                        userId,
                        this.owners
                )));

        Group group = apisHandler.getDefaultGroup();
        if (group == null) {
            this.defaultGroup = this.groups.getFirst();
        } else {
            if (this.groups.contains(group)) {
                this.defaultGroup = group;
            } else {
                this.defaultGroup = this.groups.getFirst();
                logger.warn(
                        "The group {} was not found in the list returned by the server ({}). Using {}",
                        group,
                        this.groups,
                        this.defaultGroup
                );
            }
        }

        populate(apisHandler);
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
        return resources.getString("Web.Entities.RepositoryEntities.Server.server");
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
     * @return an unmodifiable list of groups the connected owner belong to
     */
    public List<Group> getGroups() {
        return groups;
    }

    /**
     * @return an unmodifiable list of owners belonging to groups the connected owner belong to
     */
    public List<Owner> getOwners() {
        return owners;
    }

    private void populate(ApisHandler2 apisHandler) {
        populateChildren(apisHandler.getProjects(), "projects");
        populateChildren(apisHandler.getScreens(), "screens");
        populateChildren(apisHandler.getOrphanedDatasets(), "orphaned datasets");
        populateChildren(apisHandler.getOrphanedPlates(), "orphaned plates");

        OrphanedFolder.create(apisHandler)
                .exceptionally(error -> {
                    logger.error("Error while creating orphaned folder", error);
                    return null;
                })
                .thenAccept(orphanedFolder -> {
                    synchronized (this) {
                        childrenTypesPopulated++;
                    }

                    if (orphanedFolder != null && orphanedFolder.hasChildren()) {
                        children.add(orphanedFolder);
                    }
                });
    }

    private <T extends RepositoryEntity> void populateChildren(CompletableFuture<List<T>> request, String name) {
        request
                .exceptionally(error -> {
                    logger.error(String.format("Error while retrieving %s", name), error);
                    return List.of();
                })
                .thenAccept(newChildren -> {
                    synchronized (this) {
                        childrenTypesPopulated++;
                    }
                    children.addAll(newChildren);
                });
    }
}
