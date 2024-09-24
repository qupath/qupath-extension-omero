package qupath.ext.omero.core.entities.repositoryentities;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.util.List;
import java.util.Optional;
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
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final ObservableList<RepositoryEntity> children = FXCollections.observableList(new CopyOnWriteArrayList<>());
    private final ObservableList<RepositoryEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final List<Owner> owners;
    private final List<Group> groups;
    private final Owner connectedOwner;
    private final Group defaultGroup;
    private volatile boolean isPopulating = false;
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
    public ReadOnlyStringProperty getLabel() {
        return new SimpleStringProperty(resources.getString("Web.Entities.RepositoryEntities.Server.server"));
    }

    @Override
    public boolean isPopulatingChildren() {
        return isPopulating;
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
     *
     * @param apisHandler  the APIs handler of the browser
     * @return the new server, or an empty Optional if the creation failed
     */
    public static CompletableFuture<Optional<Server>> create(ApisHandler apisHandler) {
        return create(apisHandler, null, -1);
    }

    /**
     * Same as {@link #create(ApisHandler)}, but for creating the server for an
     * <b>authenticated</b> user.
     *
     * @param apisHandler  the APIs handler of the browser
     * @param defaultGroup  the default group of the provided user
     * @param userId  the ID of the connected user. It should not correspond to the root account
     *                as no images should be uploaded with this user. In that case, this function will
     *                return an empty Optional
     * @return the new server, or an empty Optional if the creation failed
     */
    public static CompletableFuture<Optional<Server>> create(ApisHandler apisHandler, Group defaultGroup, long userId) {
        if (userId == 0) {
            logger.error("It is forbidden to use the root account to log in, as no images should be uploaded with this user");
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Optional<Long>> userIdRequest = userId == -1 ?
                apisHandler.getPublicUserId() :
                CompletableFuture.completedFuture(Optional.of(userId));

        return userIdRequest.thenCompose(userIdResponse -> {
            if (userIdResponse.isPresent()) {
                return apisHandler.getGroups(userIdResponse.get()).thenApply(groups ->
                        new UserIdGroups(userIdResponse.get(), groups)
                );
            } else {
                return CompletableFuture.completedFuture(new UserIdGroups(-1, List.of()));
            }
        }).thenApply(userIdGroups -> {
            if (userIdGroups.userId() == -1) {
                logger.error("Could not retrieve user ID of public user");
                return Optional.empty();
            }
            if (userIdGroups.groups().isEmpty()) {
                logger.error(String.format("The server didn't return any group for user with ID %d", userIdGroups.userId()));
                return Optional.empty();
            }

            List<Owner> owners = userIdGroups.groups().stream()
                    .map(Group::getOwners)
                    .flatMap(List::stream)
                    .toList();

            Owner connectedOwner = owners.stream()
                    .filter(owner -> owner.id() == userIdGroups.userId())
                    .findAny()
                    .orElse(null);
            if (connectedOwner == null) {
                logger.error(String.format(
                        "The provided owner of ID %d was not found in the list returned by the server (%s)",
                        userIdGroups.userId(),
                        owners
                ));
                return Optional.empty();
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

            return Optional.of(new Server(
                    apisHandler,
                    owners,
                    userIdGroups.groups(),
                    connectedOwner,
                    group
            ));
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
        isPopulating = true;

        apisHandler.getProjects().thenCompose(projects -> {
            children.addAll(projects);

            return apisHandler.getScreens();
        }).thenCompose(screens -> {
            children.addAll(screens);

            return apisHandler.getOrphanedDatasets();
        }).thenCompose(orphanedDatasets -> {
            children.addAll(orphanedDatasets);

            return apisHandler.getOrphanedPlates();
        }).thenCompose(orphanedPlates -> {
            children.addAll(orphanedPlates);

            return OrphanedFolder.create(apisHandler);
        }).thenAccept(orphanedFolder -> {
            if (orphanedFolder.hasChildren()) {
                children.add(orphanedFolder);
            }

            isPopulating = false;
        });
    }
}
