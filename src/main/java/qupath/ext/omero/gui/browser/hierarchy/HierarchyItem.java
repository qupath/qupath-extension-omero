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
 */
public class HierarchyItem extends TreeItem<RepositoryEntity> {

    private final ObservableList<TreeItem<RepositoryEntity>> children = FXCollections.observableArrayList();
    private final FilteredList<TreeItem<RepositoryEntity>> filteredChildren = new FilteredList<>(children);
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

        Bindings.bindContent(
                getChildren(),
                new SortedList<>(
                        filteredChildren,
                        (item1, item2) -> {
                            Map<Class<? extends RepositoryEntity>, Integer> classValues = Map.of(
                                    Project.class, 1,
                                    Dataset.class, 2,
                                    Screen.class, 3,
                                    Plate.class, 4,
                                    OrphanedFolder.class, 5     // display entities in that order
                            );

                            int classComparison = classValues.getOrDefault(item1.getValue().getClass(), 0) -
                                    classValues.getOrDefault(item2.getValue().getClass(), 0);
                            if (classComparison != 0) {
                                return classComparison;
                            }

                            return item1.getValue().getLabel().compareTo(item2.getValue().getLabel());
                        }
                )
        );

        expandedProperty().addListener((p, o, n) -> {
            if (n && !computed) {
                computed = true;

                children.setAll(getValue().getChildren().stream().map(entity -> new HierarchyItem(entity, ownerBinding, groupBinding, labelPredicate)).toList());
                getValue().getChildren().addListener((ListChangeListener<? super RepositoryEntity>) change -> Platform.runLater(() -> {
                    // Make a copy of the children to make sure no one is added while iterating
                    List<? extends RepositoryEntity> newChildren = new ArrayList<>(getValue().getChildren());

                    children.setAll(newChildren.stream().map(entity -> new HierarchyItem(entity, ownerBinding, groupBinding, labelPredicate)).toList());
                }));

                filteredChildren.predicateProperty().bind(Bindings.createObjectBinding(
                        () -> (Predicate<TreeItem<RepositoryEntity>>) item -> {
                            if (item.getValue() instanceof Image image) {
                                return labelPredicate.getValue().test(image) &&
                                        image.isFilteredByGroupOwner(groupBinding.getValue(), ownerBinding.getValue());
                            } else if (item.getValue() instanceof ServerEntity serverEntity) {
                                return serverEntity.isFilteredByGroupOwner(groupBinding.getValue(), ownerBinding.getValue());
                            } else {
                                return true;
                            }
                        },
                        groupBinding, ownerBinding, labelPredicate)
                );
            }
        });
    }

    @Override
    public boolean isLeaf() {
        return !getValue().hasChildren();
    }
}
