package qupath.ext.omero.gui.browser.advancedsearch.cellfactories;

import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.entities.search.SearchResultWithParentInfo;

/**
 * Cell that displays a server entity name in the cell and in an associated tooltip.
 */
public class EntityCell<T extends ServerEntity> extends TableCell<SearchResultWithParentInfo, T> {

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        setText(null);
        setTooltip(null);

        if (item != null && !empty) {
            setText(item.getAttributeValue(0));
            setTooltip(new Tooltip(item.getAttributeValue(0)));
        }
    }
}
