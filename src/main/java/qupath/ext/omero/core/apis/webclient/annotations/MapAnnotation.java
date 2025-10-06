package qupath.ext.omero.core.apis.webclient.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroMapAnnotation;

import java.util.List;
import java.util.Objects;

/**
 * Annotation containing several key-value pairs (e.g. license, release date).
 * Note that some keys can be duplicated.
 */
public class MapAnnotation extends Annotation {

    private static final Logger logger = LoggerFactory.getLogger(MapAnnotation.class);
    private final List<Pair> pairs;

    /**
     * Create the annotation.
     *
     * @param omeroMapAnnotation the OMERO annotation to create this annotation from
     * @param experimenters a list of experimenters that should contain the adder and owner of this annotation
     */
    public MapAnnotation(OmeroMapAnnotation omeroMapAnnotation, List<OmeroSimpleExperimenter> experimenters) {
        super(
                omeroMapAnnotation.id(),
                omeroMapAnnotation.namespace(),
                omeroMapAnnotation.link() == null ? null : omeroMapAnnotation.link().owner(),
                omeroMapAnnotation.owner(),
                experimenters
        );

        this.pairs = omeroMapAnnotation.values().stream()
                .map(value -> {
                    if (value.size() > 1) {
                        return new Pair(value.get(0), value.get(1));
                    } else {
                        logger.warn("The size of {} is less than two. Skipping it", value);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public String toString() {
        return String.format("Map annotation %d containing %s", getId(), pairs);
    }

    /**
     * @return the list of pairs of this annotation
     */
    public List<Pair> getPairs() {
        return pairs;
    }
}
