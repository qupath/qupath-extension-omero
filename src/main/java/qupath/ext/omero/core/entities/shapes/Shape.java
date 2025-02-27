package qupath.ext.omero.core.entities.shapes;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.geom.Point2;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.GeometryROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Color;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An OMERO shape represents a region that can be drawn to an image.
 */
public abstract class Shape {

    private static final Logger logger = LoggerFactory.getLogger(Shape.class);
    protected static String TYPE_URL = "http://www.openmicroscopy.org/Schemas/OME/2016-06#";
    @SerializedName(value = "@type") private String type;
    @SerializedName(value = "@id") private int id;
    @SerializedName(value = "TheC") private Integer c;
    @SerializedName(value = "TheZ") private int z;
    @SerializedName(value = "TheT") private int t;
    @SerializedName(value = "Text", alternate = "text") private String text;
    @SerializedName(value = "Locked", alternate = "locked") private Boolean locked;
    @SerializedName(value = "FillColor", alternate = "fillColor") private int fillColor;
    @SerializedName(value = "StrokeColor", alternate = "strokeColor") private Integer strokeColor;
    @SerializedName(value = "oldId") private String oldId = "-1:-1";
    private transient UUID uuid;

    protected Shape(String type) {
        this.type = type;
    }

    /**
     * Class that deserializes a JSON into a shape
     */
    public static class GsonShapeDeserializer implements JsonDeserializer<Shape> {
        @Override
        public Shape deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            try {
                String type = json.getAsJsonObject().get("@type").getAsString();

                if (type.equalsIgnoreCase(Rectangle.TYPE))
                    return context.deserialize(json, Rectangle.class);
                if (type.equalsIgnoreCase(Ellipse.TYPE))
                    return context.deserialize(json, Ellipse.class);
                if (type.equalsIgnoreCase(Line.TYPE))
                    return context.deserialize(json, Line.class);
                if (type.equalsIgnoreCase(Polygon.TYPE))
                    return context.deserialize(json, Polygon.class);
                if (type.equalsIgnoreCase(Polyline.TYPE))
                    return context.deserialize(json, Polyline.class);
                if (type.equalsIgnoreCase(Point.TYPE))
                    return context.deserialize(json, Point.class);
                if (type.equalsIgnoreCase(Label.TYPE))
                    return context.deserialize(json, Label.class);

                logger.warn("Unsupported type {}", type);
                return null;
            } catch (Exception e) {
                logger.error("Could not deserialize {}", json, e);
                return null;
            }
        }
    }

    /**
     * Create a list of shapes from a path object.
     *
     * @param pathObject  the path object that represents one or more shapes
     * @param fillColor whether to fill the shapes with colors
     * @return a list of shapes corresponding to this path object
     */
    public static List<? extends Shape> createFromPathObject(PathObject pathObject, boolean fillColor) {
        ROI roi = pathObject.getROI();

        if (roi instanceof RectangleROI) {
            return List.of(new Rectangle(pathObject, fillColor));
        } else if (roi instanceof EllipseROI) {
            return List.of(new Ellipse(pathObject, fillColor));
        } else if (roi instanceof LineROI lineRoi) {
            return List.of(new Line(pathObject, lineRoi, fillColor));
        } else if (roi instanceof PolylineROI) {
            return List.of(new Polyline(pathObject, fillColor));
        } else if (roi instanceof PolygonROI) {
            return List.of(new Polygon(pathObject, pathObject.getROI(), fillColor));
        } else if (roi instanceof PointsROI) {
            return Point.create(pathObject, fillColor);
        } else if (roi instanceof GeometryROI) {
            logger.warn("OMERO shapes do not support holes. MultiPolygon will be split for OMERO compatibility.");

            return RoiTools.splitROI(RoiTools.fillHoles(roi)).stream()
                    .map(r -> new Polygon(pathObject, r, fillColor))
                    .toList();
        } else {
            logger.warn("Unsupported type: {}", roi.getRoiName());
            return List.of();
        }
    }

    /**
     * Create a list of PathObjects corresponding to the provided shapes. The returned
     * list won't include path objects with parents; they can be retrieved with
     * {@link PathObject#getChildObjects()} on the elements of the returned list.
     *
     * @param shapes the shapes to convert to path objects
     * @return a list of PathObjects corresponding to the provided shapes
     */
    public static List<PathObject> createPathObjects(List<? extends Shape> shapes) {
        List<UUID> uuids = shapes.stream()
                .map(Shape::getQuPathId)
                .distinct()
                .toList();

        Map<UUID, PathObject> idToPathObject = new HashMap<>();
        Map<UUID, UUID> idToParentId = new HashMap<>();
        for (UUID uuid: uuids) {
            List<? extends Shape> shapesWithUuid = shapes.stream()
                    .filter(shape -> uuid.equals(shape.getQuPathId()))
                    .toList();
            List<UUID> parentUuid = shapesWithUuid.stream()
                    .map(Shape::getQuPathParentId)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
            warnIfDuplicateAttribute(shapesWithUuid, parentUuid, "parent UUID");

            idToPathObject.put(uuid, createPathObject(shapesWithUuid));
            idToParentId.put(uuid, parentUuid.isEmpty() ? null : parentUuid.getFirst());
        }

        List<PathObject> pathObjects = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry: idToParentId.entrySet()) {
            if (idToPathObject.containsKey(entry.getValue())) {
                idToPathObject.get(entry.getValue()).addChildObject(idToPathObject.get(entry.getKey()));
            } else {
                pathObjects.add(idToPathObject.get(entry.getKey()));
            }
        }

        return pathObjects;
    }

    /**
     * Set the {@code oldId} field of this shape.
     * <p>
     * This corresponds to "roiID:shapeID" (see
     * <a href="https://docs.openmicroscopy.org/omero/latest/developers/json-api.html#rois-and-shapes">here</a>
     * for the difference between ROI ID and shape ID).
     *
     * @param roiID the ROI ID (as explained above)
     */
    public void setOldId(int roiID) {
        oldId = String.format("%d:%d", roiID, id);
    }

    /**
     * @return the {@code oldId} field of this shape (see {@link #setOldId(int)})
     */
    public String getOldId() {
        return oldId;
    }

    /**
     * @return the ROI that corresponds to this shape
     */
    protected abstract ROI createRoi();

    /**
     * Link this shape with a path object.
     * <p>
     * Its text will be formatted as {@code Type:Class1&Class2:ObjectID:ParentID},
     * for example {@code Annotation:NoClass:aba712b2-bbc2-4c05-bbba-d9fbab4d454f:NoParent}
     * or {@code Detection:Stroma:aba712b2-bbc2-4c05-bbba-d9fbab4d454f:205037ff-7dd7-4549-89d8-a4e3cbf61294}.
     *
     * @param pathObject the path object that should correspond to this shape
     * @param fillColor whether to fill the shape with colors
     */
    protected void linkWithPathObject(PathObject pathObject, boolean fillColor) {
        this.text = String.format(
                "%s:%s:%s:%s",
                pathObject.isDetection() ? "Detection" : "Annotation",
                pathObject.getPathClass() == null ? "NoClass" : pathObject.getPathClass().toString().replaceAll(":","&"),
                pathObject.getID().toString(),
                pathObject.getParent() == null ? "NoParent" : pathObject.getParent().getID().toString()
        );

        int color = pathObject.getPathClass() == null ? PathPrefs.colorDefaultObjectsProperty().get() : pathObject.getPathClass().getColor();
        this.strokeColor = ARGBToRGBA(color);
        this.fillColor = colorToRGBA(fillColor ? ColorToolsAwt.getMoreTranslucentColor(new Color(color)) : new Color(0, 0, 0, 0));
    }

    /**
     * Parse the OMERO string representing points into a list.
     *
     * @param pointsString a String describing a list of points returned by the OMERO API,
     *                     for example "2,3 4,2 7,9"
     * @return a list of points corresponding to the input
     */
    protected static List<Point2> parseStringPoints(String pointsString) {
        return Arrays.stream(pointsString.split(" "))
                .map(pointStr -> {
                    String[] point = pointStr.split(",");
                    if (point.length > 1) {
                        return new Point2(Double.parseDouble(point[0]), Double.parseDouble(point[1]));
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * @return the ImagePlane corresponding to this shape
     */
    protected ImagePlane getPlane() {
        return c == null ? ImagePlane.getPlane(z, t) : ImagePlane.getPlaneWithChannel(c, z, t);
    }

    /**
     * Converts the specified list of {@code Point2}s into an OMERO-friendly string
     * @return string of points
     */
    protected static String pointsToString(List<Point2> points) {
        return points.stream()
                .map(point -> point.getX() + "," + point.getY())
                .collect(Collectors.joining (" "));
    }

    /**
     * @return the QuPath ID contained in this shape, or a random UUID if not
     * found
     */
    protected UUID getQuPathId() {
        if (this.uuid == null) {
            if (text != null && text.split(":").length > 2) {
                String uuid = text.split(":")[2];
                try {
                    this.uuid = UUID.fromString(uuid);
                } catch (IllegalArgumentException e) {
                    logger.debug("Cannot create UUID from {}. Generating one", uuid);
                    this.uuid = UUID.randomUUID();
                }
            } else {
                this.uuid = UUID.randomUUID();
            }
        }

        return uuid;
    }

    /**
     * @return the color of this shape (can be null)
     */
    protected Integer getStrokeColor() {
        return strokeColor;
    }

    /**
     * @return whether this shape should be locked (can be null)
     */
    protected Boolean getLocked() {
        return locked;
    }

    private Optional<UUID> getQuPathParentId() {
        if (text != null && text.split(":").length > 3) {
            String uuid = text.split(":")[3];
            try {
                return Optional.of(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                logger.debug("Cannot create UUID from {}", uuid);
            }
        }
        return Optional.empty();
    }

    private static void warnIfDuplicateAttribute(List<? extends Shape> shapes, List<?> attributes, String type) {
        if (attributes.size() > 1) {
            logger.warn("The shapes {} have a different {}: {}. Only the first one will considered", shapes, type, attributes);
        }
    }

    private static PathObject createPathObject(List<? extends Shape> shapes) {
        if (shapes.isEmpty()) {
            return null;
        }

        List<PathClass> pathClasses = shapes.stream().map(Shape::getPathClass).distinct().toList();
        List<String> types = shapes.stream().map(Shape::getType).distinct().toList();
        List<UUID> uuids = shapes.stream().map(Shape::getQuPathId).distinct().toList();
        List<Integer> strokeColors = shapes.stream().map(Shape::getStrokeColor).distinct().toList();
        List<Boolean> locked = shapes.stream().map(Shape::getLocked).distinct().toList();

        warnIfDuplicateAttribute(shapes, pathClasses, "class");
        warnIfDuplicateAttribute(shapes, types, "type (annotation/detection)");
        warnIfDuplicateAttribute(shapes, uuids, "UUID");
        warnIfDuplicateAttribute(shapes, strokeColors, "color");
        warnIfDuplicateAttribute(shapes, locked, "locked");

        PathObject pathObject;
        if (types.getFirst().equals("Detection")) {
            pathObject = PathObjects.createDetectionObject(createRoi(shapes), pathClasses.getFirst());
        } else {
            pathObject = PathObjects.createAnnotationObject(createRoi(shapes), pathClasses.getFirst());
        }

        pathObject.setID(uuids.getFirst());

        if (strokeColors.getFirst() != null)
            pathObject.setColor(strokeColors.getFirst() >> 8);

        if (locked.getFirst() != null)
            pathObject.setLocked(locked.getFirst());

        return pathObject;
    }

    private static int ARGBToRGBA(int argb) {
        int a =  (argb >> 24) & 0xff;
        int r =  (argb >> 16) & 0xff;
        int g =  (argb >> 8) & 0xff;
        int b =  argb & 0xff;
        return (r<<24) + (g<<16) + (b<<8) + a;
    }

    private static int colorToRGBA(Color color) {
        return (color.getRed()<<24) + (color.getGreen()<<16) + (color.getBlue()<<8) + color.getAlpha();
    }

    private PathClass getPathClass() {
        if (text != null && text.split(":").length > 1 && !text.split(":")[1].isBlank()) {
            return PathClass.fromCollection(Arrays.stream(text.split(":")[1].split("&")).toList());
        } else {
            logger.debug("Path class not found in {}. Returning NoClass", text);

            return PathClass.fromString("NoClass");
        }
    }

    private String getType() {
        if (text != null && text.split(":").length > 0 && !text.split(":")[0].isBlank()) {
            return text.split(":")[0];
        } else {
            logger.debug("Type not found in {}. Returning Annotation", text);

            return "Annotation";
        }
    }

    private static ROI createRoi(List<? extends Shape> shapes) {
        if (shapes.isEmpty()) {
            return null;
        }

        List<ROI> rois = shapes.stream().map(Shape::createRoi).toList();
        ROI roi = rois.getFirst();
        for (int i=1; i<rois.size(); i++) {
            roi = RoiTools.combineROIs(roi, rois.get(i), RoiTools.CombineOp.ADD);
        }
        return roi;
    }
}
