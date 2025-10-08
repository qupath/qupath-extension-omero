package qupath.ext.omero.core.apis.webclient.annotations;

import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFile;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFileAnnotation;

import java.util.List;

/**
 * Annotation containing information on a file attached to an OMERO entity.
 */
public class FileAnnotation extends Annotation {

    private final OmeroFile file;

    /**
     * Create the annotation.
     *
     * @param omeroFileAnnotation the OMERO annotation to create this annotation from
     * @param experimenters a list of experimenters that should contain the adder and owner of this annotation
     * @throws NullPointerException if one of the provided parameters is null
     */
    public FileAnnotation(OmeroFileAnnotation omeroFileAnnotation, List<OmeroSimpleExperimenter> experimenters) {
        super(
                omeroFileAnnotation.id(),
                omeroFileAnnotation.namespace(),
                omeroFileAnnotation.link() == null ? null : omeroFileAnnotation.link().owner(),
                omeroFileAnnotation.owner(),
                experimenters
        );

        this.file = omeroFileAnnotation.file();
    }

    @Override
    public String toString() {
        return String.format("File annotation %d containing %s", getId(), file);
    }

    /**
     * @return the name of the file described by this annotation
     */
    public String getFilename() {
        return file.name();
    }

    /**
     * @return the MIME type of the file described by this annotation
     */
    public String getMimeType() {
        return file.mimetype();
    }

    /**
     * @return the size of the file described by this annotation in bytes
     */
    public Long getFileSize() {
        return file.size();
    }
}
