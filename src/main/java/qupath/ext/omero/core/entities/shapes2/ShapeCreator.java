package qupath.ext.omero.core.entities.shapes2;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.shapes2.omeroshapes.OmeroEllipse;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

public class ShapeCreator {

    private static final Logger logger = LoggerFactory.getLogger(ShapeCreator.class);
    private static final Gson gson = new Gson();

    private ShapeCreator() {
        throw new AssertionError("This class is not instantiable.");
    }

    public Shape createShape(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException(String.format("The provided JSON %s is not a JSON object", json));
        }
        JsonObject jsonObject = json.getAsJsonObject();

        if (!jsonObject.has("@type") || !jsonObject.get("@type").isJsonPrimitive()) {
            throw new IllegalArgumentException(String.format("'@type' attribute not found in the provided JSON %s", json));
        }
        String type = jsonObject.get("@type").getAsString();

        return switch (type) {
            case OmeroEllipse.TYPE -> new Ellipse(gson.fromJson(json, OmeroEllipse.class));
            //TODO: others
            default -> throw new IllegalArgumentException(String.format("Unexpected type %s", type));
        };
    }

    public List<Shape> createShapes(PathObject pathObject, boolean fillColor) {
        ROI roi = pathObject.getROI();

        if (roi instanceof EllipseROI ellipseROI) {
            return List.of(new Ellipse(ellipseROI));
            //TODO: others
        } else {
            logger.warn("Unsupported path object {}. Cannot convert it to a shape", pathObject);
            return List.of();
        }
    }
}
