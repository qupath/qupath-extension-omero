package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Item of the hierarchy of a {@link javafx.scene.control.TreeView TreeView} containing
 * {@link RepositoryEntity RepositoryEntity} elements .
 * <p>
 * The items can be filtered by {@link Owner owner}, {@link Group group}, and name.
 * <p>
 * When an item is expanded, a web request is started to retrieve its children (if they don't already exist).
 * <p>
 * This item must be {@link #close() closed} once no longer used.
 */
public class HierarchyItem extends TreeItem<RepositoryEntity> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyItem.class);
    private static final Map<Class<? extends RepositoryEntity>, Integer> CLASS_ORDER = Map.of(
            Project.class, 1,
            Dataset.class, 2,
            Screen.class, 3,
            Plate.class, 4,
            OrphanedFolder.class, 5
    );
    private final ObservableList<TreeItem<RepositoryEntity>> children = FXCollections.observableArrayList();
    private final FilteredList<TreeItem<RepositoryEntity>> filteredChildren = new FilteredList<>(children);
    private final ObservableList<TreeItem<RepositoryEntity>> sortedChildren = new SortedList<>(
            filteredChildren,
            Comparator
                    .comparingInt((TreeItem<RepositoryEntity> item) -> CLASS_ORDER.getOrDefault(item.getValue().getClass(), 0))
                    .thenComparing(item -> item.getValue().getLabel())
    );  // not converted to local variable because otherwise it might get deleted by the garbage collector
    private final ListChangeListener<? super RepositoryEntity> childrenListener;
    private boolean computed = false;

    /**
     * Creates a hierarchy item.
     *
     * @param repositoryEntity the OMERO entity that will be displayed by this item
     * @param ownerBinding the children of this item won't be shown if they are not owned by this owner
     * @param groupBinding the children of this item won't be shown if they are not owned by this group
     * @param labelPredicate the children of this item won't be shown if they are images and don't match
     *                       this predicate (which is based on the label of this item)
     */
    public HierarchyItem(
            RepositoryEntity repositoryEntity,
            ObservableValue<? extends Owner> ownerBinding,
            ObservableValue<? extends Group> groupBinding,
            ReadOnlyObjectProperty<Predicate<RepositoryEntity>> labelPredicate
    ) {
        super(repositoryEntity);

        this.childrenListener = change -> Platform.runLater(() -> {
            for (TreeItem<RepositoryEntity> item: children) {
                if (item instanceof HierarchyItem hierarchyItem) {
                    hierarchyItem.close();
                }
            }

            // Make a copy of the children to make sure no one is added while iterating
            List<? extends RepositoryEntity> newChildren = new ArrayList<>(getValue().getChildren());

            children.setAll(newChildren.stream()
                    .map(entity -> new HierarchyItem(entity, ownerBinding, groupBinding, labelPredicate))
                    .toList()
            );
        });

        Bindings.bindContent(
                getChildren(),
                sortedChildren
        );

        expandedProperty().addListener((p, o, n) -> {
            if (n && !computed) {
                logger.debug("Item of {} expanded for the first time. Getting its children", getValue());
                computed = true;

                // Make a copy of the children to make sure no one is added while iterating
                List<? extends RepositoryEntity> newChildren = new ArrayList<>(getValue().getChildren());
                children.setAll(newChildren.stream().map(entity -> new HierarchyItem(entity, ownerBinding, groupBinding, labelPredicate)).toList());
                getValue().getChildren().addListener(childrenListener);

                filteredChildren.predicateProperty().bind(Bindings.createObjectBinding(
                        () -> item -> {
                            if (item.getValue() instanceof Image image) {
                                return labelPredicate.getValue().test(image) &&
                                        image.isFilteredByGroupOwner(groupBinding.getValue(), ownerBinding.getValue());
                            } else if (item.getValue() instanceof ServerEntity serverEntity) {
                                return serverEntity.isFilteredByGroupOwner(groupBinding.getValue(), ownerBinding.getValue());
                            } else {
                                return true;
                            }
                        },
                        groupBinding,
                        ownerBinding,
                        labelPredicate
                ));
            }
        });
    }

    @Override
    public boolean isLeaf() {
        return !getValue().hasChildren();
    }

    @Override
    public void close() {
        filteredChildren.predicateProperty().unbind();

        for (TreeItem<RepositoryEntity> item: children) {
            if (item instanceof HierarchyItem hierarchyItem) {
                hierarchyItem.close();
            }
        }

        getValue().getChildren().removeListener(childrenListener);
    }
}
