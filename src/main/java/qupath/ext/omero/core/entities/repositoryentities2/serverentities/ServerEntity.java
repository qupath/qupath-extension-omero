package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;

import java.net.URI;
import java.util.List;

public abstract class ServerEntity implements RepositoryEntity {

    protected final long id;
    protected final String name;
    protected final Owner owner;
    protected final Group group;
    protected final URI webServerUri;

    protected ServerEntity(long id, String name, Owner owner, Group group, URI webServerUri) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.group = group;
        this.webServerUri = webServerUri;
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

    public long getId() {
        return id;
    }

    public Owner getOwner() {//TODO: can be null
        return owner;
    }

    public Group getGroup() {
        return group;
    }

    public abstract List<Attribute> getAttributes();
}
