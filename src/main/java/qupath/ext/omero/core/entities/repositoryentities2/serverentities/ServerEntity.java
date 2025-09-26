package qupath.ext.omero.core.entities.repositoryentities2.serverentities;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import qupath.ext.omero.core.entities.repositoryentities2.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroDataset;

import java.net.URI;

public abstract class ServerEntity implements RepositoryEntity {

    private static final String TYPE = "@type";
    private static final Gson gson = new Gson();
    protected final long id;
    protected final URI webServerURI;

    protected ServerEntity(long id, URI webServerURI) {
        this.id = id;
        this.webServerURI = webServerURI;
    }

    public static ServerEntity createFromJsonElement(JsonElement jsonElement, URI webServerUri) {
        if (!jsonElement.isJsonObject()) {
            throw new IllegalArgumentException(String.format("The provided JSON element %s is not a JSON object", jsonElement));
        }
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (!jsonObject.has(TYPE) || !jsonObject.get(TYPE).isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format("The provided JSON object %s doesn't have a %s member", jsonObject, TYPE));
        }
        String type = jsonObject.get(TYPE).getAsString();

        //TODO: other entities
        if (Dataset.isDataset(type)) {
            return new Dataset(gson.fromJson(jsonObject, OmeroDataset.class), webServerUri);
        } else {
            throw new IllegalArgumentException(String.format("The provided JSON object %s was not recognized", jsonObject));
        }
    }

    public abstract String getAttributeName(int informationIndex);

    public abstract String getAttributeValue(int informationIndex);

    public abstract int getNumberOfAttributes();
}
