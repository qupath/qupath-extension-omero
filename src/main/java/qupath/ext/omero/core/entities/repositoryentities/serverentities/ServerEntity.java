package qupath.ext.omero.core.entities.repositoryentities.serverentities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A server entity represents an OMERO entity belonging to the project/dataset/image
 * or the screen/plate/plate acquisition/well hierarchy.
 */
public abstract class ServerEntity implements RepositoryEntity {

    private static final Logger logger = LoggerFactory.getLogger(ServerEntity.class);
    protected transient URI webServerURI;
    @SerializedName(value = "@id") protected long id;
    @SerializedName(value = "Name") protected String name;
    private Owner owner = Owner.getAllMembersOwner();
    private Group group = Group.getAllGroupsGroup();

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
     * Indicates if this entity belongs to the provided group and owner.
     *
     * @param groupFilter  the group the entity should belong to
     * @param ownerFilter  the owner the entity should belong to
     * @return whether this entity matches all the filters
     */
    public boolean isFilteredByGroupOwner(Group groupFilter, Owner ownerFilter) {
        return (groupFilter == null || groupFilter == Group.getAllGroupsGroup() || group.equals(groupFilter)) &&
                (ownerFilter == null || ownerFilter == Owner.getAllMembersOwner() || owner.equals(ownerFilter));
    }

    /**
     * Returns the <b>name</b> of an attribute associated with this entity.
     *
     * @param informationIndex the index of the attribute
     * @return the attribute name corresponding to the index, or an empty String if the index is out of bound
     */
    public abstract String getAttributeName(int informationIndex);

    /**
     * Returns the <b>value</b> of an attribute associated with this entity.
     *
     * @param informationIndex the index of the attribute
     * @return the attribute value corresponding to the index, or an empty String if the index is out of bound
     */
    public abstract String getAttributeValue(int informationIndex);

    /**
     * @return the total number of attributes this entity has
     */
    public abstract int getNumberOfAttributes();

    /**
     * Creates a stream of entities from a list of JSON elements.
     * If an entity cannot be created from a JSON element, it is discarded.
     *
     * @param jsonElements  the JSON elements supposed to represent server entities
     * @param uri  the URI of the corresponding web server
     * @return a stream of server entities
     */
    public static Stream<ServerEntity> createFromJsonElements(List<JsonElement> jsonElements, URI uri) {
        return jsonElements.stream()
                .map(jsonElement -> createFromJsonElement(jsonElement, uri))
                .flatMap(Optional::stream);
    }

    /**
     * Creates a server entity from a JSON element.
     *
     * @param jsonElement  the JSON element supposed to represent a server entity
     * @param uri  the URI of the corresponding web server
     * @return a server entity, or an empty Optional if it was impossible to create
     */
    public static Optional<ServerEntity> createFromJsonElement(JsonElement jsonElement, URI uri) {
        Gson deserializer = new GsonBuilder().registerTypeAdapter(ServerEntity.class, new ServerEntityDeserializer(uri)).setLenient().create();

        try {
            return Optional.ofNullable(deserializer.fromJson(jsonElement, ServerEntity.class));
        } catch (JsonSyntaxException e) {
            logger.error("Error when deserializing " + jsonElement, e);
            return Optional.empty();
        }
    }

    /**
     * @return the OMERO ID associated with this entity
     */
    public long getId() {
        return id;
    }

    /**
     * @return the OMERO owner of this entity
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @return the OMERO group of this entity
     */
    public Group getGroup() {
        return group;
    }

    private record ServerEntityDeserializer(URI uri) implements JsonDeserializer<ServerEntity> {
        @Override
        public ServerEntity deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            try {
                String type = json.getAsJsonObject().get("@type").getAsString().toLowerCase();

                ServerEntity serverEntity = null;
                if (Image.isImage(type)) {
                    serverEntity = context.deserialize(json, Image.class);
                } else if (Dataset.isDataset(type)) {
                    serverEntity = context.deserialize(json, Dataset.class);
                } else if (Project.isProject(type)) {
                    serverEntity = context.deserialize(json, Project.class);
                } else if (Screen.isScreen(type)) {
                    serverEntity = context.deserialize(json, Screen.class);
                } else if (Plate.isPlate(type)) {
                    serverEntity = context.deserialize(json, Plate.class);
                } else if (PlateAcquisition.isPlateAcquisition(type)) {
                    serverEntity = context.deserialize(json, PlateAcquisition.class);
                } else if (Well.isWell(type)) {
                    serverEntity = context.deserialize(json, Well.class);
                } else {
                    logger.warn("Unsupported type {} when deserializing {}", type, json);
                }

                if (serverEntity != null) {
                    serverEntity.webServerURI = uri;

                    Owner owner = context.deserialize(((JsonObject) json).get("omero:details").getAsJsonObject().get("owner"), Owner.class);
                    if (owner != null) {
                        serverEntity.owner = owner;
                    }

                    Group group = context.deserialize(((JsonObject) json).get("omero:details").getAsJsonObject().get("group"), Group.class);
                    if (group != null) {
                        serverEntity.group = group;
                    }
                }

                return serverEntity;
            } catch (Exception e) {
                logger.error("Could not deserialize " + json, e);
                return null;
            }
        }
    }
}
