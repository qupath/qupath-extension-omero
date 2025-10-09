package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A server entity represents an OMERO entity belonging to the project/dataset/image
 * or the screen/plate/plate acquisition/well hierarchy.
 */
public abstract class ServerEntity implements RepositoryEntity {

    protected final long id;
    protected final String name;
    protected final SimpleEntity owner;
    protected final SimpleEntity group;
    protected final URI webServerUri;

    /**
     * Create a server entity.
     *
     * @param id the ID of this entity
     * @param name the name of this entity. Can be null
     * @param owner the experimenter that owns this entity. Can be null
     * @param group the group that owns this entity. Can be null
     * @param webServerUri the URI of the web server owning this entity
     * @throws NullPointerException if the provided web server URI is null
     */
    protected ServerEntity(long id, String name, OmeroExperimenter owner, OmeroExperimenterGroup group, URI webServerUri) {
        this.id = id;
        this.name = name;
        this.owner = owner == null ? null : new SimpleEntity(owner.id(), owner.fullName());
        this.group = group == null ? null : new SimpleEntity(group.id(), group.name());
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
     * @return the ID and the full name of the experimenter that owns this entity, or an empty Optional if not defined
     */
    public Optional<SimpleEntity> getOwner() {
        return Optional.ofNullable(owner);
    }

    /**
     * @return ID and the name of the group that owns this entity, or an empty Optional if not defined
     */
    public Optional<SimpleEntity> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * @return the name of this entity, or an empty Optional if not defined
     */
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * @return a list of attributes of this entity
     */
    public abstract List<Attribute> getAttributes();
}
