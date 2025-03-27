package qupath.ext.omero.core.entities.repositoryentities;

import javafx.collections.ObservableList;

/**
 * An element belonging to the OMERO entity hierarchy.
 */
public interface RepositoryEntity {

    /**
     * @return whether this entity has children
     */
    boolean hasChildren();

    /**
     * Returns the list of children of this element.
     * <p>
     * Usually, the initial call to this function returns an empty list but
     * starts populating it in the background, so changes to this list should
     * be listened. The {@link #isPopulatingChildren()} function indicates
     * if the populating process is currently happening.
     * <p>
     * This list may be updated from any thread.
     *
     * @return an unmodifiable list of children of this element
     */
    ObservableList<? extends RepositoryEntity> getChildren();

    /**
     * @return a text describing the entity
     */
    String getLabel();

    /**
     * @return whether this entity is currently populating its children
     */
    boolean isPopulatingChildren();
}