package qupath.ext.omero.core.entities.repositoryentities2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.ServerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Server implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final ApisHandler apisHandler;
    private final List<Group> groups;
    private final List<Owner> owners;
    private final Owner connectedOwner;
    private final Group defaultGroup;

    public Server(ApisHandler apisHandler) throws ExecutionException, InterruptedException {
        this.apisHandler = apisHandler;

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
    }

    @Override
    public boolean hasChildren() {
        return true;
    }

    @Override
    public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletableFuture<? extends List<? extends ServerEntity>>> requests = List.of(
                    apisHandler.getProjects(ownerId, groupId),
                    apisHandler.getScreens(ownerId, groupId),
                    apisHandler.getOrphanedDatasets(ownerId, groupId),
                    apisHandler.getOrphanedPlates(ownerId, groupId)
            );

            List<RepositoryEntity> children = new ArrayList<>();
            for (var request: requests) {
                try {
                    children.addAll(request.get());
                } catch (InterruptedException | ExecutionException e) {
                    logger.error(
                            "Error when getting some children of server belonging to owner with ID {} and group with ID {}",
                            ownerId,
                            groupId
                    );
                }
            }

            children.add(new OrphanedFolder(apisHandler));

            return children;
        });
    }

    @Override
    public String getLabel() {
        return resources.getString("Entities.RepositoryEntities.Server.server");
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
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Server server))
            return false;
        return Objects.equals(server.apisHandler, apisHandler);
    }

    @Override
    public int hashCode() {
        return apisHandler.hashCode();
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
}
