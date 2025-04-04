package qupath.ext.omero.core.entities.shapes;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.permissions.OmeroDetails;
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
    private static final String ANNOTATION = "Annotation";
    private static final String DETECTION = "Detection";
    private static final String NO_CLASS = "NoClass";
    private static final String NO_PARENT = "NoParent";
    private static final String NO_NAME = "NoName";
    private static final String TEXT_DELIMITER = ":";
    private static final String CLASS_DELIMITER = "&";
    private static final String POINT_DELIMITER = " ";
    private static final String POINT_COORDINATE_DELIMITER = ",";
    private static final String TYPE_URL = "http://www.openmicroscopy.org/Schemas/OME/2016-06#";
    @SerializedName(value = "@type") private final String type;
    @SerializedName(value = "Text", alternate = "text") private final String text;
    @SerializedName(value = "FillColor", alternate = "fillColor") private final int fillColor;
    @SerializedName(value = "StrokeColor", alternate = "strokeColor") private final Integer strokeColor;
    @SerializedName(value = "Locked", alternate = "locked") private final Boolean locked;
    @SerializedName(value = "@id") private int id;
    @SerializedName(value = "TheC") private Integer c;
    @SerializedName(value = "TheZ") private int z;
    @SerializedName(value = "TheT") private int t;
    private String oldId = "-1:-1";
    @SerializedName(value = "omero:details") private OmeroDetails omeroDetails;
    private transient UUID uuid;

    /**
     * Create a new shape.
     *
     * @param type the type of the shape according to the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06">Open Microscopy Environment OME Schema</a>
     * @param pathObject the path object corresponding to this shape
     * @param fillColor whether to fill the ellipse with colors
     */
    protected Shape(String type, PathObject pathObject, boolean fillColor) {
        this.type = TYPE_URL + type;

        this.text = String.format(
                "%s%s%s%s%s%s%s%s%s",
                pathObject.isDetection() ? DETECTION : ANNOTATION,
                TEXT_DELIMITER,
                pathObject.getPathClass() == null ?
                        NO_CLASS :
                        pathObject.getPathClass().toString().replaceAll(":", CLASS_DELIMITER),
                TEXT_DELIMITER,
                pathObject.getID().toString(),
                TEXT_DELIMITER,
                pathObject.getParent() == null ? NO_PARENT : pathObject.getParent().getID().toString(),
                TEXT_DELIMITER,
                pathObject.getName() == null ? NO_NAME : pathObject.getName()
        );

        int color = pathObject.getPathClass() == null ? PathPrefs.colorDefaultObjectsProperty().get() : pathObject.getPathClass().getColor();
        this.strokeColor = ARGBToRGBA(color);
        this.fillColor = colorToRGBA(fillColor ? ColorToolsAwt.getMoreTranslucentColor(new Color(color)) : new Color(0, 0, 0, 0));

        this.locked = pathObject.isLocked();
    }

    /**
     * Class that deserializes a JSON into a shape
     */
    public static class GsonShapeDeserializer implements JsonDeserializer<Shape> {
        @Override
        public Shape deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            logger.trace("Deserializing {} to shape", json);

            try {
                String type = json.getAsJsonObject().get("@type").getAsString();

                if (Ellipse.isEllipse(type)) {
                    logger.trace("{} is an ellipse", json);
                    return context.deserialize(json, Ellipse.class);
                } else if (Label.isLabel(type)) {
                    logger.trace("{} is a label", json);
                    return context.deserialize(json, Label.class);
                } else if (Line.isLine(type)) {
                    logger.trace("{} is a line", json);
                    return context.deserialize(json, Line.class);
                } else if (Point.isPoint(type)) {
                    logger.trace("{} is a point", json);
                    return context.deserialize(json, Point.class);
                } else if (Polygon.isPolygon(type)) {
                    logger.trace("{} is a polygon", json);
                    return context.deserialize(json, Polygon.class);
                } else if (Polyline.isPolyline(type)) {
                    logger.trace("{} is a polyline", json);
                    return context.deserialize(json, Polyline.class);
                } else if (Rectangle.isRectangle(type)) {
                    logger.trace("{} is a rectangle", json);
                    return context.deserialize(json, Rectangle.class);
                } else {
                    logger.warn("Unsupported type {} to convert to shape", type);
                    return null;
                }
            } catch (Exception e) {
                logger.error("Could not deserialize {} to shape", json, e);
                return null;
            }
        }
    }

    /**
     * Create a list of shapes from a path object.
     *
     * @param pathObject the path object that represents one or more shapes
     * @param fillColor whether to fill the shapes with colors
     * @return a list of shapes corresponding to this path object
     */
    public static List<? extends Shape> createFromPathObject(PathObject pathObject, boolean fillColor) {
        logger.debug("Creating shapes from path object {} and fill color {}", pathObject, fillColor);

        ROI roi = pathObject.getROI();
        if (roi instanceof RectangleROI) {
            Rectangle rectangle = new Rectangle(pathObject, fillColor);
            logger.debug("{} is a rectangle, so returning a single rectangle {}", pathObject, rectangle);

            return List.of(rectangle);
        } else if (roi instanceof EllipseROI) {
            Ellipse ellipse = new Ellipse(pathObject, fillColor);
            logger.debug("{} is an ellipse, so returning a single ellipse {}", pathObject, ellipse);

            return List.of(ellipse);
        } else if (roi instanceof LineROI) {
            Line line = new Line(pathObject, fillColor);
            logger.debug("{} is a line, so returning a single line {}", pathObject, line);

            return List.of(line);
        } else if (roi instanceof PolylineROI) {
            Polyline polyline = new Polyline(pathObject, fillColor);
            logger.debug("{} is a polyline, so returning a single polyline {}", pathObject, polyline);

            return List.of(polyline);
        } else if (roi instanceof PolygonROI) {
            Polygon polygon = new Polygon(pathObject, pathObject.getROI(), fillColor);
            logger.debug("{} is a polygon, so returning a single polygon {}", pathObject, polygon);

            return List.of(polygon);
        } else if (roi instanceof PointsROI) {
            List<Point> points = Point.create(pathObject, fillColor);
            logger.debug("{} is a list of points, so returning a list of points {}", pathObject, points);

            return points;
        } else if (roi instanceof GeometryROI) {
            List<Polygon> polygons = RoiTools.splitROI(RoiTools.fillHoles(roi)).stream()
                    .map(r -> new Polygon(pathObject, r, fillColor))
                    .toList();
            logger.warn(
                    "{} is a geometry, so splitting it to convert it to a list of polygons {}." +
                            "Note that potential holes will be filled because OMERO shapes do not support holes",
                    pathObject,
                    polygons
            );

            return polygons;
        } else {
            logger.warn("Unsupported path object {}. Cannot convert it to a shape", pathObject);
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
        logger.debug("Creating path objects from shapes {}", shapes);

        List<UUID> uuids = shapes.stream()
                .map(Shape::getQuPathId)
                .distinct()
                .toList();

        Map<UUID, PathObject> idToPathObject = new HashMap<>();
        Map<UUID, UUID> idToParentId = new HashMap<>();
        for (UUID uuid: uuids) {
            List<? extends Shape> shapesWithUuid = shapes.stream()
                    .filter(shape -> uuid.equals(((Shape) shape).getQuPathId()))
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
        logger.debug("Got ID to parent ID {} and ID to path objects {}", idToParentId, idToPathObject);

        List<PathObject> pathObjects = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry: idToParentId.entrySet()) {
            if (idToPathObject.containsKey(entry.getValue())) {
                idToPathObject.get(entry.getValue()).addChildObject(idToPathObject.get(entry.getKey()));
            } else {
                pathObjects.add(idToPathObject.get(entry.getKey()));
            }
        }
        logger.debug("Returning path objects {} (that may have children)", pathObjects);

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
     * @return the full name of the owner owning this shape, or an empty Optional if not found
     */
    public Optional<String> getOwnerFullName() {
        return Optional.ofNullable(omeroDetails).flatMap(OmeroDetails::getOwnerFullName);
    }

    /**
     * @return the ROI that corresponds to this shape
     */
    protected abstract ROI createRoi();

    /**
     * Parse the OMERO string representing points into a list.
     *
     * @param pointsString a String describing a list of points returned by the OMERO API,
     *                     for example "2,3 4,2 7,9"
     * @return a list of points corresponding to the input
     */
    protected static List<Point2> parseStringPoints(String pointsString) {
        return Arrays.stream(pointsString.split(POINT_DELIMITER))
                .map(pointStr -> {
                    String[] point = pointStr.split(POINT_COORDINATE_DELIMITER);
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
     * Converts the specified list of {@code Point2}s into an OMERO-friendly string
     * @return string of points
     */
    protected static String pointsToString(List<Point2> points) {
        return points.stream()
                .map(point -> String.format("%f%s%f", point.getX(), POINT_COORDINATE_DELIMITER, point.getY()))
                .collect(Collectors.joining(POINT_DELIMITER));
    }

    /**
     * @return the ImagePlane corresponding to this shape
     */
    protected ImagePlane getPlane() {
        return c == null ? ImagePlane.getPlane(z, t) : ImagePlane.getPlaneWithChannel(c, z, t);
    }

    private UUID getQuPathId() {
        if (this.uuid == null) {
            if (text != null && text.split(TEXT_DELIMITER).length > 2) {
                String uuid = text.split(TEXT_DELIMITER)[2];
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

    private Optional<UUID> getQuPathParentId() {
        if (text != null && text.split(TEXT_DELIMITER).length > 3) {
            String uuid = text.split(TEXT_DELIMITER)[3];
            try {
                return Optional.of(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                logger.debug("Cannot create UUID from {}. Considering parent UUID does not exist", uuid);
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
        logger.debug("Creating single path object from shapes {}", shapes);

        if (shapes.isEmpty()) {
            return null;
        }

        List<PathClass> pathClasses = shapes.stream().map(Shape::getPathClass).distinct().toList();
        List<String> types = shapes.stream().map(Shape::getType).distinct().toList();
        List<UUID> uuids = shapes.stream().map(Shape::getQuPathId).distinct().toList();
        List<Boolean> locked = shapes.stream().map(shape -> ((Shape) shape).locked).distinct().toList();
        List<Optional<String>> names = shapes.stream().map(shape -> ((Shape) shape).getName()).distinct().toList();

        warnIfDuplicateAttribute(shapes, pathClasses, "class");
        warnIfDuplicateAttribute(shapes, types, "type (annotation/detection)");
        warnIfDuplicateAttribute(shapes, uuids, "UUID");
        warnIfDuplicateAttribute(shapes, locked, "locked");
        warnIfDuplicateAttribute(shapes, names, "names");

        PathObject pathObject;
        if (types.getFirst().equals(DETECTION)) {
            if (pathClasses.getFirst().equals(PathClass.NULL_CLASS)) {
                pathObject = PathObjects.createDetectionObject(createRoi(shapes));
            } else {
                pathObject = PathObjects.createDetectionObject(createRoi(shapes), pathClasses.getFirst());
            }
        } else {
            if (pathClasses.getFirst().equals(PathClass.NULL_CLASS)) {
                pathObject = PathObjects.createAnnotationObject(createRoi(shapes));
            } else {
                pathObject = PathObjects.createAnnotationObject(createRoi(shapes), pathClasses.getFirst());
            }
        }

        pathObject.setID(uuids.getFirst());

        if (locked.getFirst() != null) {
            pathObject.setLocked(locked.getFirst());
        }

        if (names.getFirst().isPresent()) {
            pathObject.setName(names.getFirst().get());
        }

        logger.debug("Created path object {} from shapes {}", pathObject, shapes);
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
        if (text != null && text.split(TEXT_DELIMITER).length > 1 && !text.split(TEXT_DELIMITER)[1].isBlank()) {
            List<String> names = Arrays.stream(text.split(TEXT_DELIMITER)[1].split(CLASS_DELIMITER)).toList();

            if (names.size() == 1 && names.getFirst().equals(NO_CLASS)) {
                logger.debug("{} path class detected. Returning {}", NO_CLASS, PathClass.NULL_CLASS);
                return PathClass.NULL_CLASS;
            } else {
                return PathClass.fromCollection(names);
            }
        } else {
            logger.debug("Path class not found in {}. Returning {}", text, PathClass.NULL_CLASS);
            return PathClass.NULL_CLASS;
        }
    }

    private String getType() {
        if (text != null && text.split(TEXT_DELIMITER).length > 0 && !text.split(TEXT_DELIMITER)[0].isBlank()) {
            return text.split(TEXT_DELIMITER)[0];
        } else {
            logger.debug("Type not found in {}. Returning {}", text, ANNOTATION);
            return ANNOTATION;
        }
    }

    private Optional<String> getName() {
        if (text != null && text.split(TEXT_DELIMITER).length > 4 && !text.split(TEXT_DELIMITER)[4].isBlank()) {
            if (NO_NAME.equals(text.split(TEXT_DELIMITER)[4])) {
                logger.debug("{} name found in {}. Not setting any name", NO_NAME, text);
                return Optional.empty();
            } else {
                return Optional.of(text.split(TEXT_DELIMITER)[4]);
            }
        } else {
            logger.debug("Name not found in {}. Not setting any name", text);
            return Optional.empty();
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
