package qupath.ext.omero.core.entities.shapes2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public abstract class Shape {

    private static final Logger logger = LoggerFactory.getLogger(Shape.class);
    private static final String NO_CLASS = "NoClass";
    private static final String NO_PARENT = "NoParent";
    private static final String NO_NAME = "NoName";
    private static final String TEXT_DELIMITER = ":";
    private static final String CLASS_DELIMITER = "&";
    private final long id;
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

    protected Shape(long id, String text, Color fillColor, Color strokeColor, boolean locked, int c, int z, int t) {
        this.id = id;

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
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Shape shape))
            return false;
        return shape.id == this.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    public List<PathObject> createPathObjects(List<Shape> shapes) {
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

    public abstract String createJson();

    protected long getId() {
        return id;
    }

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

    protected Optional<Color> getFillColor() {
        return Optional.ofNullable(fillColor);
    }

    protected Optional<Color> getStrokeColor() {
        return Optional.ofNullable(strokeColor);
    }

    protected boolean getLocked() {
        return locked;
    }

    protected ImagePlane getPlane() {
        return ImagePlane.getPlaneWithChannel(c, z, t);
    }

    protected static int colorToRgba(Color color) {
        return (color.getRed()<<24) + (color.getGreen()<<16) + (color.getBlue()<<8) + color.getAlpha();
    }

    //TODO: check
    protected static Color rgbaToColor(int rgba) {
        return new Color((rgba >> 24) & 0xff, (rgba >> 16) & 0xff, (rgba >> 8) & 0xff, rgba & 0xff);
    }

    protected abstract ROI createRoi();

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
                    yield PathObjects.createDetectionObject(createRoi(shapes));
                } else {
                    yield PathObjects.createDetectionObject(createRoi(shapes), pathClasses.getFirst());
                }
            }
            case DETECTION -> {
                if (pathClasses.getFirst().equals(PathClass.NULL_CLASS)) {
                    yield PathObjects.createAnnotationObject(createRoi(shapes));
                } else {
                    yield PathObjects.createAnnotationObject(createRoi(shapes), pathClasses.getFirst());
                }
            }
        };

        pathObject.setID(uuids.getFirst());
        pathObject.setLocked(locked.getFirst());
        pathObject.setName(names.getFirst());

        logger.debug("Created path object {} from shapes {}", pathObject, shapes);
        return pathObject;
    }

    private static ROI createRoi(List<Shape> shapes) {
        if (shapes.isEmpty()) {
            return null;
        }

        List<ROI> rois = shapes.stream().map(Shape::createRoi).toList();
        if (rois.isEmpty()) {
            return null;
        }

        ROI roi = rois.getFirst();
        for (int i=1; i<rois.size(); i++) {
            roi = RoiTools.combineROIs(roi, rois.get(i), RoiTools.CombineOp.ADD);
        }
        return roi;
    }
}
