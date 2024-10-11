package qupath.ext.omero.core.entities.repositoryentities;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A server is the top element in the OMERO entity hierarchy.
 * It contains one {@link OrphanedFolder}, and zero or more projects, orphaned datasets, screen, and orphaned plates (described in
 * {@link qupath.ext.omero.core.entities.repositoryentities.serverentities server entities}).
 */
public class Server implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int NUMBER_OF_CHILDREN_TYPES = 5;
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final ObservableList<RepositoryEntity> children = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private final ObservableList<RepositoryEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final List<Owner> owners;
    private final List<Group> groups;
    private final Owner connectedOwner;
    private final Group defaultGroup;
    private int childrenTypesPopulated = 0;
    private record UserIdGroups(long userId, List<Group> groups) {}

    private Server(ApisHandler apisHandler, List<Owner> owners, List<Group> groups, Owner connectedOwner, Group defaultGroup) {
        this.owners = owners;
        this.groups = groups;
        this.connectedOwner = connectedOwner;
        this.defaultGroup = defaultGroup;

        populate(apisHandler);
    }

    @Override
    public String toString() {
        return String.format("Server of user %s containing the following children: %s", connectedOwner, children);
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
     * <p>
     *     Create the server for an <b>unauthenticated</b> user. This also start populating the
     *     children (orphaned folder, projects, orphaned datasets...) of the server.
     * </p>
     * <p>
     *     Call {@link #create(ApisHandler, Group, long)} if you want to create the server for an
     *     authenticated user.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param apisHandler the APIs handler of the browser
     * @return a CompletableFuture (that may complete exceptionally) with the new server
     */
    public static CompletableFuture<Server> create(ApisHandler apisHandler) {
        return create(apisHandler, null, -1);
    }

    /**
     * Same as {@link #create(ApisHandler)}, but for creating the server for an
     * <b>authenticated</b> user.
     *
     * @param apisHandler the APIs handler of the browser
     * @param defaultGroup the default group of the provided user
     * @param userId the ID of the connected user. It should not correspond to the root account
     *               as no images should be uploaded with this user. In that case, this function will
     *               return an empty Optional
     * @return a CompletableFuture (that may complete exceptionally) with the new server
     */
    public static CompletableFuture<Server> create(ApisHandler apisHandler, Group defaultGroup, long userId) {
        if (userId == 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "It is forbidden to use the root account to log in, as no images should be uploaded with this user"
            ));
        }

        CompletableFuture<Long> userIdRequest = userId == -1 ?
                apisHandler.getPublicUserId() :
                CompletableFuture.completedFuture(userId);

        return userIdRequest.thenCompose(userIdResponse ->
                apisHandler.getGroups(userIdResponse).thenApply(groups -> new UserIdGroups(userIdResponse, groups))
        ).thenApply(userIdGroups -> {
            if (userIdGroups.groups().isEmpty()) {
                throw new RuntimeException(String.format(
                        "The server didn't return any group for user with ID %d", userIdGroups.userId()
                ));
            }

            List<Owner> owners = userIdGroups.groups().stream()
                    .map(Group::getOwners)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();

            Owner connectedOwner = owners.stream()
                    .filter(owner -> owner.id() == userIdGroups.userId())
                    .findAny()
                    .orElse(null);
            if (connectedOwner == null) {
                throw new RuntimeException(String.format(
                        "The provided owner of ID %d was not found in the list returned by the server (%s)",
                        userIdGroups.userId(),
                        owners
                ));
            }

            Group group;
            if (defaultGroup == null) {
                group = userIdGroups.groups().get(0);
            } else {
                if (userIdGroups.groups().contains(defaultGroup)) {
                    group = defaultGroup;
                } else {
                    group = userIdGroups.groups().get(0);
                    logger.warn(String.format(
                            "The provided default group (%s) was not found in the list returned by the server (%s). Using %s",
                            defaultGroup,
                            userIdGroups.groups(),
                            group
                    ));
                }
            }

            return new Server(
                    apisHandler,
                    owners,
                    userIdGroups.groups(),
                    connectedOwner,
                    group
            );
        });
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

    private void populate(ApisHandler apisHandler) {
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
