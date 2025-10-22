package qupath.ext.omero.core.apis.commonentities.shapes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.geom.Point2;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Color;
import java.awt.geom.Area;
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
    private static final String NO_CLASS = "NoClass";
    private static final String NO_PARENT = "NoParent";
    private static final String NO_NAME = "NoName";
    private static final String TEXT_DELIMITER = ":";
    private static final String CLASS_DELIMITER = "&";
    private static final String POINT_DELIMITER = " ";
    private static final String POINT_COORDINATE_DELIMITER = ",";
    private final long id;
    private final long roiId;
    private final UUID uuid;
    private final String name;
    private final UUID parentUuid;
    private final PathClass pathClass;
    private final ShapeType shapeType;
    private final Color fillColor;
    private final Color strokeColor;
    private final boolean locked;
    private final int c;
    private final int z;
    private final int t;
    private final SimpleEntity owner;

    /**
     * Create a shape from a list of parameters.
     *
     * @param id the shape unique ID
     * @param roiId in OMERO, a ROI contains one or more shapes. This parameter is the ID of the ROI containing this shape
     * @param text a text describing the shape. Can be null
     * @param fillColor the fill color of the shape. Can be null
     * @param strokeColor the stroke color of the shape. Can be null
     * @param locked whether this shape is locked
     * @param c the 0-based channel index of the shape
     * @param z the 0-based z-stack index of the shape
     * @param t the 0-based timepoint index of the shape
     * @param owner the ID and the full name of the experimenter owning this shape. Can be null
     */
    protected Shape(
            long id,
            long roiId,
            String text,
            Color fillColor,
            Color strokeColor,
            boolean locked,
            int c,
            int z,
            int t,
            SimpleEntity owner
    ) {
        this.id = id;
        this.roiId = roiId;

        UUID uuid = createUuid(getFieldInText(2, text));
        this.uuid = uuid == null ? UUID.randomUUID() : uuid;

        this.name = getFieldInText(4, text);

        this.parentUuid = createUuid(getFieldInText(3, text));

        String pathClass = getFieldInText(1, text);
        if (pathClass == null) {
            this.pathClass = PathClass.NULL_CLASS;
        } else {
            List<String> names = Arrays.stream(pathClass.split(CLASS_DELIMITER)).toList();

            if (names.size() == 1 && names.getFirst().equals(NO_CLASS)) {
                this.pathClass = PathClass.NULL_CLASS;
            } else {
                this.pathClass = PathClass.fromCollection(names);
            }
        }

        String shapeTypeText = getFieldInText(0, text);
        if (shapeTypeText == null) {
            this.shapeType = ShapeType.ANNOTATION;
        } else {
            this.shapeType = Arrays.stream(ShapeType.values())
                    .filter(shapeType1 -> shapeType1.getDisplayName().equals(shapeTypeText))
                    .findAny()
                    .orElse(ShapeType.ANNOTATION);
        }

        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.locked = locked;
        this.c = c;
        this.z = z;
        this.t = t;
        this.owner = owner;
    }

    /**
     * Create a shape from a {@link PathObject}.
     *
     * @param pathObject the {@link PathObject} to create the shape from
     * @param fillColor whether this shape should have a fill color
     * @throws NullPointerException if the provided path object is null
     */
    protected Shape(PathObject pathObject, boolean fillColor) {
        this.id = 0;
        this.roiId = 0;
        this.uuid = pathObject.getID();
        this.name = pathObject.getName();
        this.parentUuid = pathObject.getParent() == null ? null : pathObject.getParent().getID();
        this.pathClass = pathObject.getPathClass() == null ? PathClass.NULL_CLASS : pathObject.getPathClass();
        this.shapeType = pathObject.isDetection() ? ShapeType.DETECTION : ShapeType.ANNOTATION;

        this.strokeColor = new Color(
                pathObject.getPathClass() == null ? PathPrefs.colorDefaultObjectsProperty().get() : pathObject.getPathClass().getColor()
        );
        this.fillColor = fillColor ? ColorToolsAwt.getMoreTranslucentColor(this.strokeColor) : new Color(0, 0, 0, 0);

        this.locked = pathObject.isLocked();
        this.c = pathObject.getROI().getC();
        this.z = pathObject.getROI().getZ();
        this.t = pathObject.getROI().getT();
        this.owner = null;
    }

    /**
     * Create a list of PathObjects corresponding to the provided shapes. The returned
     * list won't include path objects with parents; they can be retrieved with
     * {@link PathObject#getChildObjects()} on the elements of the returned list.
     *
     * @param shapes the shapes to convert to path objects
     * @return a list of PathObjects corresponding to the provided shapes
     * @throws NullPointerException if the provided list is null
     */
    public static List<PathObject> createPathObjects(List<Shape> shapes) {
        logger.debug("Creating path objects from shapes {}", shapes);

        List<UUID> uuids = shapes.stream()
                .map(shape -> shape.uuid)
                .distinct()
                .toList();

        Map<UUID, PathObject> idToPathObject = new HashMap<>();
        Map<UUID, UUID> idToParentId = new HashMap<>();
        for (UUID uuid: uuids) {
            List<Shape> shapesWithUuid = shapes.stream()
                    .filter(shape -> uuid.equals(shape.uuid))
                    .toList();
            List<UUID> parentUuid = shapesWithUuid.stream()
                    .map(shape -> shape.parentUuid)
                    .filter(Objects::nonNull)
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
     * Create a compact JSON representation of this shape corresponding to an OMERO shape.
     *
     * @return a string containing a JSON representation of this shape
     */
    public abstract String createJson();

    /**
     * In OMERO, a ROI contains one or more shapes. This old ID is "roiID:shapeID".
     *
     * @return "roiID:shapeID" with the corresponding values
     */
    public String getOldId() {
        return String.format("%d:%d", roiId, id);
    }

    /**
     * @return the ID and the full name (first, middle and last name) of the experimenter owning this shape, or an empty Optional if not found
     */
    public Optional<SimpleEntity> getOwner() {
        return Optional.ofNullable(owner);
    }

    /**
     * @return the ID of this shape
     */
    protected long getId() {
        return id;
    }

    /**
     * @return a text representing the type, the path class, the UUID, and the parent UUID of this shape
     */
    protected String getText() {
        return String.format(
                "%s%s%s%s%s%s%s%s%s",
                shapeType.getDisplayName(),
                TEXT_DELIMITER,
                pathClass == PathClass.NULL_CLASS ?
                        NO_CLASS :
                        pathClass.toString().replaceAll(":", CLASS_DELIMITER),
                TEXT_DELIMITER,
                uuid.toString(),
                TEXT_DELIMITER,
                parentUuid == null ? NO_PARENT : parentUuid.toString(),
                TEXT_DELIMITER,
                name == null ? NO_NAME : name
        );
    }

    /**
     * @return the fill color of this shape
     */
    protected Optional<Color> getFillColor() {
        return Optional.ofNullable(fillColor);
    }

    /**
     * @return the stroke color of this shape
     */
    protected Optional<Color> getStrokeColor() {
        return Optional.ofNullable(strokeColor);
    }

    /**
     * @return whether this shape is locked
     */
    protected boolean getLocked() {
        return locked;
    }

    /**
     * @return the {@link ImagePlane} indicating where this shape is located
     */
    protected ImagePlane getPlane() {
        return ImagePlane.getPlaneWithChannel(c, z, t);
    }

    /**
     * Convert a {@link Color} to its integer RGBA representation.
     *
     * @param color the color to convert
     * @return the integer RGBA representation of the provided color
     * @throws NullPointerException if the provided color is null
     */
    protected static int colorToRgba(Color color) {
        return (color.getRed()<<24) + (color.getGreen()<<16) + (color.getBlue()<<8) + color.getAlpha();
    }

    /**
     * Conver an integer RGBA representation of a color to a {@link Color}.
     *
     * @param rgba the integer RGBA representation of a color to convert
     * @return a {@link Color} corresponding to the provided color
     */
    protected static Color rgbaToColor(int rgba) {
        return new Color((rgba >> 24) & 0xff, (rgba >> 16) & 0xff, (rgba >> 8) & 0xff, rgba & 0xff);
    }

    /**
     * @return a new {@link ROI} corresponding to this shape
     */
    protected abstract ROI createRoi();

    /**
     * Parse the OMERO string representing points into a list.
     *
     * @param pointsString a String describing a list of points returned by the OMERO API,
     *                     for example "2,3 4,2 7,9"
     * @return a list of points corresponding to the input
     * @throws NullPointerException if the provided string is null
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
     * Converts the specified list of {@link Point2} into an OMERO-friendly text.
     *
     * @return string of points
     * @throws NullPointerException if the provided list is null
     */
    protected static String pointsToString(List<Point2> points) {
        return points.stream()
                .map(point -> String.format("%s%s%s", point.getX(), POINT_COORDINATE_DELIMITER, point.getY()))
                .collect(Collectors.joining(POINT_DELIMITER));
    }

    private static String getFieldInText(int fieldIndex, String text) {
        if (text != null && text.split(TEXT_DELIMITER).length > fieldIndex) {
            return text.split(TEXT_DELIMITER)[fieldIndex];
        } else {
            return null;
        }
    }

    private static UUID createUuid(String uuid) {
        if (uuid == null) {
            return null;
        } else {
            try {
                return UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                logger.debug("Cannot create UUID from {}", uuid);
                return null;
            }
        }
    }

    private static void warnIfDuplicateAttribute(List<Shape> shapes, List<?> attributes, String type) {
        if (attributes.size() > 1) {
            logger.warn("The shapes {} have a different {}: {}. Only the first one will considered", shapes, type, attributes);
        }
    }

    private static PathObject createPathObject(List<Shape> shapes) {
        logger.debug("Creating single path object from shapes {}", shapes);

        if (shapes.isEmpty()) {
            return null;
        }

        List<PathClass> pathClasses = shapes.stream().map(shape -> shape.pathClass).distinct().toList();
        List<ShapeType> types = shapes.stream().map(shape -> shape.shapeType).distinct().toList();
        List<UUID> uuids = shapes.stream().map(shape -> shape.uuid).distinct().toList();
        List<Boolean> locked = shapes.stream().map(shape -> shape.locked).distinct().toList();
        List<String> names = shapes.stream()
                .map(shape -> shape.name)
                .filter(Objects::nonNull)
                .filter(name -> !name.equals(NO_NAME))
                .distinct().toList();

        warnIfDuplicateAttribute(shapes, pathClasses, "class");
        warnIfDuplicateAttribute(shapes, types, "type (annotation/detection)");
        warnIfDuplicateAttribute(shapes, uuids, "UUID");
        warnIfDuplicateAttribute(shapes, locked, "locked");
        warnIfDuplicateAttribute(shapes, names, "names");

        PathObject pathObject = switch (types.getFirst()) {
            case ANNOTATION -> {
                if (pathClasses.getFirst().equals(PathClass.NULL_CLASS)) {
                    yield PathObjects.createAnnotationObject(createRoi(shapes));
                } else {
                    yield PathObjects.createAnnotationObject(createRoi(shapes), pathClasses.getFirst());
                }
            }
            case DETECTION -> {
                if (pathClasses.getFirst().equals(PathClass.NULL_CLASS)) {
                    yield PathObjects.createDetectionObject(createRoi(shapes));
                } else {
                    yield PathObjects.createDetectionObject(createRoi(shapes), pathClasses.getFirst());
                }
            }
        };

        pathObject.setID(uuids.getFirst());
        pathObject.setLocked(locked.getFirst());

        if (!names.isEmpty()) {
            pathObject.setName(names.getFirst());
        }

        logger.debug("Created path object {} from shapes {}", pathObject, shapes);
        return pathObject;
    }

    /**
     * Create a single {@link ROI} from the provided list of shapes. This is done by:
     * <ul>
     *     <li>Converting each provided {@link Shape} to a {@link ROI}. This gives a list of {@link ROI}.</li>
     *     <li>Every point of the list of ROI are combined with the {@link RoiTools.CombineOp#ADD} operation.</li>
     *     <li>Every other {@link ROI} of the list are combined with the XOR operation.</li>
     *     <li>The resulting two {@link ROI} are combined with the {@link RoiTools.CombineOp#ADD} operation (if they exist).</li>
     * </ul>
     * The {@link RoiTools.CombineOp#ADD} operation is used for points because they don't support the XOR operation.
     *
     * @param shapes the shapes to convert and combine to a {@link ROI}
     * @return the created {@link ROI}, or null if the provided list is empty
     */
    private static ROI createRoi(List<Shape> shapes) {
        List<ROI> rois = shapes.stream().map(Shape::createRoi).toList();

        List<ROI> pointRois = rois.stream().filter(ROI::isPoint).toList();
        ROI pointsRoi = pointRois.isEmpty() ? null : pointRois.getFirst();
        for (int i=1; i<pointRois.size(); i++) {
            pointsRoi = RoiTools.combineROIs(pointsRoi, rois.get(i), RoiTools.CombineOp.ADD);
        }

        List<ROI> nonPointRois = rois.stream().filter(roi -> !roi.isPoint()).toList();
        ROI nonPointsRoi = nonPointRois.isEmpty() ? null : nonPointRois.getFirst();
        for (int i=1; i<nonPointRois.size(); i++) {
            Area nonPointArea = new Area(nonPointsRoi.getShape());

            nonPointArea.exclusiveOr(new Area(nonPointRois.get(i).getShape()));

            nonPointsRoi = RoiTools.getShapeROI(nonPointArea, nonPointsRoi.getImagePlane());
        }

        if (pointsRoi == null && nonPointsRoi == null) {
            return null;
        } else if (pointsRoi == null) {
            return nonPointsRoi;
        } else if (nonPointsRoi == null) {
            return pointsRoi;
        } else {
            return RoiTools.combineROIs(pointsRoi, nonPointsRoi, RoiTools.CombineOp.ADD);
        }
    }
}
