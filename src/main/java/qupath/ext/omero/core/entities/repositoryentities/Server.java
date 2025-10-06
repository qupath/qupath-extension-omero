package qupath.ext.omero.core.entities.repositoryentities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.permissions.Experimenter;
import qupath.ext.omero.core.entities.permissions.ExperimenterGroup;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Server implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final ApisHandler apisHandler;
    private final List<ExperimenterGroup> groups;
    private final List<Experimenter> experimenters;
    private final Experimenter connectedExperimenter;
    private final ExperimenterGroup defaultGroup;

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

        this.experimenters = this.groups.stream()
                .map(ExperimenterGroup::getExperimenters)
                .flatMap(List::stream)
                .distinct()
                .toList();
        logger.debug("Got experimenters {} for server", experimenters);

        this.connectedExperimenter = this.experimenters.stream()
                .filter(experimenter -> experimenter.getId() == userId)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "The provided experimenter of ID %d was not found in the list returned by the server (%s)",
                        userId,
                        this.experimenters
                )));
        logger.debug("Got connected experimenter {} for server", connectedExperimenter);

        this.defaultGroup = apisHandler.getDefaultGroupId()
                .map(groupId -> {
                    Optional<ExperimenterGroup> foundGroup = groups.stream().filter(group -> group.getId() == groupId).findAny();

                    if (foundGroup.isPresent()) {
                        return foundGroup.get();
                    } else {
                        logger.warn(
                                "The group {} was not found in the list returned by the server ({}). Using {}",
                                groupId,
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
                            "Error when getting some children of server belonging to experimenter with ID {} and group with ID {}",
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
                "Server containing groups %s, experimenters %s, connected experimenter %s and default group %s",
                groups,
                experimenters,
                connectedExperimenter,
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
    public ExperimenterGroup getDefaultGroup() {
        return defaultGroup;
    }

    /**
     * Get the experimenter connected to this server. This can be the authenticated user
     * or the public user.
     *
     * @return the experimenter connected to this server
     */
    public Experimenter getConnectedExperimenter() {
        return connectedExperimenter;
    }

    /**
     * @return an unmodifiable list of groups the connected experimenter belongs to (except for the
     * 'user' and 'system' groups), or all groups of the server if the connected user is an admin
     */
    public List<ExperimenterGroup> getGroups() {
        return groups;
    }

    /**
     * @return an unmodifiable list of experimenters belonging to groups the connected experimenter belong to,
     * or all experimenters of the server if the connected user is an admin. Note that non-visible experimenters
     * (for example experimenters of private groups) are not included
     */
    public List<Experimenter> getExperimenters() {
        return experimenters;
    }
}
