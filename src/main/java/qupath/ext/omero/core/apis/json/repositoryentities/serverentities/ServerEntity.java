package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A server entity represents an OMERO entity belonging to the project/dataset/image
 * or the screen/plate/plate acquisition/well hierarchy.
 */
public abstract class ServerEntity implements RepositoryEntity {

    protected final long id;
    protected final String name;
    protected final long ownerId;
    protected final long groupId;
    protected final URI webServerUri;

    /**
     * Create a server entity.
     *
     * @param id the ID of this entity
     * @param name the name of this entity. Can be null
     * @param ownerId the ID of the experimenter that owns this entity
     * @param groupId the ID of the group that owns this entity
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if the provided web server URI is null
     */
    protected ServerEntity(long id, String name, long ownerId, long groupId, URI webServerUri) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.groupId = groupId;
        this.webServerUri = Objects.requireNonNull(webServerUri);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof ServerEntity serverEntity))
            return false;
        return serverEntity.id == this.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    /**
     * @return the ID of this entity
     */
    public long getId() {
        return id;
    }

    /**
     * @return the ID of the experimenter that owns this entity
     */
    public long getOwnerId() {
        return ownerId;
    }

    /**
     * @return the ID of the group that owns this entity
     */
    public long getGroupId() {
        return groupId;
    }

    /**
     * @return the name of this entity, or an empty Optional if not defined
     */
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }
}
