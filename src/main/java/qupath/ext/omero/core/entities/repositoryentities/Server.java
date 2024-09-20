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
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * A server is the top element in the OMERO entity hierarchy.
 * It contains one {@link OrphanedFolder}, and zero or more projects, orphaned datasets, screen, and orphaned plates (described in
 * {@link qupath.ext.omero.core.entities.repositoryentities.serverentities server entities}).
 */
public class Server implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final ObservableList<RepositoryEntity> children = FXCollections.observableArrayList();
    private final ObservableList<RepositoryEntity> childrenImmutable = FXCollections.unmodifiableObservableList(children);
    private final List<Owner> owners;
    private final List<Group> groups;
    private final Owner defaultOwner;
    private final Group defaultGroup;
    private volatile boolean isPopulating = false;

    private Server(ApisHandler apisHandler, List<Owner> owners, List<Group> groups, Owner defaultOwner, Group defaultGroup) {
        this.owners = Stream.concat(owners.stream(), Stream.of(Owner.getAllMembersOwner())).toList();
        this.groups = Stream.concat(groups.stream(), Stream.of(Group.getAllGroupsGroup())).toList();

        this.defaultOwner = defaultOwner;
        this.defaultGroup = defaultGroup;

        populate(apisHandler);
    }

    @Override
    public String toString() {
        return String.format("Server containing the following children: %s", children);
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
     *     Create the server. This creates the orphaned folder and populates the
     *     children (projects and orphaned datasets) of the server.
     * </p>
     * <p>
     *     Call {@link #create(ApisHandler, Group, int)} if you want to specify a default group
     *     and a default user.
     * </p>
     *
     * @param apisHandler  the APIs handler of the browser
     * @return the new server, or an empty Optional if the creation failed
     */
    public static CompletableFuture<Optional<Server>> create(ApisHandler apisHandler) {
        return create(apisHandler, null, -1);
    }

    /**
     * Same as {@link #create(ApisHandler)}, but by specifying a default group
     * and a default user.
     *
     * @param apisHandler  the APIs handler of the browser
     * @param defaultGroup  the default group of this server. This is usually the group
     *                      of the connected user
     * @param defaultUserId  the ID of the default owner of this server. This is usually the connected user
     * @return the new server, or an empty Optional if the creation failed
     */
    public static CompletableFuture<Optional<Server>> create(ApisHandler apisHandler, Group defaultGroup, int defaultUserId) {
        return CompletableFuture.supplyAsync(() -> {
            if (defaultUserId == 0) {
                logger.error("It is forbidden to use the root account to log in, as no images should be uploaded with this user");
                return Optional.empty();
            }

            List<Group> groups;
            try {
                groups = apisHandler.getGroups().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error while retrieving groups", e);
                return Optional.empty();
            }

            if (groups.isEmpty()) {
                logger.error("The server didn't return any group");
                return Optional.empty();
            }
            if (defaultGroup != null && !groups.contains(defaultGroup)) {
                logger.error("The default group was not found in the list returned by the server");
                return Optional.empty();
            }

            List<Owner> owners;
            try {
                owners = apisHandler.getOwners().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error while retrieving owners", e);
                return Optional.empty();
            }
            Owner defaultOwner = null;
            if (defaultUserId > -1) {
                defaultOwner = owners.stream()
                        .filter(owner -> owner.id() == defaultUserId)
                        .findAny()
                        .orElse(null);
            }

            if (owners.isEmpty()) {
                logger.error("The server didn't return any owner");
                return Optional.empty();
            }
            if (defaultOwner == null && defaultUserId > -1) {
                logger.error("The provided owner was not found in the list returned by the server");
                return Optional.empty();
            }

            return Optional.of(new Server(
                    apisHandler,
                    owners,
                    groups,
                    defaultOwner,
                    defaultGroup
            ));
        });
    }

    /**
     * <p>Get the default group of this server. This is usually the group of the connected user.</p>
     *
     * @return the default group of this server, or an empty Optional if no default group was set
     * (usually when the user is not authenticated)
     */
    public Optional<Group> getDefaultGroup() {
        return Optional.ofNullable(defaultGroup);
    }

    /**
     * <p>Get the default owner of this server. This is usually the connected user.</p>
     *
     * @return the default owner of this server, or an empty Optional if no default owner was set
     * (usually when the user is not authenticated)
     */
    public Optional<Owner> getDefaultOwner() {
        return Optional.ofNullable(defaultOwner);
    }

    /**
     * @return an unmodifiable list of groups of this server. This includes
     * the default group
     */
    public List<Group> getGroups() {
        return groups;
    }

    /**
     * @return an unmodifiable list of owners of this server. This includes
     * the default owner
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
