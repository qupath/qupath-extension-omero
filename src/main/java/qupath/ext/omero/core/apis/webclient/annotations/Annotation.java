package qupath.ext.omero.core.apis.webclient.annotations;

import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.Namespace;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An OMERO annotation is <b>not</b> similar to a QuPath annotation.
 * It represents metadata attached to OMERO entities.
 */
public abstract class Annotation {

    private final long id;
    private final String namespace;
    private final OmeroSimpleExperimenter adder;
    private final OmeroSimpleExperimenter owner;

    /**
     * Create the annotation.
     *
     * @param id the ID of the annotation
     * @param namespace the namespace of the annotation. Can be null
     * @param adder the experimenter who added the annotation. Can be null
     * @param owner the experimenter who owns the annotation. Can be null
     * @param experimenters a list of experimenters, potentially containing the adder and owner of this annotation
     * @throws NullPointerException if the provided list of experimenters is null
     */
    protected Annotation(
            long id,
            String namespace,
            OmeroAnnotationExperimenter adder,
            OmeroAnnotationExperimenter owner,
            List<OmeroSimpleExperimenter> experimenters
    ) {
        this.id = id;
        this.namespace = namespace;
        this.adder = findExperimenter(experimenters, adder);
        this.owner = findExperimenter(experimenters, owner);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Annotation annotation))
            return false;
        return Objects.equals(annotation.id, id);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    /**
     * @return the ID of this annotation
     */
    public long getId() {
        return id;
    }

    /**
     * @return the namespace of this annotation, or an empty Optional if this annotation doesn't have a namespace
     */
    public Optional<Namespace> getNamespace() {
        return Optional.ofNullable(namespace).map(Namespace::new);
    }

    /**
     * @return the name (first name + last name) of the experimenter that added this annotation, or an empty Optional if not defined
     */
    public Optional<String> getAdderName() {
        return Optional.ofNullable(adder).map(Annotation::getExperimenterName);
    }

    /**
     * @return the name (first name + last name) of the experimenter that owns this annotation, or an empty Optional if not defined
     */
    public Optional<String> getOwnerName() {
        return Optional.ofNullable(owner).map(Annotation::getExperimenterName);
    }

    private static OmeroSimpleExperimenter findExperimenter(List<OmeroSimpleExperimenter> experimenters, OmeroAnnotationExperimenter experimenter) {
        if (experimenter == null) {
            return null;
        } else {
            return experimenters.stream()
                    .filter(simpleExperimenter -> simpleExperimenter.id().equals(experimenter.id()))
                    .findAny()
                    .orElse(null);
        }
    }

    private static String getExperimenterName(OmeroSimpleExperimenter experimenter) {
        return Stream.of(experimenter.firstName(), experimenter.lastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
