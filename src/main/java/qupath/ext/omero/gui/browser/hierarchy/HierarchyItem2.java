package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.permissions.Experimenter;
import qupath.ext.omero.core.entities.permissions.ExperimenterGroup;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Image;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Item of the hierarchy of a {@link javafx.scene.control.TreeView TreeView} containing
 * {@link RepositoryEntity RepositoryEntity} elements .
 * <p>
 * Items can be filtered by {@link Experimenter}, {@link ExperimenterGroup}, and image name.
 * <p>
 * This item must be {@link #close() closed} once no longer used.
 * <p>
 * Warning: changing the value of this object with {@link TreeItem#setValue(Object)} is not
 * supported: such change won't probably be reflected in the UI.
 */
public class HierarchyItem2 extends TreeItem<RepositoryEntity> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyItem2.class);
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
    );  // not converted to local variable because otherwise it might get deleted by the garbage collector TODO: check
    private final ChangeListener<? super Experimenter> ownerListener = (p, o, n) ->
            fetchChildrenIfExpanded();
    private final ChangeListener<? super ExperimenterGroup> groupListener = (p, o, n) ->
            fetchChildrenIfExpanded();
    private final ObservableValue<? extends Experimenter> ownerBinding;
    private final ObservableValue<? extends ExperimenterGroup> groupBinding;
    private final ObservableValue<Predicate<RepositoryEntity>> labelPredicate;
    private CompletableFuture<Void> request;

    /**
     * Creates a hierarchy item.
     *
     * @param repositoryEntity the OMERO entity that will be displayed by this item
     * @param ownerBinding the children of this item won't be shown if they are not owned by this experimenter
     * @param groupBinding the children of this item won't be shown if they are not owned by this group
     * @param labelPredicate the children of this item won't be shown if they are images and don't match
     *                       this predicate (which is based on the label of this item)
     */
    public HierarchyItem2(
            RepositoryEntity repositoryEntity,
            ObservableValue<? extends Experimenter> ownerBinding,
            ObservableValue<? extends ExperimenterGroup> groupBinding,
            ObservableValue<Predicate<RepositoryEntity>> labelPredicate
    ) {
        super(repositoryEntity);

        this.ownerBinding = ownerBinding;
        this.groupBinding = groupBinding;
        this.labelPredicate = labelPredicate;

        Bindings.bindContent(getChildren(), sortedChildren);

        this.filteredChildren.predicateProperty().bind(Bindings.createObjectBinding(
                () -> item -> !(item.getValue() instanceof Image) || labelPredicate.getValue().test(item.getValue()),
                labelPredicate
        ));

        ownerBinding.addListener(ownerListener);
        groupBinding.addListener(groupListener);

        expandedProperty().addListener((p, o, n) -> fetchChildrenIfExpanded());

        valueProperty().addListener((p, o, n) -> logger.warn(
                "The value of {} was changed from {} to {}. This is not supported, so changes won't probably be reflected in the UI",
                this,
                o,
                n
        ));
    }

    @Override
    public boolean isLeaf() {
        return !getValue().hasChildren();
    }

    @Override
    public void close() {
        filteredChildren.predicateProperty().unbind();

        ownerBinding.removeListener(ownerListener);
        groupBinding.removeListener(groupListener);
    }

    private void fetchChildrenIfExpanded() {
        if (!isExpanded()) {
            return;
        }

        if (request != null) {
            request.cancel(true);
        }

        clearChildren();

        RepositoryEntity repositoryEntity = getValue();

        request = repositoryEntity.getChildren(
                ownerBinding.getValue().getId(),
                groupBinding.getValue().getId()
        ).handle((repositoryEntities, error) -> {
            if (error != null) {
                logger.error("Error when getting children of {}", repositoryEntity, error);
                return null;
            }

            Platform.runLater(() -> {
                clearChildren();

                children.addAll(repositoryEntities.stream()
                        .map(child -> new HierarchyItem2(child, ownerBinding, groupBinding, labelPredicate))
                        .toList()
                );
            });

            return null;
        });
    }

    private void clearChildren() {
        for (TreeItem<RepositoryEntity> item: children) {
            if (item instanceof HierarchyItem2 hierarchyItem) {
                hierarchyItem.close();
            }
        }

        children.clear();
    }
}
