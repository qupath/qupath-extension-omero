/**
 * This package contains classes describing OMERO annotations.
 * <p>
 * An OMERO annotation is <b>not</b> similar to a QuPath annotation.
 * It represents metadata attached to OMERO entities.
 * <p>
 * An annotation is represented by the {@link qupath.ext.omero.core.entities.annotations.Annotation Annotation} abstract class.
 * This package contains concrete implementations of this class.
 * <p>
 * An OMERO entity can have multiple annotations attached to it. Therefore, they can be grouped in an
 * {@link qupath.ext.omero.core.entities.annotations.AnnotationGroup AnnotationGroup}.
 * <p>
 * An annotation can also contain complex data types, which are described in the
 * {@link qupath.ext.omero.core.entities.annotations.annotationsentities annotations entities} package.
 */
package qupath.ext.omero.core.entities.annotations;